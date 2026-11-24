#!/usr/bin/env python3
"""
inference.py — جرّب النموذج بعد التدريب: النموذج الأساسي + أدابتور LoRA بتاعك.

Loads the base model (4-bit) + your LoRA adapter from the HF Hub and answers
one coding prompt from the command line.

Examples:
    python inference.py --adapter YOUR_HF_USERNAME/qwen-coder-1.5b-lora \
        --prompt "Write a Python function that checks if a string is a palindrome"

    # Interactive-ish: no --prompt → uses a built-in demo prompt
    python inference.py --adapter YOUR_HF_USERNAME/qwen-coder-1.5b-lora

Needs a CUDA GPU for comfortable speed (works on CPU too, just slow —
drop --no-4bit there if bitsandbytes complains).
"""

import argparse

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

DEFAULT_BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
DEFAULT_ADAPTER = "YOUR_HF_USERNAME/qwen-coder-1.5b-lora"
DEMO_PROMPT = "Write a Python function that returns the nth Fibonacci number using memoization."


def parse_args():
    p = argparse.ArgumentParser(description="Run the fine-tuned code model")
    p.add_argument("--base-model", default=DEFAULT_BASE_MODEL)
    p.add_argument("--adapter", default=DEFAULT_ADAPTER,
                   help="HF repo id (or local path) of your LoRA adapter")
    p.add_argument("--prompt", default=None, help="Coding prompt to answer")
    p.add_argument("--max-new-tokens", type=int, default=300)
    p.add_argument("--no-4bit", action="store_true",
                   help="Load in float16 without bitsandbytes quantization")
    return p.parse_args()


def main():
    args = parse_args()
    prompt = args.prompt or DEMO_PROMPT

    tokenizer = AutoTokenizer.from_pretrained(args.base_model)

    if args.no_4bit:
        model = AutoModelForCausalLM.from_pretrained(
            args.base_model, torch_dtype=torch.float16, device_map="auto",
        )
    else:
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.float16,
            bnb_4bit_use_double_quant=True,
        )
        model = AutoModelForCausalLM.from_pretrained(
            args.base_model, quantization_config=bnb_config, device_map="auto",
        )

    model = PeftModel.from_pretrained(model, args.adapter)
    model.eval()

    messages = [{"role": "user", "content": prompt}]
    text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(text, return_tensors="pt").to(model.device)

    with torch.no_grad():
        out = model.generate(
            **inputs,
            max_new_tokens=args.max_new_tokens,
            do_sample=False,
            pad_token_id=tokenizer.eos_token_id,
        )
    answer = tokenizer.decode(out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)
    print("PROMPT:", prompt)
    print("-" * 80)
    print(answer)


if __name__ == "__main__":
    main()
