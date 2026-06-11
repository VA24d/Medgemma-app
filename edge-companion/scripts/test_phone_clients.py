#!/usr/bin/env python3
"""Simulate Android EdgeCompanionClient + GeminiClient against live stack."""
from __future__ import annotations

import json
import os
import sys
import urllib.request
from pathlib import Path

from dotenv import load_dotenv

BASE = "http://127.0.0.1:8787"
REPO = Path(__file__).resolve().parent.parent.parent
load_dotenv(REPO / ".env")


def post(path: str, body: dict, timeout: int = 120) -> dict:
    data = json.dumps(body).encode()
    req = urllib.request.Request(
        f"{BASE}{path}",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read())


def gemini_direct(prompt: str, model: str = "gemini-2.5-flash") -> str:
    key = os.getenv("GEMINI_API_KEY", "").strip()
    if not key:
        raise RuntimeError("no GEMINI_API_KEY")
    body = {"contents": [{"parts": [{"text": prompt}]}]}
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
    data = json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        j = json.loads(r.read())
    parts = j["candidates"][0]["content"]["parts"]
    return "".join(p.get("text", "") for p in parts).strip()


def main() -> int:
    print("Tier 2 — EdgeCompanionClient.chat (ollama)")
    r = post("/v1/chat", {"message": "Say hello in 3 words", "backend": "ollama"})
    print(" ", (r.get("reply") or "")[:100])

    print("Tier 2 — process/entry (ollama)")
    r = post(
        "/v1/process/entry",
        {
            "patient_name": "Test",
            "entry_type": "NOTE",
            "title": "Smoke",
            "content": "Patient reports mild cough.",
            "prompt": "Summarize this clinical note in one sentence.",
            "backend": "ollama",
            "num_predict": 128,
        },
        timeout=180,
    )
    print(" ", (r.get("analysis_result") or "")[:100])

    print("Tier 3 — GeminiClient direct (phone path)")
    print(" ", gemini_direct("Reply with exactly: OK")[:60])

    print("Tier 3 — companion chat (gemini backend)")
    r = post("/v1/chat", {"message": "Reply with exactly: OK", "backend": "gemini"})
    print(" ", (r.get("reply") or "")[:100])

    print("Tier 3 — process/entry (gemini backend)")
    r = post(
        "/v1/process/entry",
        {
            "patient_name": "Test",
            "entry_type": "NOTE",
            "title": "Smoke Gemini",
            "content": "BP 120/80, no acute distress.",
            "prompt": "One sentence clinical summary.",
            "backend": "gemini",
            "num_predict": 128,
        },
        timeout=180,
    )
    print(" ", (r.get("analysis_result") or "")[:100])

    print("\nALL CLIENT PATHS OK")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print(f"FAIL: {e}")
        sys.exit(1)
