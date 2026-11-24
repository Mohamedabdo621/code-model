# Code Model API — بيشغّل نموذج Alex المدرّب (Qwen2.5-Coder-1.5B + LoRA) وبيقدمه
# كصفحة تجربة + API يقدر أي تطبيق (Expo / ويب / أي لغة) يكلمه بـ HTTP.
#
# النموذج الأساسي + الأدابتور بيتنزلوا من Hugging Face أول ما الـ Space يشتغل،
# وبيندمجوا (merge) مرة واحدة عشان الاستدلال يبقى أبسط وأسرع.
import torch
import gradio as gr
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel

BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
ADAPTER = "darklink56/qwen-coder-1.5b-lora-out"  # الأدابتور المدرّب فعلياً وموجود على HF

print("Loading tokenizer...")
tokenizer = AutoTokenizer.from_pretrained(ADAPTER)

print("Loading base model (CPU, fp16)...")
model = AutoModelForCausalLM.from_pretrained(BASE_MODEL, torch_dtype=torch.float16)

print("Applying LoRA adapter and merging...")
model = PeftModel.from_pretrained(model, ADAPTER)
model = model.merge_and_unload()
model.eval()
print("Model ready.")


def generate(prompt: str, max_new_tokens: int = 200) -> str:
    """المطلوب (سؤال/مهمة كود) -> كود/إجابة من النموذج المدرّب."""
    if not prompt or not prompt.strip():
        return "اكتب سؤال أو مهمة كود الأول."
    messages = [{"role": "user", "content": prompt.strip()}]
    text = tokenizer.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=True
    )
    inputs = tokenizer(text, return_tensors="pt")
    with torch.no_grad():
        out = model.generate(
            **inputs,
            max_new_tokens=int(max_new_tokens),
            do_sample=False,
            pad_token_id=tokenizer.eos_token_id,
        )
    answer = out[0][inputs["input_ids"].shape[1]:]
    return tokenizer.decode(answer, skip_special_tokens=True)


demo = gr.Interface(
    fn=generate,
    inputs=[
        gr.Textbox(label="Prompt — اكتب مهمة كود", lines=3,
                   placeholder="Write a Python function that..."),
        gr.Slider(32, 1024, value=200, step=16, label="Max new tokens"),
    ],
    outputs=gr.Textbox(label="Answer — إجابة النموذج", lines=10),
    title="darklink56 Code Model",
    description="Qwen2.5-Coder-1.5B fine-tuned with LoRA by darklink56. "
                "نفس الدالة دي متاحة كـ API باسم generate — شوف README.",
    api_name="generate",
)

if __name__ == "__main__":
    demo.launch()
