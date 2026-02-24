"""
Evaluate a MedGemma TFLite model on MedMCQA.

Uses the TFLite interpreter with signature runner ("decode") and manual
KV-cache management — the same approach proven in the existing codebase.

Usage (default paths):
    cd tflite/inference
    python run_benchmark.py --n-examples 50 --verbose

Usage (custom paths):
    python run_benchmark.py \
        --tflite-model /path/to/model.tflite \
        --tokenizer-dir /path/to/tokenizer \
        --n-examples 100

Requires: tensorflow (or ai-edge-litert), transformers, datasets, tqdm
    pip install tensorflow transformers datasets tqdm
"""

import argparse
import csv
import json
import os
import re
import sys
import time
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np
from datasets import load_dataset
from tqdm import tqdm
from transformers import AutoTokenizer

LETTER_SET = "ABCDEF"

# ─── Architecture constants (MedGemma 4B) ───────────────────────────────────
NUM_LAYERS = 34
NUM_KV_HEADS = 4
HEAD_DIM = 256
BOS_ID = 2
EOS_ID = 1


# ─── TFLite model wrapper ───────────────────────────────────────────────────

class TFLiteMedGemma:
    """Wraps a MedGemma TFLite model for greedy text generation and MCQ logit scoring."""

    def __init__(self, model_path: str, tokenizer_dir: str):
        # Load tokenizer
        self.tokenizer = AutoTokenizer.from_pretrained(tokenizer_dir)

        # Pre-compute token IDs for answer letters A-D
        self.letter_token_ids = {
            letter: self.tokenizer.encode(letter, add_special_tokens=False)[0]
            for letter in "ABCD"
        }

        # Load model bytes into interpreter (Windows-safe)
        print(f"  Reading model file ({os.path.getsize(model_path) / 1e9:.2f} GB)...")
        with open(model_path, "rb") as f:
            model_content = f.read()

        try:
            import tensorflow as tf
            self.interpreter = tf.lite.Interpreter(model_content=model_content)
        except ImportError:
            from ai_edge_litert import interpreter as tfl_interpreter
            self.interpreter = tfl_interpreter.Interpreter(model_content=model_content)

        del model_content
        self.runner = self.interpreter.get_signature_runner("decode")

        # Auto-detect KV_MAX from input tensor shapes
        input_details = self.runner.get_input_details()
        kv_k0_shape = input_details["kv_cache_k_0"]["shape"]  # [1, heads, KV_MAX, head_dim]
        self.kv_max = int(kv_k0_shape[2])
        print(f"  KV cache size (context window): {self.kv_max}")
        print(f"  Answer token IDs: {self.letter_token_ids}")

    def _init_caches(self) -> dict:
        caches = {}
        for i in range(NUM_LAYERS):
            caches[f"kv_cache_k_{i}"] = np.zeros(
                (1, NUM_KV_HEADS, self.kv_max, HEAD_DIM), dtype=np.float32
            )
            caches[f"kv_cache_v_{i}"] = np.zeros(
                (1, NUM_KV_HEADS, HEAD_DIM, self.kv_max), dtype=np.float32
            )
        return caches

    def generate(self, prompt: str, max_new_tokens: int = 8) -> str:
        """Tokenise → prefill → decode → return generated text."""
        input_ids = self.tokenizer.encode(prompt, add_special_tokens=True)

        # Truncate to leave room for generation
        max_prompt_len = self.kv_max - max_new_tokens
        if len(input_ids) > max_prompt_len:
            input_ids = input_ids[:max_prompt_len]

        caches = self._init_caches()

        # ── Prefill (one token at a time) ──
        curr_logits = None
        for pos, token in enumerate(input_ids):
            mask = np.full((1, 1, 1, self.kv_max), -1e9, dtype=np.float32)
            mask[:, :, :, : pos + 1] = 0.0

            inputs = {
                "tokens": np.array([[token]], dtype=np.int32),
                "input_pos": np.array([pos], dtype=np.int32),
                "mask": mask,
                **caches,
            }
            outputs = self.runner(**inputs)
            for k in caches:
                caches[k] = outputs[k]
            curr_logits = outputs["logits"][0, 0]

        # ── Decode (greedy) ──
        generated_tokens: list[int] = []
        next_token = int(np.argmax(curr_logits))
        curr_pos = len(input_ids)

        for _ in range(max_new_tokens):
            if next_token == EOS_ID or next_token == self.tokenizer.eos_token_id:
                break
            if curr_pos >= self.kv_max:
                break

            generated_tokens.append(next_token)

            mask = np.full((1, 1, 1, self.kv_max), -1e9, dtype=np.float32)
            mask[:, :, :, : curr_pos + 1] = 0.0

            inputs = {
                "tokens": np.array([[next_token]], dtype=np.int32),
                "input_pos": np.array([curr_pos], dtype=np.int32),
                "mask": mask,
                **caches,
            }
            outputs = self.runner(**inputs)
            for k in caches:
                caches[k] = outputs[k]
            curr_logits = outputs["logits"][0, 0]
            next_token = int(np.argmax(curr_logits))
            curr_pos += 1

        return self.tokenizer.decode(generated_tokens, skip_special_tokens=True)

    def score_mcq(self, prompt: str, n_options: int = 4) -> Optional[int]:
        """Score an MCQ by comparing logits for A/B/C/D after prefill.

        This is faster and more reliable than generating text, since the model
        may enter thinking mode which wastes tokens.  We just look at which
        answer-letter token has the highest logit right after the prompt.
        """
        input_ids = self.tokenizer.encode(prompt, add_special_tokens=True)

        if len(input_ids) >= self.kv_max:
            # Truncate (leave at least 1 slot)
            input_ids = input_ids[: self.kv_max - 1]

        caches = self._init_caches()

        # ── Prefill ──
        curr_logits = None
        for pos, token in enumerate(input_ids):
            mask = np.full((1, 1, 1, self.kv_max), -1e9, dtype=np.float32)
            mask[:, :, :, : pos + 1] = 0.0

            inputs = {
                "tokens": np.array([[token]], dtype=np.int32),
                "input_pos": np.array([pos], dtype=np.int32),
                "mask": mask,
                **caches,
            }
            outputs = self.runner(**inputs)
            for k in caches:
                caches[k] = outputs[k]
            curr_logits = outputs["logits"][0, 0]

        # ── Compare logits for A, B, C, D ──
        letters = list("ABCD")[:n_options]
        letter_logits = {
            letter: float(curr_logits[self.letter_token_ids[letter]])
            for letter in letters
        }
        best_letter = max(letter_logits, key=letter_logits.get)
        best_index = ord(best_letter) - ord("A")
        return best_index, letter_logits


