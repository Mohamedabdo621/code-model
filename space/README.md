---
title: Code Model API
emoji: "💻"
colorFrom: indigo
colorTo: blue
sdk: gradio
sdk_version: 5.50.0
app_file: app.py
pinned: false
---

# Code Model API — ربط النموذج بأي تطبيق

> ⚠️ تحديث (2026-11-24): Hugging Face بقت بتشترط خطة PRO مدفوعة لأي Space بيشتغل على compute (Gradio/Docker) — المجاني بقى Static بس، وده ميشغّلش نموذج. فالملفات دي محفوظة كمرجع/للمستقبل، والطريقة المجانية الشغالة فعلياً دلوقتي هي نوتبوك `notebooks/serve_api_colab.ipynb` (Colab + رابط gradio.live) — نفس الـ API بنفس الاسم `generate`.

الملفات اللي في الفولدر ده (`app.py` + `requirements.txt` + الـ README ده)
بتتعمل بيها **Hugging Face Space** مجانية بتشغّل نموذج Alex المدرّب
(`darklink56/qwen-coder-1.5b-lora-out` فوق `Qwen/Qwen2.5-Coder-1.5B-Instruct`)
وبتقدّمه بطريقتين في نفس الوقت:

1. **صفحة تجربة** في المتصفح (تسأل ويجاوب قدامك).
2. **API** يقدر أي تطبيق يكلمه — Expo، ويب، بايثون، أي حاجة بتعمل HTTP.

## إنشاء الـ Space (مرة واحدة، 3 دقايق)

1. افتح https://huggingface.co/new-space بحسابك (darklink56).
2. الاسم: `code-model-api` — الـ SDK: اختار **Gradio** — الأجهزة: **CPU basic** (المجاني).
3. بعد ما الـ Space تتعمل، ارفع الملفات التلاتة اللي في الفولدر ده زي ما هم
   (من زرار **Files → Add file → Upload files** جوه الـ Space)، واستنى البناء يخلص.
4. الـ Space هتكون شغالة على:
   `https://huggingface.co/spaces/darklink56/code-model-api`
   ورابط الـ API المباشر: `https://darklink56-code-model-api.hf.space`

> ملحوظة واقعية: الـ CPU المجاني بطيء نسبياً مع نموذج 1.5B — الرد ممكن ياخد
> ثواني لحد دقيقة حسب طول الإجابة. دي نسخة ربط وتجربة سريعة؛ لو المشروع كبر،
> الترقية الطبيعية هي ZeroGPU أو استضافة مدفوعة، وساعتها بس هيبقى في تكلفة.

## استخدام الـ API من أي تطبيق

الـ API شغال بنظام Gradio على خطوتين: تبعت السؤال تاخد `event_id`، وبعدين
تجيب النتيجة بيه.

### من JavaScript / Expo (React Native)

```ts
const SPACE_URL = "https://darklink56-code-model-api.hf.space";

// 1) ابعت السؤال
const post = await fetch(`${SPACE_URL}/gradio_api/call/generate`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    data: ["Write a Python function that checks if a string is a palindrome.", 200],
  }),
});
const { event_id } = await post.json();

// 2) هات النتيجة (سطر SSE بيبدأ بـ data: )
const res = await fetch(`${SPACE_URL}/gradio_api/call/generate/${event_id}`);
const text = await res.text();
const line = text.split("\n").find((l) => l.startsWith("data:"));
const answer = JSON.parse(line.slice(5))[0];
console.log(answer);
```

### من Python

```python
from gradio_client import Client

client = Client("darklink56/code-model-api")
answer = client.predict(
    "Write a SQL query that returns all users older than 30.",
    200,
    api_name="/generate",
)
print(answer)
```

## تغيير النموذج لاحقاً

لو درّبت أدابتور جديد (تشغيلة أكبر)، غيّر سطر `ADAPTER` في أول `app.py`
لاسم المستودع الجديد، والـ Space هتشتغل بيه على طول بعد إعادة البناء.
