#!/usr/bin/env python3
"""Code CLI — مساعد كود في التيرمنال، بيكلم نموذج Alex المدرّب عن طريق الـ API.

بيشتغل في أي تيرمنال: Termux على الموبايل، كمبيوتر، أو Colab.
مش محتاج أي مكتبات — Python القياسي بس (3.9+).

الاستخدامات:
    # محادثة تفاعلية: تسأل ويرد عليك بالكود
    python code_cli.py --url https://XXXX.gradio.live

    # طلب واحد على طول
    python code_cli.py --url https://XXXX.gradio.live "اكتب دالة بايثون تجيب الأعداد الأولية"

    # بناء مشروع كامل على القرص (مش كود في الشاشة بس):
    python code_cli.py --url https://XXXX.gradio.live build "صفحة هبوط لمحل قهوة فيها منيو وزر طلب"
    #   -> بيخطط الملفات مع النموذج، يولّد كل ملف (index.html, style.css,
    #      script.js, ...) ويكتبهم فعلياً في فولدر مشروع جاهز للفتح.

    # أو عيّن الرابط مرة واحدة
    export CODE_MODEL_URL=https://XXXX.gradio.live

أوامر جوه المحادثة:
    /build وصف المشروع   ابنِ مشروع كامل في فولدر
    /save اسم_الملف       احفظ آخر رد (الكود اللي فيه بس) في ملف
    /tokens 300           غيّر أقصى طول للرد
    /exit                 خروج

ملحوظة: رابط gradio.live بيتغير كل ما جلسة Colab تتقفل وتتفتح — حدّثه
بـ --url أو CODE_MODEL_URL وخلاص.
"""
import argparse
import json
import os
import re
import sys
import urllib.request
from datetime import datetime

DEFAULT_TOKENS = 300
BUILD_TOKENS = 500  # أقصى طول لكل ملف في وضع البناء (حد الـ API الحالي)
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


LANG_BY_EXT = {
    ".html": ("html",),
    ".css": ("css",),
    ".js": ("javascript", "js"),
    ".json": ("json",),
}


def extract_file_content(text: str, filename: str) -> str:
    """يطلع محتوى ملف واحد من رد النموذج.

    النماذج الصغيرة أحياناً بترجع المشروع كله في رد واحد — هنا بنختار
    البلوك اللي وسم لغته يطابق امتداد الملف المطلوب، مش أي بلوك.
    """
    tagged = re.findall(r"```(\w+)?\n(.*?)```", text, re.DOTALL)
    if not tagged:
        return text
    ext = os.path.splitext(filename)[1].lower()
    wanted = LANG_BY_EXT.get(ext, ())
    for lang, content in tagged:
        if (lang or "").lower() in wanted:
            return content
    # مفيش وسم مطابق: ملف HTML بيبقى أول بلوك عادة، وغيره غالباً آخر بلوك
    if ext == ".html":
        return tagged[0][1]
    return tagged[-1][1]


# ---------------------------------------------------------------------------
# وضع البناء: النموذج يخطط ويولّد، والأداة تكتب الملفات فعلياً على القرص
# ---------------------------------------------------------------------------

DEFAULT_FILES = ["index.html", "style.css", "script.js"]
FILENAME_RE = re.compile(r"^[\w./-]+\.(html|css|js|json|txt|md|svg|xml)$", re.IGNORECASE)


def plan_files(base_url: str, description: str) -> list:
    """يسأل النموذج عن قايمة ملفات المشروع، ويرجع قايمة نظيفة (أو الافتراضية)."""
    prompt = (
        "You are building a small, complete web project.\n"
        f"Project description: {description}\n\n"
        "List the files needed to build it. Rules:\n"
        "- One filename per line, filenames only, no explanations.\n"
        "- At most 5 files. Always include index.html.\n"
        "- Example:\nindex.html\nstyle.css\nscript.js"
    )
    try:
        reply = ask_model(base_url, prompt, 120)
    except Exception:  # noqa: BLE001 — أي فشل في التخطيط -> الافتراضي
        return DEFAULT_FILES[:]
    files = []
    for line in reply.splitlines():
        name = line.strip().strip("`-*•0123456789. ").strip()
        if FILENAME_RE.match(name) and name not in files:
            files.append(name)
    if not files:
        return DEFAULT_FILES[:]
    if not any(f.lower().endswith(".html") for f in files):
        files.insert(0, "index.html")
    return files[:5]


