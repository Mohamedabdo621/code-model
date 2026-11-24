# نموذج كود خاص بيك — Code Model (Path B)

مشروع **Fine-tune** لنموذج كود مفتوح على حسابك إنت، بصفر تكلفة:
بنعلّم `Qwen/Qwen2.5-Coder-1.5B-Instruct` تعليم إضافي (LoRA/QLoRA) على
Google Colab المجاني، والنتيجة بتترفع على Hugging Face باسمك.

## صراحة الأول — إحنا بنعمل إيه بالظبط؟

- إحنا **مش** بندرّب نموذج من الصفر. النموذج الأساسي (Qwen-Coder) اتدرب
  أصلاً على كميات ضخمة من الكود وبيفهم لغات برمجة كتير (Python,
  JavaScript, SQL, C++, Java, Rust, Go وغيرها).
- اللي بنعمله اسمه **Fine-tuning**: بناخد النموذج الجاهز ونعلّمه على
  أمثلة تعليمات-وحلول (Instruction/Solution) عشان يتحسّن في أسلوب
  الإجابة ونوع المهام اللي إحنا عايزينه، وبعدين — وده الأهم — تقدر
  تعلّمه على **أمثلة من عندك** فيبقى متخصص في شغلك إنت.
- التدريب من الصفر لنموذج بالحجم ده بيحتاج حوسبة بملايين؛ الـ
  Fine-tune بـ LoRA بيحتاج كارت T4 مجاني. دي مش حيلة — دي الطريقة
  اللي الصناعة نفسها بتشتغل بيها.

## Pipeline — المشروع بيشتغل إزاي

```text
Hugging Face (dataset)          Google Colab (free T4 GPU)
ise-uiuc/Magicoder-OSS-Instruct-75K ──► notebooks/train_code_model_colab.ipynb
  columns: problem / solution          │  4-bit load (QLoRA) + LoRA r=16
                                       │  TRL SFTTrainer, ~3000 examples
                                       ▼
                              LoRA adapter (small files)
                                       │  push_to_hub
                                       ▼
Hugging Face (your account)     YOUR_HF_USERNAME/qwen-coder-1.5b-lora
                                       │
                                       ▼
                              inference.py = base model + adapter
                                       → بيجاوب على أسئلة كود
```

## خطوات التشغيل — Step by step

### 1) اعمل توكن Hugging Face بصلاحية Write
- افتح: https://huggingface.co/settings/tokens
- `Create new token` → النوع **Write** → انسخ التوكن (`hf_...`)
- ⚠️ التوكن ده سر: **متحطوش في أي ملف ولا في الشات** — هيتحط في
  Colab Secrets بس (الخطوة الجاية).

### 2) افتح النوتبوك في Colab
الريبو ده هيعيش على GitHub تحت حساب **Mohamedabdo621** باسم
**`code-model`**، فاللينك هيبقى بالشكل القياسي ده:

```text
https://colab.research.google.com/github/Mohamedabdo621/code-model/blob/main/notebooks/train_code_model_colab.ipynb
```

(أو من Colab نفسه: `File → Open notebook → GitHub` وحط لينك الريبو.)

### 3) ظبط الـ Runtime على GPU
من قايمة Colab: `Runtime → Change runtime type → T4 GPU → Save`

### 4) حط التوكن في Colab Secrets
- من الشريط الشمال دوس أيقونة 🔑 **Secrets**
- اعمل سر جديد: الاسم `HF_TOKEN` والقيمة التوكن بتاعك
- فعّل **Notebook access** للنوتبوك دي

### 5) عدّل اسم المستخدم
في خلية الـ **Config** (رقم 4 في النوتبوك) غيّر:

```python
HF_USERNAME = "YOUR_HF_USERNAME"   # ← اسم مستخدمك على Hugging Face
```

النتيجة هتترفع على: `YOUR_HF_USERNAME/qwen-coder-1.5b-lora`

### 6) شغّل الكل
`Runtime → Run all` وخلي التبويب مفتوح.

