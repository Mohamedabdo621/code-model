# Training data — الداتا بتشتغل إزاي

## Default dataset (what the first run uses)

**`ise-uiuc/Magicoder-OSS-Instruct-75K`** on Hugging Face Hub
(verified 2026-11-24: exists, **not gated**, license **MIT** per its dataset card).

- ~75K code-instruction examples generated from open-source code seeds
  (the "OSS-Instruct" method from the Magicoder paper).
- Columns used by this project:

  | column     | meaning                                   |
  |------------|-------------------------------------------|
  | `problem`  | The coding task / instruction (user turn) |
  | `solution` | The reference solution (assistant turn)   |

  (The dataset also has `lang`, `seed`, `index`, `raw_index`,
  `openai_fingerprint` columns — we ignore those.)

- The notebook/script takes only the first `SAMPLE_SIZE` rows
  (default **3000**) so a first run fits in one free Colab session.

### License caveat ⚠️

The dataset card lists the license as **MIT**, and the base model
`Qwen/Qwen2.5-Coder-1.5B-Instruct` is **Apache-2.0**. The training examples
were *generated with the help of GPT-3.5* from open-source snippets — before
using a fine-tuned model **commercially**, re-check the current dataset card
and the terms of the tools used to generate the data. For learning and
personal projects this is fine.

## Adding YOUR OWN examples — كده النموذج يبقى «بتاعك»

The public dataset teaches the model general code-instruction behavior.
What makes the model *yours* is fine-tuning on **your** examples: your
style, your stacks (Expo / Supabase / Telegram bots…), your language mix.

Format: one JSON object per line (JSONL), two keys only:

```json
{"instruction": "the task, as a user would ask it", "output": "the answer you want the model to learn"}
```

See **`sample_custom.jsonl`** in this folder — 3 ready examples
(Python, JavaScript, SQL). Copy that file, replace the rows with yours,
and aim for quality over quantity: 200–1000 clean, correct examples beat
10,000 sloppy ones.

### How to train on your file

- **Script:** `python train_local.py --custom-data data/sample_custom.jsonl`
- **Notebook:** in the dataset cell, replace the `load_dataset(...)` line with:

  ```python
  from datasets import load_dataset
  raw = load_dataset("json", data_files="data/sample_custom.jsonl", split="train")
  # then map raw["instruction"] → user, raw["output"] → assistant
  # (same chat-template formatting, just different column names)
  ```

- You can also **mix**: train first on Magicoder (general), then run a
  second, shorter LoRA pass on your custom file to specialize it.