def slugify(text: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug[:40] or "project-" + datetime.now().strftime("%H%M%S")


def build_project(base_url: str, description: str, out_dir: str = "", tokens: int = BUILD_TOKENS) -> str:
    """يبني المشروع كاملاً ويرجع مسار الفولدر اللي اتكتب فيه."""
    print("🧠 النموذج بيخطط ملفات المشروع...")
    files = plan_files(base_url, description)
    print(f"📋 الملفات: {', '.join(files)}")

    if not out_dir:
        out_dir = slugify(description)
        base_out, n = out_dir, 2
        while os.path.exists(out_dir):
            out_dir = f"{base_out}-{n}"
            n += 1
    os.makedirs(out_dir, exist_ok=True)

    file_list = ", ".join(files)
    html_context = ""
    for fname in files:
        print(f"🔨 بيولّد {fname} ...")
        prompt = (
            f"Write the complete file `{fname}` for this web project.\n"
            f"Project description: {description}\n"
            f"Project files: {file_list}\n"
            "Requirements:\n"
            "- The files must work together: index.html links style.css and script.js "
            "with relative paths.\n"
            "- Complete and working, but compact — no placeholder comments like "
            "'your code here'.\n"
            f"- Output ONLY the content of `{fname}` inside one code block. "
            "Do NOT output any other file. No explanations."
        )
        if html_context and not fname.lower().endswith(".html"):
            prompt += (
                "\n\nThe index.html file is ALREADY created — here it is. You MUST "
                "use the exact same element ids, class names and function names "
                f"that appear in it:\n```html\n{html_context[:1500]}\n```"
            )
        reply = ask_model(base_url, prompt, tokens)
        content = extract_file_content(reply, fname).strip() + "\n"
        path = os.path.join(out_dir, fname)
        os.makedirs(os.path.dirname(path) or out_dir, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"   💾 {path} ({len(content)} حرف)")
        if fname.lower().endswith(".html") and not html_context:
            html_context = content

    print(f"\n✅ المشروع جاهز في فولدر: {out_dir}")
    if "index.html" in files:
        print(f"   افتح {os.path.join(out_dir, 'index.html')} في المتصفح عشان تشوفه.")
    return out_dir


# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description="Code CLI — اسأل نموذج الكود بتاعك من التيرمنال أو ابنِ بيه مشاريع")
    parser.add_argument("words", nargs="*", help="'build وصف المشروع' للبناء، أو طلب واحد، أو سيبها فاضية للمحادثة")
    parser.add_argument("--url", default=os.environ.get("CODE_MODEL_URL", ""),
                        help="رابط الـ API (gradio.live) — أو عيّن CODE_MODEL_URL")
    parser.add_argument("--tokens", type=int, default=DEFAULT_TOKENS, help="أقصى طول للرد")
    parser.add_argument("--out", default="", help="فولدر المشروع في وضع البناء (اختياري)")
    args = parser.parse_args()

    url = args.url.strip()
    if not url:
        url = input("الصق رابط الـ API (gradio.live): ").strip()
    if not url:
        print("لازم رابط. شغّل نوتبوك serve_api_colab.ipynb في Colab وخد الرابط منها.")
        return 1

    words = args.words

    # وضع البناء من سطر الأوامر
    if words and words[0] == "build":
        description = " ".join(words[1:]).strip()
        if not description:
            print('الاستخدام: python code_cli.py build "وصف المشروع"')
            return 1
        try:
            build_project(url, description, out_dir=args.out)
        except Exception as e:  # noqa: BLE001
            print(f"❌ البناء وقف: {e}")
            print("اتأكد إن جلسة Colab لسه مفتوحة وإن الرابط هو الأحدث.")
            return 1
        return 0

    # طلب واحد
    if words:
        try:
            print(ask_model(url, " ".join(words), args.tokens))
        except Exception as e:  # noqa: BLE001
            print(f"❌ مشكلة في الاتصال بالـ API: {e}")
            print("اتأكد إن جلسة Colab لسه مفتوحة وإن الرابط هو الأحدث.")
            return 1
        return 0

    # وضع المحادثة
    tokens = args.tokens
    last_answer = ""
    print("=" * 60)
    print("🤖 Code CLI — نموذج الكود بتاعك جاهز. اكتب طلبك ودوس Enter.")
    print("   /build وصف مشروع | /save ملف.py | /tokens 300 | /exit")
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
        if prompt.startswith("/build"):
            description = prompt[len("/build"):].strip()
            if not description:
                print("الاستخدام: /build وصف المشروع")
                continue
            try:
                build_project(url, description)
            except Exception as e:  # noqa: BLE001
                print(f"❌ البناء وقف: {e}")
            continue
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