# ─── Shared evaluation logic ────────────────────────────────────────────────

def normalize_options(example: Dict) -> List[str]:
    if "options" in example and isinstance(example["options"], list):
        return [str(option) for option in example["options"]]

    candidate_keys = ["opa", "opb", "opc", "opd", "ope", "opf"]
    options = [example[key] for key in candidate_keys if key in example and example[key] not in (None, "")]
    if options:
        return [str(option) for option in options]

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
                if 1 <= idx <= len(options):
                    return idx - 1
            if value in LETTER_SET:
                idx = LETTER_SET.index(value)
                if idx < len(options):
                    return idx
    return None


def build_prompt(question: str, options: Sequence[str]) -> str:
    """Build a Gemma-style chat prompt. Kept concise to fit small context windows."""
    letters = LETTER_SET[: len(options)]
    options_text = "\n".join(f"{letter}) {text}" for letter, text in zip(letters, options))
    return (
        "<start_of_turn>user\n"
        "Answer the medical question with ONLY the letter (A/B/C/D).\n\n"
        f"Q: {question}\n"
        f"{options_text}\n"
        "<end_of_turn>\n"
        "<start_of_turn>model\n"
    )


def extract_choice(output_text: str, n_options: int) -> Optional[int]:
    text = output_text.strip()

    # Strip thinking/reasoning block if present
    if text.lower().startswith("thought"):
        parts = re.split(r"\n(?=[A-D]\b)", text, maxsplit=1)
        if len(parts) > 1:
            text = parts[1].strip()

    text_upper = text.upper()

    # Strip everything after <end_of_turn> if present
    if "<END_OF_TURN>" in text_upper:
        text_upper = text_upper.split("<END_OF_TURN>")[0].strip()

    # Strict: standalone letter A-D
    strict = re.search(r"\b([A-D])\b", text_upper)
    if strict:
        idx = ord(strict.group(1)) - ord("A")
        if idx < n_options:
            return idx

    # Fallback: first A-D anywhere
    fallback = re.search(r"[A-D]", text_upper)
    if fallback:
        idx = ord(fallback.group(0)) - ord("A")
        if idx < n_options:
            return idx

    return None


