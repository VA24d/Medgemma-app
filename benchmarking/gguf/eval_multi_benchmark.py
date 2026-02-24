#!/usr/bin/env python3
"""
Evaluate MedGemma GGUF models across multiple medical benchmarks.

Benchmarks:
  - MedMCQA       (openlifescienceai/medmcqa, validation, 4183 examples)
  - MedQA 4-op    (GBaker/MedQA-USMLE-4-options, test, 1273 examples)
  - PubMedQA      (qiaojin/PubMedQA pqa_labeled, train, 1000 examples)
  - MMLU Med      (cais/mmlu, 6 medical subsets, test, ~1089 total)
  - MedXpertQA    (TsinghuaC3I/MedXpertQA Text, test, 2450 examples)

Usage:
    # Quick smoke test
    python eval_multi_benchmark.py --models-config models.json --n-examples 50

    # Full run (500 per benchmark, 4 models)
    python eval_multi_benchmark.py \\
        --models-config models.json \\
        --benchmarks medmcqa medqa pubmedqa mmlu_med medxpertqa \\
        --n-examples 500 --baseline BF16

    # Single benchmark
    python eval_multi_benchmark.py --models-config models.json --benchmarks medmcqa --n-examples 0

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

LETTER_SET = "ABCDEFGHIJ"


# ─── Dataset loaders ─────────────────────────────────────────────────────────

def load_medmcqa(n_examples: int, seed: int) -> list:
    ds = load_dataset("medmcqa", split="validation")
    if n_examples > 0:
        ds = ds.shuffle(seed=seed).select(range(min(n_examples, len(ds))))
    examples = []
    for row in ds:
        opts = [str(row[k]) for k in ["opa", "opb", "opc", "opd"] if row.get(k) not in (None, "")]
        gold = row.get("cop")
        if isinstance(gold, int) and 0 <= gold < len(opts):
            examples.append({"question": row["question"], "options": opts, "gold": gold})
    return examples


def load_medqa(n_examples: int, seed: int) -> list:
    ds = load_dataset("GBaker/MedQA-USMLE-4-options", split="test")
    if n_examples > 0:
        ds = ds.shuffle(seed=seed).select(range(min(n_examples, len(ds))))
    examples = []
    for row in ds:
        opts_dict = row["options"]
        letters = sorted(opts_dict.keys())
        opts = [opts_dict[l] for l in letters]
        answer = row["answer_idx"].strip().upper()
        if answer in letters:
            examples.append({"question": row["question"], "options": opts, "gold": letters.index(answer)})
    return examples


def load_pubmedqa(n_examples: int, seed: int) -> list:
    ds = load_dataset("qiaojin/PubMedQA", "pqa_labeled", split="train")
    if n_examples > 0:
        ds = ds.shuffle(seed=seed).select(range(min(n_examples, len(ds))))
    label_map = {"yes": 0, "no": 1, "maybe": 2}
    opts = ["Yes", "No", "Maybe"]
    examples = []
    for row in ds:
        decision = row["final_decision"].strip().lower()
        if decision in label_map:
            context = ""
            ctx = row.get("context", {})
            if isinstance(ctx, dict):
                abstracts = ctx.get("contexts", [])
                if abstracts:
                    context = "\n".join(abstracts) + "\n\n"
            examples.append({
                "question": context + row["question"],
                "options": opts,
                "gold": label_map[decision],
            })
    return examples


def load_mmlu_med(n_examples: int, seed: int) -> list:
    subjects = [
        "anatomy", "clinical_knowledge", "college_biology",
        "college_medicine", "medical_genetics", "professional_medicine",
    ]
    all_ex = []
    for subj in subjects:
        ds = load_dataset("cais/mmlu", subj, split="test")
        for row in ds:
            opts = list(row["choices"])
            gold = row["answer"]
            if isinstance(gold, int) and 0 <= gold < len(opts):
                all_ex.append({"question": row["question"], "options": opts, "gold": gold})
    if n_examples > 0:
        import random
        random.Random(seed).shuffle(all_ex)
        all_ex = all_ex[:n_examples]
    return all_ex


def load_medxpertqa(n_examples: int, seed: int) -> list:
    ds = load_dataset("TsinghuaC3I/MedXpertQA", "Text", split="test")
    if n_examples > 0:
        ds = ds.shuffle(seed=seed).select(range(min(n_examples, len(ds))))
    examples = []
    for row in ds:
        opts_dict = row["options"]
        letters = sorted(opts_dict.keys())
        opts = [opts_dict[l] for l in letters]
        label = row["label"].strip().upper()
        if label in letters:
            examples.append({"question": row["question"], "options": opts, "gold": letters.index(label)})
    return examples


BENCHMARK_LOADERS = {
    "medmcqa": ("MedMCQA", load_medmcqa),
    "medqa": ("MedQA (4-op)", load_medqa),
    "pubmedqa": ("PubMedQA", load_pubmedqa),
    "mmlu_med": ("MMLU Med", load_mmlu_med),
    "medxpertqa": ("MedXpertQA", load_medxpertqa),
}


# ─── Prompt & extraction ────────────────────────────────────────────────────

def build_prompt(question: str, options: Sequence[str]) -> str:
    letters = LETTER_SET[: len(options)]
    options_text = "\n".join(f"{letter}) {text}" for letter, text in zip(letters, options))
    return (
        "<start_of_turn>user\n"
        "You are a medical expert. Answer the following multiple-choice question.\n"
        f"Respond with ONLY the single letter of the correct option ({', '.join(letters)}). "
        "Do not explain.\n\n"
        f"Question: {question}\n\n"
        f"Options:\n{options_text}\n"
        "<end_of_turn>\n"
        "<start_of_turn>model\n"
    )


def extract_choice(output_text: str, n_options: int) -> Optional[int]:
    text = output_text.strip()
    if text.lower().startswith("thought"):
        valid = LETTER_SET[:n_options]
        parts = re.split(r"\n(?=[" + valid + r"]\b)", text, maxsplit=1)
        if len(parts) > 1:
            text = parts[1].strip()
    text_upper = text.upper()
    if "<END_OF_TURN>" in text_upper:
        text_upper = text_upper.split("<END_OF_TURN>")[0].strip()
    valid = LETTER_SET[:n_options]
    strict = re.search(r"\b([" + valid + r"])\b", text_upper)
    if strict:
        return LETTER_SET.index(strict.group(1))
    fallback = re.search(r"[" + valid + r"]", text_upper)
    if fallback:
        return LETTER_SET.index(fallback.group(0))
    return None


# ─── Evaluation ──────────────────────────────────────────────────────────────

def evaluate_model(llm: Llama, examples: list, max_tokens: int) -> Dict:
    correct = total = unparsable = 0
    for ex in tqdm(examples, desc="Evaluating", leave=False):
        prompt = build_prompt(ex["question"], ex["options"])
        output = llm(prompt, max_tokens=max_tokens, temperature=0.0, top_p=1.0,
                      stop=["<end_of_turn>"])
        pred = extract_choice(output["choices"][0]["text"], len(ex["options"]))
        if pred is None:
            unparsable += 1
        elif pred == ex["gold"]:
            correct += 1
        total += 1
    accuracy = (correct / total) if total else 0.0
    return {
        "correct": correct, "total": total, "accuracy": accuracy,
        "unparsable": unparsable, "unparsable_rate": (unparsable / total) if total else 0.0,
    }


# ─── Main ────────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="Multi-benchmark MedGemma GGUF evaluation")
    p.add_argument("--models-config", type=str, required=True)
    p.add_argument("--benchmarks", nargs="+", default=list(BENCHMARK_LOADERS.keys()),
                   choices=list(BENCHMARK_LOADERS.keys()))
    p.add_argument("--n-examples", type=int, default=0, help="0 = full dataset")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--n-ctx", type=int, default=4096)
    p.add_argument("--n-gpu-layers", type=int, default=-1)
    p.add_argument("--max-tokens", type=int, default=64)
    p.add_argument("--baseline", type=str, default="BF16")
    p.add_argument("--output-dir", type=str, default="results")
    return p.parse_args()


def main():
    args = parse_args()
    os.makedirs(args.output_dir, exist_ok=True)

    with open(args.models_config) as f:
        models = json.load(f)

    # Load benchmarks
    benchmark_data = {}
    for key in args.benchmarks:
        name, loader = BENCHMARK_LOADERS[key]
        print(f"Loading {name}...")
        data = loader(args.n_examples, args.seed)
        benchmark_data[key] = (name, data)
        print(f"  {len(data)} examples loaded")

    # Evaluate each model
    all_results = {}
    for model_info in models:
        mname, mpath = model_info["name"], model_info["path"]
        if not os.path.exists(mpath):
            print(f"\nSkipping {mname}: not found at {mpath}")
            continue

        print(f"\n{'='*60}\nLoading model: {mname}\n{'='*60}")
        llm = Llama(model_path=mpath, n_ctx=args.n_ctx, n_gpu_layers=args.n_gpu_layers,
                     n_threads=max(1, (os.cpu_count() or 8) // 2), verbose=False)

        model_results = {}
        for key in args.benchmarks:
            bname, examples = benchmark_data[key]
            print(f"\n  --- {bname} ({len(examples)} examples) ---")
            t0 = time.time()
            metrics = evaluate_model(llm, examples, args.max_tokens)
            elapsed = time.time() - t0
            metrics["time_seconds"] = round(elapsed, 1)
            metrics["questions_per_second"] = round(metrics["total"] / elapsed, 2) if elapsed else 0
            model_results[key] = metrics
            print(f"  {bname}: {metrics['accuracy']*100:.2f}% ({metrics['correct']}/{metrics['total']}) | {elapsed:.1f}s")

        all_results[mname] = model_results
        del llm

    # Summary table
    bench_names = [BENCHMARK_LOADERS[b][0] for b in args.benchmarks]
    header = f"{'Model':<12}" + "".join(f"{n:>14}" for n in bench_names)
    print(f"\n{'='*60}\nFINAL SUMMARY\n{'='*60}")
    print(header)
    print("-" * len(header))
    for mname, mr in all_results.items():
        row = f"{mname:<12}"
        for key in args.benchmarks:
            if key in mr:
                row += f"{mr[key]['accuracy']*100:>13.2f}%"
            else:
                row += f"{'N/A':>14}"
        print(row)

    baseline = all_results.get(args.baseline)
    if baseline:
        print(f"\nDrop vs {args.baseline} (percentage points):")
        print("-" * len(header))
        for mname, mr in all_results.items():
            if mname == args.baseline:
                continue
            row = f"{mname:<12}"
            for key in args.benchmarks:
                if key in mr and key in baseline:
                    drop = (baseline[key]["accuracy"] - mr[key]["accuracy"]) * 100
                    row += f"{drop:>+13.2f}%"
                else:
                    row += f"{'N/A':>14}"
            print(row)

    # Save
    with open(os.path.join(args.output_dir, "multi_bench_results.json"), "w") as f:
        json.dump({"config": {"benchmarks": args.benchmarks, "n_examples": args.n_examples,
                               "seed": args.seed, "baseline": args.baseline},
                    "results": all_results}, f, indent=2)

    with open(os.path.join(args.output_dir, "multi_bench_summary.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["model", "benchmark", "accuracy_pct", "correct", "total",
                     "unparsable_pct", "time_s", "q_per_s"])
        for mname, mr in all_results.items():
            for key in args.benchmarks:
                if key in mr:
                    m = mr[key]
                    w.writerow([mname, BENCHMARK_LOADERS[key][0], f"{m['accuracy']*100:.2f}",
                                m["correct"], m["total"], f"{m['unparsable_rate']*100:.2f}",
                                m["time_seconds"], m["questions_per_second"]])

    print(f"\nSaved to {args.output_dir}/")


if __name__ == "__main__":
    main()
