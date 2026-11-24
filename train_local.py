#!/usr/bin/env python3
"""
train_local.py — نفس تدريب النوتبوك، بس كاسكربت مستقل (الإصدار النظيف v2).

Same LoRA/QLoRA fine-tune as notebooks/train_code_model_colab.ipynb, as a
standalone script for local machines with a GPU, Kaggle, or any server.
Dependencies are EXACT pins (see requirements.txt): trl==0.29.1,
transformers==4.57.6, peft==0.22.0, accelerate==1.16.0,
bitsandbytes==0.50.2, datasets==4.8.5 — verified against the pinned
wheels' source code.

v2 fixes vs v1:
  * SFTConfig uses max_length (TRL removed max-seq-length).
  * fp16=False / bf16=False in SFTConfig: TRL 0.29.1 casts QLoRA adapter
    weights to bf16 itself; with fp16=True the GradScaler crashes on T4
    with NotImplementedError "... not implemented for 'BFloat16'".
    Compute still runs in float16 inside bitsandbytes.
  * Model loaded with torch_dtype=torch.float16, tokenizer.pad_token set,
    model.enable_input_require_grads() + use_reentrant=False for
    gradient checkpointing + LoRA.

Examples:
    # Default run (3000 samples of Magicoder, push adapter to HF at the end)
    HF_TOKEN=<your-write-token> python train_local.py

    # Train on your OWN examples instead (see data/README.md)
    python train_local.py --custom-data data/sample_custom.jsonl --no-push

Requires a CUDA GPU (a free Kaggle/Colab-class T4 with 16GB is enough).
The HF token is read from the HF_TOKEN environment variable or the local
HF cache — never hardcode it here.
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
    """Return a dataset with a single 'text' column rendered via the chat template.

    We do NOT pre-tokenize: SFTTrainer (TRL 0.29.1) tokenizes the "text"
    field itself — that is its documented, source-verified behavior.
    """
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
    tokenizer.pad_token = "<|endoftext|>"  # Qwen has no separate pad token
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
    model.enable_input_require_grads()  # required with gradient checkpointing + LoRA

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
        fp16=False,  # must stay False: TRL casts QLoRA adapters to bf16;
        bf16=False,  # fp16 GradScaler + bf16 grads = BFloat16 crash on T4
        gradient_checkpointing=True,
        gradient_checkpointing_kwargs={"use_reentrant": False},
        optim="paged_adamw_8bit",
        max_length=args.max_seq_len,  # TRL >= 0.29: max_length (max-seq-length removed)
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
