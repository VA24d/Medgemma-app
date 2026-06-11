from __future__ import annotations

from typing import Optional

from core.config import DEFAULT_MODEL, get_setting
from core.gemini import gemini_chat, gemini_configured
from core.ollama import ollama_chat, ollama_healthy, strip_thinking

VALID_BACKENDS = ("ollama", "gemini")


def resolve_backend(requested: str | None, default_key: str) -> str:
    if requested and requested in VALID_BACKENDS:
        return requested
    cfg = get_setting(default_key, "ollama")
    return cfg if cfg in VALID_BACKENDS else "ollama"


async def llm_chat(
    backend: str,
    model: str,
    prompt: str,
    images: Optional[list[str]] = None,
    num_predict: int = 1024,
) -> str:
    if backend == "gemini":
        if not gemini_configured():
            raise RuntimeError("Gemini API key not configured on server (.env)")
        gemini_model = get_setting("gemini_model", "gemini-2.5-flash")
        return await gemini_chat(gemini_model, prompt, images=images, num_predict=num_predict)
    resolved = model or get_setting("default_model", DEFAULT_MODEL)
    text = await ollama_chat(resolved, prompt, images=images, num_predict=num_predict)
    return strip_thinking(text)


async def backend_ready(backend: str) -> tuple[bool, str]:
    if backend == "gemini":
        if gemini_configured():
            return True, ""
        return False, "Gemini API key not configured"
    ok, _ = await ollama_healthy()
    if ok:
        return True, ""
    return False, "Ollama not running on laptop"
