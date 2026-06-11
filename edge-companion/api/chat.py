from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from core.config import get_setting
from core.llm_router import llm_chat, resolve_backend
from db import get_repo
from prompts.chat_prompts import build_patient_system_prompt, maybe_instant_reply, wants_longitudinal_question

router = APIRouter(prefix="/v1/chat", tags=["chat"])


class ChatRequest(BaseModel):
    patient_id: int | None = None
    message: str
    images: list[str] = Field(default_factory=list)
    model: str = ""
    backend: str = ""


@router.post("")
async def chat(req: ChatRequest) -> dict:
    model = req.model or get_setting("default_model", "MedGemma1.5:latest")
    backend = resolve_backend(req.backend or None, "chat_backend")
    if not req.message.strip():
        raise HTTPException(400, "Empty message")

    if req.patient_id is None or req.patient_id <= 0:
        reply = await llm_chat(backend, model, req.message, images=req.images or None, num_predict=512)
        return {"reply": reply, "instant": False, "backend": backend}

    repo = get_repo()
    patient = repo.get_patient(req.patient_id)
    if not patient:
        raise HTTPException(404, "Patient not found")
    entries = repo.list_entries(req.patient_id)
    diagnoses = repo.list_diagnoses(req.patient_id)

    instant = maybe_instant_reply(patient, len(entries), req.message)
    if instant:
        return {"reply": instant, "instant": True}

    system = build_patient_system_prompt(patient, entries, diagnoses)
    num_predict = 896 if wants_longitudinal_question(req.message) else 512
    prompt = f"{system}\n\nCurrent request: {req.message}"
    reply = await llm_chat(backend, model, prompt, images=req.images or None, num_predict=num_predict)
    return {"reply": reply, "instant": False, "backend": backend}
