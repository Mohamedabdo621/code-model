#!/usr/bin/env python3
"""Code CLI — مساعد كود في التيرمنال، بيكلم نموذج Alex المدرّب عن طريق الـ API.

بيشتغل في أي تيرمنال: Termux على الموبايل، كمبيوتر، أو Colab.
مش محتاج أي مكتبات — Python القياسي بس (3.9+).

أمثلة:
    # وضع المحادثة التفاعلي (تكتب طلباتك سطر ورا سطر)
    python code_cli.py --url https://XXXX.gradio.live

    # طلب واحد على طول
    python code_cli.py --url https://XXXX.gradio.live "اكتب دالة بايثون تجيب الأعداد الأولية لحد 100"

    # أو حط الرابط في متغير مرة واحدة
    export CODE_MODEL_URL=https://XXXX.gradio.live
    python code_cli.py

أوامر جوه المحادثة:
    /save اسم_الملف   احفظ آخر رد (الكود اللي فيه بس) في ملف
    /tokens 300       غيّر أقصى طول للرد
    /exit             خروج

ملحوظة: رابط gradio.live بيتغير كل ما جلسة Colab تتقفل وتتفتح — حدّثه
بـ --url أو CODE_MODEL_URL وخلاص.
"""
import argparse
import json
import os
import re
import sys
import urllib.request

DEFAULT_TOKENS = 300
POLL_TIMEOUT = 300  # ثواني كحد أقصى لانتظار الرد


def ask_model(base_url: str, prompt: str, max_tokens: int) -> str:
    """يبعت السؤال لـ Gradio API (خطوتين: POST يرجع event_id، وبعده GET يجيب النتيجة)."""
    base = base_url.rstrip("/")
    post_url = f"{base}/gradio_api/call/generate"
    payload = json.dumps({"data": [prompt, max_tokens]}).encode("utf-8")
    req = urllib.request.Request(
        post_url, data=payload, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        event_id = json.loads(resp.read().decode("utf-8"))["event_id"]

    get_url = f"{base}/gradio_api/call/generate/{event_id}"
    with urllib.request.urlopen(get_url, timeout=POLL_TIMEOUT) as resp:
        body = resp.read().decode("utf-8")
    for line in body.splitlines():
        if line.startswith("data:"):
            data = json.loads(line[5:].strip())
            return data[0] if data else ""
    raise RuntimeError(f"رد غير متوقع من الـ API:\n{body[:500]}")


def extract_code(text: str) -> str:
    """يطلع محتوى بلوكات الكود ```...``` من الرد، أو الرد كله لو مفيش بلوكات."""
    blocks = re.findall(r"```(?:\w+)?\n(.*?)```", text, re.DOTALL)
    return "\n\n".join(blocks) if blocks else text


def main() -> int:
    parser = argparse.ArgumentParser(description="Code CLI — اسأل نموذج الكود بتاعك من التيرمنال")
    parser.add_argument("prompt", nargs="?", help="طلب واحد (اختياري — من غيره بيفتح وضع المحادثة)")
    parser.add_argument("--url", default=os.environ.get("CODE_MODEL_URL", ""),
                        help="رابط الـ API (gradio.live) — أو عيّن CODE_MODEL_URL")
    parser.add_argument("--tokens", type=int, default=DEFAULT_TOKENS, help="أقصى طول للرد")
    args = parser.parse_args()

    url = args.url.strip()
    if not url:
        url = input("الصق رابط الـ API (gradio.live): ").strip()
    if not url:
        print("لازم رابط. شغّل نوتبوك serve_api_colab.ipynb في Colab وخد الرابط منها.")
        return 1

    # طلب واحد
    if args.prompt:
        try:
            print(ask_model(url, args.prompt, args.tokens))
        except Exception as e:  # noqa: BLE001 — رسالة واضحة للمستخدم
            print(f"❌ مشكلة في الاتصال بالـ API: {e}")
            print("اتأكد إن جلسة Colab لسه مفتوحة وإن الرابط هو الأحدث.")
            return 1
        return 0

    # وضع المحادثة
    tokens = args.tokens
    last_answer = ""
    print("=" * 60)
    print("🤖 Code CLI — نموذج الكود بتاعك جاهز. اكتب طلبك ودوس Enter.")
    print("   /save ملف.py لحفظ آخر كود | /tokens 300 | /exit للخروج")
    print("=" * 60)
    while True:
        try:
            prompt = input("\nانت> ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nمع السلامة 👋")
            return 0
        if not prompt:
            continue
        if prompt == "/exit":
            print("مع السلامة 👋")
            return 0
        if prompt.startswith("/tokens"):
            parts = prompt.split()
            if len(parts) == 2 and parts[1].isdigit():
                tokens = int(parts[1])
                print(f"تمام — أقصى طول للرد بقى {tokens}")
            else:
                print("الاستخدام: /tokens 300")
            continue
        if prompt.startswith("/save"):
            parts = prompt.split(maxsplit=1)
            if len(parts) == 2 and last_answer:
                path = parts[1].strip()
                with open(path, "w", encoding="utf-8") as f:
                    f.write(extract_code(last_answer))
                print(f"💾 اتحفظ الكود في {path}")
            elif not last_answer:
                print("مفيش رد لسه يتحفظ — اسأل الأول.")
            else:
                print("الاستخدام: /save ملف.py")
            continue
        print("النموذج> بيكتب...")
        try:
            last_answer = ask_model(url, prompt, tokens)
        except Exception as e:  # noqa: BLE001
            print(f"❌ مشكلة في الاتصال: {e}")
            print("اتأكد إن جلسة Colab لسه مفتوحة وإن الرابط هو الأحدث.")
            continue
        print(f"النموذج>\n{last_answer}")


if __name__ == "__main__":
    sys.exit(main())
