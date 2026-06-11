from __future__ import annotations

import time

from fastapi import APIRouter
from pydantic import BaseModel

from core.config import DEFAULT_MODEL
from core.events import log_event
from core.llm_router import llm_chat, resolve_backend
from core.ollama import strip_thinking

router = APIRouter(tags=["legacy"])


class ProcessEntryRequest(BaseModel):
    model: str = DEFAULT_MODEL
    patient_name: str = ""
    entry_id: int = 0
    entry_type: str = ""
    title: str = ""
    content: str = ""
    prompt: str
    image_base64: str = ""
    num_predict: int = 1024
    backend: str = ""


class ProcessEntryResponse(BaseModel):
    analysis_result: str
    visit_summary: str
    duration_ms: int


class LongitudinalRequest(BaseModel):
    model: str = DEFAULT_MODEL
    patient_name: str = ""
    prompt: str
    num_predict: int = 1536
    backend: str = ""


class LongitudinalResponse(BaseModel):
    diagnosis: str
    duration_ms: int


class PingRequest(BaseModel):
    device_label: str = "android"


@router.post("/v1/ping")
async def phone_ping(req: PingRequest) -> dict:
    import core.events as ev

    ev.last_phone_ping = time.time()
    log_event("ping", f"Phone connected ({req.device_label})")
    return {"ok": True}


@router.post("/v1/process/entry", response_model=ProcessEntryResponse)
async def process_entry(req: ProcessEntryRequest) -> ProcessEntryResponse:
    backend = resolve_backend(req.backend or None, "batch_backend")
    t0 = time.time()
    log_event(
        "start_entry",
        f"Processing {req.entry_type}: {req.title}",
        patient_name=req.patient_name,
        entry_title=req.title,
        entry_type=req.entry_type,
        image_preview_b64=req.image_base64[:50000] if req.image_base64 else "",
    )
    images = [req.image_base64] if req.image_base64 else None
    analysis = await llm_chat(backend, req.model, req.prompt, images=images, num_predict=req.num_predict)
    summary_prompt = (
        f"Write exactly 1-2 short sentences summarizing this clinical entry for a chart timeline. "
        f"No bullets, no preamble.\n\nEntry type: {req.entry_type}\nTitle: {req.title}\n\n{analysis}"
    )
    visit_summary = await llm_chat(backend, req.model, summary_prompt, num_predict=128)
    visit_summary = strip_thinking(visit_summary).split("\n")[0].strip()
    ms = int((time.time() - t0) * 1000)
    log_event(
        "done_entry",
        f"Done ({ms}ms)",
        patient_name=req.patient_name,
        entry_title=req.title,
        entry_type=req.entry_type,
        output_preview=analysis[:500],
    )
    return ProcessEntryResponse(analysis_result=analysis, visit_summary=visit_summary, duration_ms=ms)


@router.post("/v1/process/longitudinal", response_model=LongitudinalResponse)
async def process_longitudinal(req: LongitudinalRequest) -> LongitudinalResponse:
    backend = resolve_backend(req.backend or None, "batch_backend")
    t0 = time.time()
    log_event("start_longitudinal", f"Longitudinal synthesis: {req.patient_name}", patient_name=req.patient_name)
    diagnosis = await llm_chat(backend, req.model, req.prompt, num_predict=req.num_predict)
    ms = int((time.time() - t0) * 1000)
    log_event("done_longitudinal", f"Done ({ms}ms)", patient_name=req.patient_name, output_preview=diagnosis[:500])
    return LongitudinalResponse(diagnosis=diagnosis, duration_ms=ms)
