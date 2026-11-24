#!/usr/bin/env python3
"""
train_local.py — نفس تدريب النوتبوك، بس كاسكربت مستقل.

Same LoRA/QLoRA fine-tune as notebooks/train_code_model_colab.ipynb, as a
standalone script for local machines with a GPU, Kaggle, or any server.

Examples:
    # Default run (3000 samples of Magicoder, push adapter to HF at the end)
    HF_TOKEN=hf_xxx python train_local.py --hf-username darklink56

    # Train on your OWN examples instead (see data/README.md)
    python train_local.py --custom-data data/sample_custom.jsonl --no-push

Requires a CUDA GPU (a free Kaggle/Colab-class T4 with 16GB is enough).
The HF token is read from the HF_TOKEN environment variable or ~/.cache —
never hardcode it here.
"""

import argparse
import os

import torch
from datasets import load_dataset
from peft import LoraConfig
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer

DEFAULT_BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
DEFAULT_DATASET = "ise-uiuc/Magicoder-OSS-Instruct-75K"  # columns: problem / solution


def parse_args():
    p = argparse.ArgumentParser(description="LoRA fine-tune of Qwen2.5-Coder on code instructions")
    p.add_argument("--base-model", default=DEFAULT_BASE_MODEL)
    p.add_argument("--dataset", default=DEFAULT_DATASET,
                   help="HF dataset id with 'problem'/'solution' columns (ignored with --custom-data)")
    p.add_argument("--custom-data", default=None,
                   help="Path to your own JSONL file with 'instruction'/'output' rows (see data/README.md)")
    p.add_argument("--sample-size", type=int, default=3000)
    p.add_argument("--epochs", type=float, default=1.0)
    p.add_argument("--lr", type=float, default=2e-4)
    p.add_argument("--batch", type=int, default=2)
    p.add_argument("--grad-accum", type=int, default=8)
    p.add_argument("--max-seq-len", type=int, default=1024)
    p.add_argument("--output-dir", default="qwen-coder-1.5b-lora-out")
    p.add_argument("--hf-username", default="darklink56",
                   help="Your Hugging Face username (used to build the output repo id)")
    p.add_argument("--hf-repo", default=None,
                   help="Full HF repo id for the adapter (overrides --hf-username)")
    p.add_argument("--no-push", action="store_true", help="Train only, do not push to HF Hub")
    return p.parse_args()


def build_dataset(args, tokenizer):
    """Return a dataset with a single 'text' column rendered via the chat template."""
    if args.custom_data:
        ds = load_dataset("json", data_files=args.custom_data, split="train")
        if args.sample_size and len(ds) > args.sample_size:
            ds = ds.select(range(args.sample_size))

        def fmt(example):
            messages = [
                {"role": "user", "content": example["instruction"]},
                {"role": "assistant", "content": example["output"]},
            ]
            return {"text": tokenizer.apply_chat_template(messages, tokenize=False)}

        return ds.map(fmt, remove_columns=ds.column_names)

    ds = load_dataset(args.dataset, split=f"train[:{args.sample_size}]")

    def fmt(example):
        messages = [
            {"role": "user", "content": example["problem"]},
            {"role": "assistant", "content": example["solution"]},
        ]
        return {"text": tokenizer.apply_chat_template(messages, tokenize=False)}

    return ds.map(fmt, remove_columns=ds.column_names)


def main():
    args = parse_args()
    hf_repo = args.hf_repo or f"{args.hf_username}/qwen-coder-1.5b-lora"

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)
    train_ds = build_dataset(args, tokenizer)
    print(f"Training examples: {len(train_ds)}")

    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True,
    )
    model = AutoModelForCausalLM.from_pretrained(
        args.base_model,
        quantization_config=bnb_config,
        torch_dtype=torch.float16,
        device_map="auto",
    )
    model.config.use_cache = False

    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                        "gate_proj", "up_proj", "down_proj"],
    )

    sft_config = SFTConfig(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        logging_steps=10,
        save_strategy="no",
        fp16=True,
        gradient_checkpointing=True,
        optim="paged_adamw_8bit",
        max_length=args.max_seq_len,  # TRL الجديدة: max_length بدل max_seq_length
        packing=False,
        report_to=[],
    )

    trainer = SFTTrainer(
        model=model,
        args=sft_config,
        train_dataset=train_ds,
        peft_config=lora_config,
        processing_class=tokenizer,
    )
    trainer.train()
    trainer.save_model(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"Adapter saved locally to: {args.output_dir}")

    if not args.no_push:
        if not os.environ.get("HF_TOKEN") and not os.environ.get("HUGGING_FACE_HUB_TOKEN"):
            print("⚠️  No HF_TOKEN found in environment — skipping push. "
                  "Set HF_TOKEN (Write token) and re-run, or push manually with huggingface_hub.")
        else:
            trainer.push_to_hub(hf_repo)
            tokenizer.push_to_hub(hf_repo)
            print(f"✅ Adapter pushed to: https://huggingface.co/{hf_repo}")


if __name__ == "__main__":
    main()