**توقعات واقعية لأول تشغيلة:** الإعدادات الافتراضية 3000 مثال و
epoch واحدة على T4 — دي تشغيلة إثبات فكرة بتخلص غالباً في حدود جلسة
Colab مجانية واحدة (الوقت الفعلي بيختلف حسب طول الأمثلة وزحمة
Colab). النموذج هيتحسّن في اتباع التعليمات وأسلوب الحلول، بس متتوقعش
قفزة سحرية من أول 3000 مثال — الجودة بتيجي مع تكبير الداتا بعد كده.

### 7) النتيجة وتجربتها
- الأدابتور بيظهر على: `https://huggingface.co/YOUR_HF_USERNAME/qwen-coder-1.5b-lora`
- جرّبه محلياً (محتاج GPU لسرعة معقولة):

```bash
pip install -r requirements.txt
python inference.py --adapter YOUR_HF_USERNAME/qwen-coder-1.5b-lora \
    --prompt "Write a Python function that checks if a string is a palindrome"
```

- أو درّب من غير نوتبوك (Kaggle / جهازك):

```bash
HF_TOKEN=hf_xxx python train_local.py --hf-username YOUR_HF_USERNAME
```

## التكلفة والحدود — Costs = 0

| الحاجة | التكلفة | الحد المجاني |
|---|---|---|
| النموذج الأساسي (Apache-2.0) | 0 | تنزيل حر من HF |
| الداتاست (MIT) | 0 | استخدام حر |
| التدريب | 0 | Colab T4 مجاني — سقف ساعات وفصل بعد الخمول |
| تخزين الأدابتور | 0 | HF Hub مجاني للنماذج العامة |
| الكود | 0 | GitHub |

الثمن الحقيقي هو **الوقت والحدود**: جلسات Colab بتفصل، فالتشغيلات
الكبيرة بتتقسم على كذا جلسة، والحجم اللي نقدر نعلّمه مجاناً محدود
(1.5B هو الحجم الصح للـ T4).

## Troubleshooting — مشاكل معروفة

- **Out of Memory:** قلّل `PER_DEVICE_BATCH` لـ 1، أو `MAX_SEQ_LEN`
  لـ 768، أو `SAMPLE_SIZE` — بالتفصيل في آخر خلية في النوتبوك.
- **الجلسة فصلت قبل الرفع:** الأدابتور بيضيع؛ عشان كده أول تشغيلة
  صغيرة. للتشغيلات الكبيرة: ارفع بعد كل مرحلة.
- **401/403 من Hugging Face:** التوكن مش Write، أو اسم `HF_TOKEN`
  في Secrets مش مظبوط، أو Notebook access مش مفعّلة.

## Roadmap — اللي جاي

1. **داتا أكبر:** 10K ثم 30K+ مثال على جلسات متتابعة، و epochs = 2.
2. **تخصيص حقيقي:** علّمه على أمثلتك إنت (شوف `data/README.md`) —
   شغلك في Expo / Supabase / Telegram bots بأسلوبك.
3. **دمج الأدابتور (Merge):** طلّع نموذج كامل مستقل بدل أدابتور فوق
   أساسي.
4. **GGUF Export:** حوّل النموذج لصيغة GGUF عشان يشتغل محلياً على
   جهازك بـ Ollama / llama.cpp من غير إنترنت.
5. **واجهة:** اربط النموذج ببوت تليجرام أو صفحة تجربة على
   HF Spaces (مع تنبيه: التشغيل الدائم السريع أونلاين هو الحاجة
   الوحيدة اللي غالباً هتحتاج فلوس لو المشروع كبر).

## Files — ملفات المشروع

| الملف | وظيفته |
|---|---|
| `notebooks/train_code_model_colab.ipynb` | نوتبوك التدريب الكاملة لـ Colab (الطريق الرئيسي) |
| `train_local.py` | نفس التدريب كاسكربت مستقل (Kaggle / جهازك) |
| `inference.py` | تجربة النموذج بعد التدريب من سطر الأوامر |
| `requirements.txt` | المكتبات المطلوبة |
| `data/README.md` | شرح الداتا وإزاي تضيف أمثلتك |
| `data/sample_custom.jsonl` | 3 أمثلة جاهزة بصيغة أمثلتك الخاصة |
| `.env.example` | شكل متغير `HF_TOKEN` (من غير أي توكن حقيقي) |
