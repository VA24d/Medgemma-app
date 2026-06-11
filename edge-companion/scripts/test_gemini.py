#!/usr/bin/env python3
"""Validate GEMINI_API_KEY from .env without printing the key."""
from __future__ import annotations

import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = ROOT.parent


def load_env() -> None:
    try:
        from dotenv import load_dotenv
    except ImportError:
        print("FAIL: install python-dotenv (pip install -r requirements.txt)")
        sys.exit(1)
    for path in (REPO_ROOT / ".env", ROOT / ".env"):
        if path.exists():
            load_dotenv(path)
            print(f"Loaded env from {path}")
            return
    print("FAIL: no .env found at repo root or edge-companion/")


def main() -> None:
    load_env()
    key = os.getenv("GEMINI_API_KEY", "").strip()
    if not key:
        print("FAIL: GEMINI_API_KEY is empty")
        sys.exit(1)
    print(f"Key present ({len(key)} chars, prefix {key[:4]}...)")

    try:
        from google import genai
    except ImportError:
        print("FAIL: install google-genai (pip install -r requirements.txt)")
        sys.exit(1)

    client = genai.Client(api_key=key)
    model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

    try:
        response = client.models.generate_content(
            model=model,
            contents="Reply with exactly: OK",
        )
        text = (response.text or "").strip()
        print(f"Model: {model}")
        print(f"Response: {text[:120]}")
        if "OK" in text.upper():
            print("PASS: Gemini API key works")
            sys.exit(0)
        print("WARN: unexpected response but API call succeeded")
        sys.exit(0)
    except Exception as e:
        print(f"FAIL: {type(e).__name__}: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
