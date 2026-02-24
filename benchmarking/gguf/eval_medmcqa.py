#!/usr/bin/env python3
"""
Evaluate MedGemma GGUF quantizations on MedMCQA.

Compares multiple GGUF quantization levels (BF16, Q8_0, Q6_K, Q4_K_M)
on the MedMCQA validation set using llama-cpp-python with GPU acceleration.

Usage:
    # Quick test (50 examples)
    python eval_medmcqa.py --models-config models.json --n-examples 50

    # Full benchmark (500 examples, recommended)
    python eval_medmcqa.py --models-config models.json --n-examples 500 --baseline BF16

    # Full dataset
    python eval_medmcqa.py --models-config models.json --n-examples 0

Requirements:
    pip install llama-cpp-python datasets tqdm

    For GPU acceleration (recommended):
    CMAKE_ARGS="-DGGML_CUDA=on" pip install llama-cpp-python --force-reinstall --no-cache-dir
"""

import argparse
import csv
import json
import os
import re
import time
from typing import Dict, List, Optional, Sequence

from datasets import load_dataset
from llama_cpp import Llama
from tqdm import tqdm

LETTER_SET = "ABCDEF"


# ─── Prompt & extraction ────────────────────────────────────────────────────

def build_prompt(question: str, options: Sequence[str]) -> str:
    """Build a Gemma 3 chat-template prompt for MCQ."""
    letters = LETTER_SET[: len(options)]
    options_text = "\n".join(f"{letter}) {text}" for letter, text in zip(letters, options))
    return (
        "<start_of_turn>user\n"
        "You are a medical expert. Answer the following multiple-choice question.\n"
        "Respond with ONLY the single letter of the correct option (A, B, C, or D). "
        "Do not explain.\n\n"
        f"Question: {question}\n\n"
        f"Options:\n{options_text}\n"
        "<end_of_turn>\n"
        "<start_of_turn>model\n"
    )


def extract_choice(output_text: str, n_options: int) -> Optional[int]:
    """Parse the model's output to extract the chosen letter (A-D)."""
    text = output_text.strip()

    # Strip thinking/reasoning block if present
    if text.lower().startswith("thought"):
        parts = re.split(r"\n(?=[A-D]\b)", text, maxsplit=1)
        if len(parts) > 1:
            text = parts[1].strip()

    text_upper = text.upper()

    # Strict: standalone letter A-D
    strict = re.search(r"\b([A-D])\b", text_upper)
    if strict:
        idx = ord(strict.group(1)) - ord("A")
        if idx < n_options:
            return idx

    # Fallback: first A-D letter anywhere
    fallback = re.search(r"[A-D]", text_upper)
    if fallback:
        idx = ord(fallback.group(0)) - ord("A")
        if idx < n_options:
            return idx

    return None


# ─── Dataset helpers ─────────────────────────────────────────────────────────

def normalize_options(example: Dict) -> List[str]:
    if "options" in example and isinstance(example["options"], list):
        return [str(o) for o in example["options"]]
    candidate_keys = ["opa", "opb", "opc", "opd"]
    options = [example[k] for k in candidate_keys if k in example and example[k] not in (None, "")]
    if options:
        return [str(o) for o in options]
    raise ValueError("Unable to infer options from dataset row")


def extract_gold_index(example: Dict, options: Sequence[str]) -> Optional[int]:
    for key in ("answer", "cop", "label"):
        if key not in example:
            continue
        value = example[key]
        if isinstance(value, int):
            if 0 <= value < len(options):
                return value
            if 1 <= value <= len(options):
                return value - 1
        if isinstance(value, str):
            value = value.strip().upper()
            if value.isdigit():
                idx = int(value)
                if 0 <= idx < len(options):
                    return idx
            if value in LETTER_SET:
                idx = LETTER_SET.index(value)
                if idx < len(options):
                    return idx
    return None


# ─── Evaluation ──────────────────────────────────────────────────────────────

def evaluate_model(llm: Llama, dataset, max_tokens: int) -> Dict:
    correct = 0
    total = 0
    unparsable = 0

    for example in tqdm(dataset, desc="Evaluating", leave=False):
        question = str(example.get("question", "")).strip()
        options = normalize_options(example)
        gold_index = extract_gold_index(example, options)
        if not question or gold_index is None:
            continue

        prompt = build_prompt(question, options)
        output = llm(
            prompt,
            max_tokens=max_tokens,
            temperature=0.0,
            top_p=1.0,
            stop=["<end_of_turn>"],
        )

        text = output["choices"][0]["text"]
        pred_index = extract_choice(text, len(options))
        if pred_index is None:
            unparsable += 1
        elif pred_index == gold_index:
            correct += 1
        total += 1

    accuracy = (correct / total) if total else 0.0
    return {
        "correct": correct,
        "total": total,
        "accuracy": accuracy,
        "unparsable": unparsable,
        "unparsable_rate": (unparsable / total) if total else 0.0,
    }