def ensure_parent_dir(file_path: str) -> None:
    parent = os.path.dirname(file_path)
    if parent:
        os.makedirs(parent, exist_ok=True)


def evaluate_model(
    model: TFLiteMedGemma, dataset, max_tokens: int, verbose: bool = False,
    use_logits: bool = True,
) -> Dict:
    correct = 0
    total = 0
    unparsable = 0
    skipped_long = 0
    per_question_times: list[float] = []

    pbar = tqdm(dataset, desc="Evaluating", leave=False)
    for example in pbar:
        question = str(example.get("question", "")).strip()
        options = normalize_options(example)
        gold_index = extract_gold_index(example, options)

        if not question or gold_index is None:
            continue

        prompt = build_prompt(question, options)

        # Check token length fits context window
        prompt_tokens = model.tokenizer.encode(prompt, add_special_tokens=True)
        if len(prompt_tokens) >= model.kv_max:
            skipped_long += 1
            continue

        t0 = time.time()

        if use_logits:
            # ── Logit-based scoring (faster, avoids thinking-mode issues) ──
            pred_index, letter_logits = model.score_mcq(prompt, n_options=len(options))
            response_str = " ".join(
                f"{k}={v:.2f}" for k, v in letter_logits.items()
            )
        else:
            # ── Text generation fallback ──
            response = model.generate(prompt, max_new_tokens=max_tokens)
            # Strip special token artifacts before parsing
            clean = re.sub(r"<[^>]+>", "", response).strip()
            pred_index = extract_choice(clean, len(options))
            response_str = response[:60]

        elapsed_q = time.time() - t0
        per_question_times.append(elapsed_q)

        if verbose:
            gold_letter = LETTER_SET[gold_index]
            pred_letter = LETTER_SET[pred_index] if pred_index is not None else "?"
            marker = "OK" if pred_index == gold_index else "XX"
            tqdm.write(
                f"  [{marker}] gold={gold_letter} pred={pred_letter} "
                f"({elapsed_q:.1f}s, {len(prompt_tokens)}tok) | {response_str}"
            )

        if pred_index is None:
            unparsable += 1
        elif pred_index == gold_index:
            correct += 1

        total += 1

        # Update progress bar with running accuracy
        acc_so_far = correct / total if total else 0
        avg_time = sum(per_question_times) / len(per_question_times)
        pbar.set_postfix(
            acc=f"{acc_so_far:.1%}", avg=f"{avg_time:.1f}s/q", skip=skipped_long
        )

    accuracy = (correct / total) if total else 0.0
    unparsable_rate = (unparsable / total) if total else 0.0
    return {
        "correct": correct,
        "total": total,
        "accuracy": accuracy,
        "unparsable": unparsable,
        "unparsable_rate": unparsable_rate,
        "skipped_too_long": skipped_long,
        "avg_seconds_per_question": round(
            sum(per_question_times) / len(per_question_times), 2
        )
        if per_question_times
        else 0,
    }


def parse_args():
    # Locate default model and tokenizer relative to this script
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_model = os.path.join(
        script_dir, "..", "models", "medgemma_4b_tpu_q8_ekv128.tflite"
    )
    default_tokenizer = os.path.join(script_dir, "..", "tokenizer")

    parser = argparse.ArgumentParser(
        description="Evaluate MedGemma TFLite model on MedMCQA."
    )
    parser.add_argument(
        "--tflite-model",
        type=str,
        default=default_model,
        help="Path to the .tflite model file (default: auto-detected best model)",
    )
    parser.add_argument(
        "--tokenizer-dir",
        type=str,
        default=default_tokenizer,
        help="Directory containing tokenizer files (tokenizer.json, etc.)",
    )
    parser.add_argument("--model-name", type=str, default="MedGemma-TFLite",
                        help="Label for this model in results")
    parser.add_argument("--dataset", type=str, default="openlifescienceai/medmcqa")
    parser.add_argument("--split", type=str, default="validation")
    parser.add_argument("--n-examples", type=int, default=50,
                        help="Number of examples to evaluate (default: 50)")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--max-tokens", type=int, default=8,
                        help="Max tokens to generate per answer (only need 1-2 for MCQ)")
    parser.add_argument("--verbose", action="store_true",
                        help="Print each question's result")
    parser.add_argument("--use-generation", action="store_true",
                        help="Use text generation instead of logit-based scoring "
                             "(slower, may be affected by thinking mode)")
    parser.add_argument(
        "--output-json", type=str, default="results/tflite_medmcqa.json",
    )
    parser.add_argument(
        "--output-csv", type=str, default="results/tflite_medmcqa.csv",
    )
    return parser.parse_args()


