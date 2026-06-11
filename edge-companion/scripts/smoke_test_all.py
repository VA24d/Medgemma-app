#!/usr/bin/env python3
"""Smoke test edge-companion APIs (three-tier inference + sync)."""
from __future__ import annotations

import json
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8787"
FAILURES: list[str] = []


def req(method: str, path: str, body: dict | None = None, timeout: int = 120) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(
        f"{BASE}{path}",
        data=data,
        headers={"Content-Type": "application/json"} if data else {},
        method=method,
    )
    with urllib.request.urlopen(r, timeout=timeout) as resp:
        return json.loads(resp.read())


def check(name: str, cond: bool, detail: str = "") -> None:
    if cond:
        print(f"  PASS  {name}")
    else:
        msg = f"  FAIL  {name}" + (f" — {detail}" if detail else "")
        print(msg)
        FAILURES.append(name)


def main() -> int:
    print("=== Edge companion smoke test ===\n")

    try:
        h = req("GET", "/health", timeout=10)
    except Exception as e:
        print(f"FAIL health: {e}")
        return 1

    check("health status", h.get("status") in ("ok", "degraded"), str(h.get("status")))
    check("ollama_ok field", "ollama_ok" in h)
    check("gemini_configured field", "gemini_configured" in h, str(h.get("gemini_configured")))
    check("chat_backend field", h.get("chat_backend") in ("ollama", "gemini"))
    check("batch_backend field", h.get("batch_backend") in ("ollama", "gemini"))

    settings = req("GET", "/v1/settings")
    check("settings gemini_configured", "gemini_configured" in settings)
    check("settings chat_backend", settings.get("chat_backend") in ("ollama", "gemini"))

    patients = req("GET", "/v1/patients")
    check("patients list", isinstance(patients, list))

    pull = req("GET", "/v1/sync/pull?since=0&device_id=smoke-test")
    check("sync pull", "cursor" in pull and "patients" in pull)

    ping = req("POST", "/v1/ping", {"device_label": "smoke-test"})
    check("ping", ping.get("ok") is True)

    for backend in ("ollama", "gemini"):
        try:
            chat = req("POST", "/v1/chat", {"message": "Reply with exactly: OK", "backend": backend}, timeout=90)
            reply = (chat.get("reply") or "").strip()
            check(f"chat backend={backend}", "OK" in reply.upper() or len(reply) > 0, reply[:80])
        except Exception as e:
            if backend == "gemini" and not h.get("gemini_configured"):
                check(f"chat backend={backend}", False, "key missing — expected if no .env")
            else:
                check(f"chat backend={backend}", False, str(e))

    try:
        html = urllib.request.urlopen(f"{BASE}/patients", timeout=10).read()
        check("SPA /patients", b"<!DOCTYPE" in html or b"<html" in html.lower())
    except Exception as e:
        check("SPA /patients", False, str(e))

    print()
    if FAILURES:
        print(f"FAILED ({len(FAILURES)}): {', '.join(FAILURES)}")
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
