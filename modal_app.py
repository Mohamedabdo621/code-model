"""NEXA Code API — نشر النموذج على Modal برابط دائم (مش بيخلص).

ليه Modal؟ رابط ثابت بصيغة https://<user>--nexa-code-api-web.modal.run
بيفضل زي ما هو على طول، والسيرفر بيصحى على GPU (T4) لما ييجي طلب وينام
لما ميبقاش فيه استخدام — فبيفضل شغال ضمن الكريديت المجاني الشهري.

النشر (مرة واحدة بس) — من أي تيرمنال فيه Python (Termux / Colab / كمبيوتر):
    pip install modal
    modal setup            # تسجيل دخول بحساب Modal (GitHub أو Google)
    modal deploy modal_app.py

بعدها الصق الرابط اللي هيظهر في التطبيق (تبويب «الرابط») أو في أداة
التيرمنال بـ --url. البروتوكول نفس بروتوكول Gradio بالظبط
(/gradio_api/call/generate) فكل العملاء الحاليين بيشتغلوا من غير تعديل.

ملحوظات:
- أول طلب بعد فترة خمول بياخد وقت أطول (تشغيل الـ GPU وتحميل النموذج).
- الأوزان بتتخزن في Volume فالتحميل بيحصل مرة واحدة بس.
- النموذج والـ adapter عامين على Hugging Face — مش محتاج أي توكن.
"""
import modal

app = modal.App("nexa-code-api")

BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
ADAPTER = "darklink56/qwen-coder-1.5b-lora-out"

image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "torch",
        "transformers==4.57.6",
        "peft==0.22.0",
        "accelerate==1.16.0",
        "bitsandbytes==0.50.2",
    )
    .env({"HF_HOME": "/hf-cache"})
)

web_image = modal.Image.debian_slim(python_version="3.11").pip_install(
    "fastapi", "uvicorn"
)

hf_cache = modal.Volume.from_name("nexa-hf-cache", create_if_missing=True)


@app.cls(
    gpu="T4",
    image=image,
    volumes={"/hf-cache": hf_cache},
    scaledown_window=180,
    timeout=900,
)
class Model:
    @modal.enter()
    def load(self):
        import torch
        from peft import PeftModel
        from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

        self.tokenizer = AutoTokenizer.from_pretrained(ADAPTER)
        self.tokenizer.pad_token = "<|endoftext|>"
        bnb = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=True,
        )
        model = AutoModelForCausalLM.from_pretrained(
            BASE_MODEL,
            quantization_config=bnb,
            torch_dtype=torch.float16,
            device_map="auto",
        )
        model = PeftModel.from_pretrained(model, ADAPTER)
        self.model = model.merge_and_unload()
        self.model.eval()

    @modal.method()
    def generate(self, prompt: str, max_new_tokens: int = 200) -> str:
        import torch

        messages = [{"role": "user", "content": prompt}]
        text = self.tokenizer.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )
        inputs = self.tokenizer(text, return_tensors="pt").to(self.model.device)
        with torch.no_grad():
            out = self.model.generate(
                **inputs,
                max_new_tokens=int(max_new_tokens),
                do_sample=False,
                pad_token_id=self.tokenizer.eos_token_id,
            )
        return self.tokenizer.decode(
            out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True
        )


@app.function(image=web_image, scaledown_window=300, timeout=900)
@modal.asgi_app()
def web():
    import threading
    import time
    import uuid

    from fastapi import FastAPI
    from fastapi.responses import JSONResponse, PlainTextResponse
    from pydantic import BaseModel

    api = FastAPI()
    jobs = {}  # event_id -> {"done": bool, "result": str, "error": str}

    class CallBody(BaseModel):
        data: list

    def run_job(event_id: str, prompt: str, tokens: int):
        try:
            jobs[event_id]["result"] = Model().generate.remote(prompt, tokens)
        except Exception as e:  # noqa: BLE001
            jobs[event_id]["error"] = str(e)
        jobs[event_id]["done"] = True

    @api.post("/gradio_api/call/generate")
    def call_generate(body: CallBody):
        prompt = str(body.data[0]) if body.data else ""
        tokens = int(body.data[1]) if len(body.data) > 1 else 200
        event_id = uuid.uuid4().hex
        jobs[event_id] = {"done": False, "result": "", "error": ""}
        threading.Thread(target=run_job, args=(event_id, prompt, tokens), daemon=True).start()
        return JSONResponse({"event_id": event_id})

    @api.get("/gradio_api/call/generate/{event_id}")
    def get_result(event_id: str):
        job = jobs.get(event_id)
        if job is None:
            return PlainTextResponse("event: error\ndata: [\"unknown event\"]\n\n",
                                     media_type="text/event-stream")
        # انتظار طويل لحد ما النتيجة تجهز (العملاء بيقروا أول سطر data:)
        deadline = time.time() + 280
        while not job["done"] and time.time() < deadline:
            time.sleep(1)
        if job["error"]:
            return PlainTextResponse(
                'event: error\ndata: ["' + job["error"].replace('"', "'")[:200] + '"]\n\n',
                media_type="text/event-stream")
        import json as _json

        payload = _json.dumps([job["result"]], ensure_ascii=False)
        return PlainTextResponse(f"event: complete\ndata: {payload}\n\n",
                                 media_type="text/event-stream")

    return api
