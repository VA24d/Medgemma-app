from __future__ import annotations

import asyncio
import base64
import os
from typing import Optional

from core.config import REPO_ROOT, ROOT, get_setting

_client = None

DIRECT_PREFIX = (
    "Answer directly for clinical documentation. "
    "No chain-of-thought, planning, or meta commentary.\n\n"
)


def _load_dotenv() -> None:
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    for path in (REPO_ROOT / ".env", ROOT / ".env"):
        if path.exists():
            load_dotenv(path)
            return


def gemini_api_key() -> str:
    _load_dotenv()
    return os.getenv("GEMINI_API_KEY", "").strip()


def gemini_configured() -> bool:
    return bool(gemini_api_key())


def _get_client():
    global _client
    key = gemini_api_key()
    if not key:
        raise RuntimeError("GEMINI_API_KEY not configured in .env")
    if _client is None:
        from google import genai

        _client = genai.Client(api_key=key)
    return _client


def _sync_chat(
    model: str,
    prompt: str,
    images: Optional[list[str]] = None,
    num_predict: int = 1024,
) -> str:
    from google.genai import types

    client = _get_client()
    parts: list = [types.Part.from_text(text=DIRECT_PREFIX + prompt)]
    if images:
        for b64 in images:
            if not b64:
                continue
            raw = base64.b64decode(b64)
            parts.append(types.Part.from_bytes(data=raw, mime_type="image/jpeg"))

    response = client.models.generate_content(
        model=model,
        contents=types.Content(role="user", parts=parts),
        config=types.GenerateContentConfig(max_output_tokens=num_predict),
    )
    return (response.text or "").strip()


async def gemini_chat(
    model: str,
    prompt: str,
    images: Optional[list[str]] = None,
    num_predict: int = 1024,
) -> str:
    resolved = model or get_setting("gemini_model", "gemini-2.5-flash")
    return await asyncio.to_thread(_sync_chat, resolved, prompt, images, num_predict)
