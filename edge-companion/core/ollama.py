from __future__ import annotations

import re
import socket
from typing import Any, Optional

import httpx

from core.config import DEFAULT_MODEL, OLLAMA_BASE

DIRECT_PREFIX = (
    "Answer directly for clinical documentation. "
    "No chain-of-thought, planning, or meta commentary. "
    "Do not use <unused94> or thinking blocks.\n\n"
)

THINK_BLOCK_RE = re.compile(r"<unused94>thought>[\s\S]*?<unused95>")


def strip_thinking(text: str) -> str:
    out = THINK_BLOCK_RE.sub("", text)
    return (
        out.replace("<unused94>", "")
        .replace("<unused95>", "")
        .replace("thought>", "")
        .strip()
    )


def lan_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except OSError:
        return "127.0.0.1"


async def ollama_tags() -> dict:
    async with httpx.AsyncClient(timeout=8.0) as client:
        r = await client.get(f"{OLLAMA_BASE}/api/tags")
        r.raise_for_status()
        return r.json()


async def ollama_chat(
    model: str,
    prompt: str,
    images: Optional[list[str]] = None,
    num_predict: int = 1024,
) -> str:
    msg: dict[str, Any] = {"role": "user", "content": DIRECT_PREFIX + prompt}
    if images:
        msg["images"] = images
    body: dict[str, Any] = {
        "model": model,
        "messages": [msg],
        "stream": False,
        "options": {"num_predict": num_predict},
    }
    async with httpx.AsyncClient(timeout=600.0) as client:
        r = await client.post(f"{OLLAMA_BASE}/api/chat", json=body)
        r.raise_for_status()
        data = r.json()
        content = data.get("message", {}).get("content", "")
        return strip_thinking(content)


async def ollama_healthy() -> tuple[bool, list[str]]:
    try:
        tags = await ollama_tags()
        models = [m.get("name", "") for m in tags.get("models", [])]
        return True, models
    except Exception:
        return False, []