def main():
    args = parse_args()

    if not os.path.exists(args.tflite_model):
        print(f"ERROR: Model file not found: {args.tflite_model}")
        sys.exit(1)

    print(f"Model:     {args.tflite_model}")
    print(f"Tokenizer: {args.tokenizer_dir}")
    print(f"Examples:  {args.n_examples}")
    print()

    print("Loading model...")
    model = TFLiteMedGemma(args.tflite_model, args.tokenizer_dir)
    print(f"  Context window: {model.kv_max} tokens")
    print()

    # Quick sanity check: generate a single token
    print("Sanity check (single prompt)...")
    test_out = model.generate(
        "<start_of_turn>user\nWhat is 1+1?<end_of_turn>\n<start_of_turn>model\n",
        max_new_tokens=5,
    )
    print(f"  Model responded: '{test_out}'")
    print()

    print(f"Loading dataset: {args.dataset} [{args.split}]")
    dataset = load_dataset(args.dataset, split=args.split, trust_remote_code=True)
    dataset = dataset.shuffle(seed=args.seed).select(
        range(min(args.n_examples, len(dataset)))
    )

    use_logits = not args.use_generation
    mode = "logit-based scoring" if use_logits else "text generation"
    print(f"Evaluating on {len(dataset)} examples ({mode})...\n")
    t0 = time.time()
    metrics = evaluate_model(
        model, dataset, max_tokens=args.max_tokens, verbose=args.verbose,
        use_logits=use_logits,
    )
    elapsed = time.time() - t0

    metrics["model_path"] = args.tflite_model
    metrics["time_seconds"] = round(elapsed, 1)
    metrics["questions_per_second"] = (
        round(metrics["total"] / elapsed, 2) if elapsed > 0 else 0
    )

    print(
        f"\n{'='*60}\n"
        f"Accuracy: {metrics['accuracy'] * 100:.2f}% "
        f"({metrics['correct']}/{metrics['total']}) | "
        f"Unparsable: {metrics['unparsable_rate'] * 100:.1f}% | "
        f"Skipped (too long): {metrics['skipped_too_long']}\n"
        f"Time: {elapsed:.1f}s total, "
        f"{metrics['avg_seconds_per_question']:.1f}s/question, "
        f"{metrics['questions_per_second']:.2f} q/s\n"
        f"{'='*60}"
    )

    # Save results
    ensure_parent_dir(args.output_json)
    with open(args.output_json, "w", encoding="utf-8") as f:
        json.dump(
            {
                "config": {
                    "model": args.tflite_model,
                    "model_name": args.model_name,
                    "dataset": args.dataset,
                    "split": args.split,
                    "n_examples": args.n_examples,
                    "seed": args.seed,
                    "max_tokens": args.max_tokens,
                    "kv_max": model.kv_max,
                },
                "results": {args.model_name: metrics},
            },
            f,
            indent=2,
        )

    ensure_parent_dir(args.output_csv)
    with open(args.output_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "model", "accuracy_percent", "correct", "total",
            "unparsable_percent", "skipped_too_long",
            "time_seconds", "avg_s_per_question", "questions_per_second", "model_path",
        ])
        writer.writerow([
            args.model_name,
            f"{metrics['accuracy'] * 100:.4f}",
            metrics["correct"],
            metrics["total"],
            f"{metrics['unparsable_rate'] * 100:.4f}",
            metrics["skipped_too_long"],
            metrics["time_seconds"],
            metrics["avg_seconds_per_question"],
            metrics["questions_per_second"],
            args.tflite_model,
        ])

    print(f"\nSaved JSON: {args.output_json}")
    print(f"Saved CSV:  {args.output_csv}")


if __name__ == "__main__":
    main()