# ─── Main ────────────────────────────────────────────────────────────────────

def parse_args():
    parser = argparse.ArgumentParser(
        description="Evaluate MedGemma GGUF quantizations on MedMCQA."
    )
    parser.add_argument("--models-config", type=str, required=True,
                        help="JSON file: [{\"name\": \"BF16\", \"path\": \"/path/to/model.gguf\"}, ...]")
    parser.add_argument("--n-examples", type=int, default=500,
                        help="Number of examples (0 = full dataset)")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--n-ctx", type=int, default=4096)
    parser.add_argument("--n-gpu-layers", type=int, default=-1,
                        help="-1 = offload all layers to GPU")
    parser.add_argument("--max-tokens", type=int, default=64)
    parser.add_argument("--baseline", type=str, default="BF16")
    parser.add_argument("--output-json", type=str, default="results/medmcqa_results.json")
    parser.add_argument("--output-csv", type=str, default="results/medmcqa_summary.csv")
    return parser.parse_args()


def main():
    args = parse_args()

    with open(args.models_config) as f:
        models = json.load(f)

    print(f"Loading dataset: medmcqa [validation]")
    dataset = load_dataset("medmcqa", split="validation")
    if args.n_examples > 0:
        dataset = dataset.shuffle(seed=args.seed).select(
            range(min(args.n_examples, len(dataset)))
        )

    all_results = {}
    for item in models:
        name, path = item["name"], item["path"]
        print(f"\n{'='*60}\n=== {name} ===\n{'='*60}")
        if not os.path.exists(path):
            print(f"  Skipping: file not found at {path}")
            continue

        llm = Llama(
            model_path=path,
            n_ctx=args.n_ctx,
            n_gpu_layers=args.n_gpu_layers,
            n_threads=max(1, (os.cpu_count() or 8) // 2),
            verbose=False,
        )

        t0 = time.time()
        metrics = evaluate_model(llm, dataset, args.max_tokens)
        elapsed = time.time() - t0

        metrics["time_seconds"] = round(elapsed, 1)
        metrics["questions_per_second"] = round(metrics["total"] / elapsed, 2) if elapsed else 0
        all_results[name] = {"model_path": path, **metrics}

        print(
            f"  Accuracy: {metrics['accuracy']*100:.2f}% "
            f"({metrics['correct']}/{metrics['total']}) | "
            f"Unparsable: {metrics['unparsable_rate']*100:.1f}% | "
            f"{elapsed:.1f}s ({metrics['questions_per_second']:.2f} q/s)"
        )
        del llm

    # Compute drops vs baseline
    baseline = all_results.get(args.baseline)
    for name, m in all_results.items():
        if baseline and name != args.baseline:
            m["drop_vs_baseline_pp"] = round(
                (baseline["accuracy"] - m["accuracy"]) * 100, 2
            )
        else:
            m["drop_vs_baseline_pp"] = 0.0

    # Print summary
    print(f"\n{'='*60}\nFINAL SUMMARY\n{'='*60}")
    print(f"{'Model':<12} {'Accuracy':>10} {'Drop(pp)':>10} {'Time':>10}")
    print("-" * 44)
    for name, m in all_results.items():
        print(
            f"{name:<12} {m['accuracy']*100:>9.2f}% "
            f"{m['drop_vs_baseline_pp']:>+9.2f} "
            f"{m['time_seconds']:>9.1f}s"
        )

    # Save
    os.makedirs(os.path.dirname(args.output_json) or ".", exist_ok=True)
    with open(args.output_json, "w") as f:
        json.dump({"config": vars(args), "results": all_results}, f, indent=2)

    os.makedirs(os.path.dirname(args.output_csv) or ".", exist_ok=True)
    with open(args.output_csv, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["model", "accuracy_pct", "correct", "total", "unparsable_pct",
                     "drop_vs_baseline_pp", "time_s", "q_per_s", "model_path"])
        for name, m in all_results.items():
            w.writerow([name, f"{m['accuracy']*100:.2f}", m["correct"], m["total"],
                        f"{m['unparsable_rate']*100:.2f}", m["drop_vs_baseline_pp"],
                        m["time_seconds"], m["questions_per_second"], m["model_path"]])

    print(f"\nSaved: {args.output_json}, {args.output_csv}")


if __name__ == "__main__":
    main()
