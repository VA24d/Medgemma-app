from __future__ import annotations

import time
from typing import Any, Optional

from core.config import get_setting
from core.events import log_event
from core.ollama import ollama_chat, ollama_healthy, strip_thinking
from db import get_repo
from processor.jobs import current_job, emit_progress, request_cancel
from prompts.chart_prompts import (
    build_longitudinal_prompt,
    needs_text_processing,
    needs_vision_processing,
    text_entry_prompt,
    vision_prompt,
)


async def process_one_entry(
    patient_name: str,
    entry: dict,
    force: bool,
    model: str,
    image_b64: str = "",
) -> dict:
    now = int(time.time() * 1000)
    analysis = entry.get("analysisResult", "")
    summary = entry.get("visitSummary", "")

    if needs_vision_processing(entry, force):
        b64 = image_b64
        if b64:
            prompt = vision_prompt(entry.get("entryType", "XRAY"))
            analysis = strip_thinking(
                await ollama_chat(model, prompt, images=[b64], num_predict=1024)
            )
            sp = (
                f"Write exactly 1-2 short sentences summarizing this clinical entry for a chart timeline. "
                f"No bullets, no preamble.\n\nEntry type: {entry.get('entryType')}\nTitle: {entry.get('title')}\n\n{analysis}"
            )
            summary = strip_thinking(await ollama_chat(model, sp, num_predict=128)).split("\n")[0].strip()
    elif needs_text_processing(entry, force):
        prompt = text_entry_prompt(entry)
        analysis = strip_thinking(await ollama_chat(model, prompt, num_predict=1024))
        sp = (
            f"Write exactly 1-2 short sentences summarizing this clinical entry for a chart timeline. "
            f"No bullets, no preamble.\n\nEntry type: {entry.get('entryType')}\nTitle: {entry.get('title')}\n\n{analysis}"
        )
        summary = strip_thinking(await ollama_chat(model, sp, num_predict=128)).split("\n")[0].strip()
    elif not summary and analysis:
        prompt = f"One sentence chart headline for: {entry.get('title')}\n\n{analysis}"
        summary = strip_thinking(await ollama_chat(model, prompt, num_predict=128))
        if not summary:
            summary = analysis[:200]

    return {
        **entry,
        "analysisResult": analysis,
        "visitSummary": summary,
        "cloudProcessedAt": now,
        "status": "analyzed",
        "updatedAt": now,
    }


async def process_patient(patient_id: int, force: bool = False) -> dict:
    repo = get_repo()
    model = get_setting("default_model", "MedGemma1.5:latest")
    ok, models = await ollama_healthy()
    if not ok:
        raise RuntimeError("Ollama not running on laptop")
    if models and model not in models:
        raise RuntimeError(f"Model '{model}' not on server")

    patient = repo.get_patient(patient_id)
    if not patient:
        raise RuntimeError("Patient not found")

    entries = repo.list_entries(patient_id)
    if not entries:
        raise RuntimeError("No entries to process")

    to_process = [
        e for e in entries
        if force
        or needs_vision_processing(e, force)
        or needs_text_processing(e, force)
        or (not (e.get("visitSummary") or "").strip() and (e.get("analysisResult") or "").strip())
    ]
    if not to_process:
        to_process = entries

    emit_progress("entry", f"Processing {patient['name']}", patient_name=patient["name"], total=len(to_process))
    log_event("start_patient", f"Server processing {patient['name']}", patient_name=patient["name"])

    for idx, entry in enumerate(to_process, 1):
        if current_job.cancel_requested:
            raise RuntimeError("Cancelled")
        emit_progress(
            "entry",
            f"Processing {entry.get('entryType')}: {entry.get('title')}",
            patient_name=patient["name"],
            current=idx,
            total=len(to_process),
            entry_title=entry.get("title", ""),
        )
        b64 = repo.first_image_b64(entry)
        log_event(
            "start_entry",
            f"Processing {entry.get('entryType')}: {entry.get('title')}",
            patient_name=patient["name"],
            entry_title=entry.get("title", ""),
            entry_type=entry.get("entryType", ""),
            image_preview_b64=b64[:50000] if b64 else "",
        )
        updated = await process_one_entry(patient["name"], entry, force, model, b64)
        repo.upsert_entry(updated, source="server")
        log_event(
            "done_entry",
            "Done",
            patient_name=patient["name"],
            entry_title=entry.get("title", ""),
            output_preview=(updated.get("analysisResult") or "")[:500],
        )

    emit_progress("longitudinal", "Synthesizing longitudinal prognosis…", patient_name=patient["name"])
    refreshed = repo.list_entries(patient_id)
    prompt = build_longitudinal_prompt(patient, refreshed)
    log_event("start_longitudinal", f"Longitudinal: {patient['name']}", patient_name=patient["name"])
    diagnosis = strip_thinking(await ollama_chat(model, prompt, num_predict=1536))
    repo.upsert_diagnosis({
        "patientId": patient_id,
        "diagnosis": diagnosis,
        "generatedAt": int(time.time() * 1000),
        "scope": "CLOUD_FULL",
        "entryCount": len(refreshed),
        "modelName": f"Ollama:{model}",
        "thinkingEnabled": False,
    }, source="server")
    log_event("done_longitudinal", "Done", patient_name=patient["name"], output_preview=diagnosis[:500])
    emit_progress("done", f"Complete — {len(refreshed)} entries enriched", patient_name=patient["name"])
    return {"patientId": patient_id, "entries": len(refreshed)}


async def process_all(force: bool = False) -> dict:
    repo = get_repo()
    patients = repo.all_patients_with_entries()
    done = 0
    for p in patients:
        if current_job.cancel_requested:
            break
        emit_progress("patient", f"Starting patient: {p['name']}", patient_name=p["name"])
        await process_patient(p["id"], force=force)
        done += 1
    emit_progress("done", f"All patients processed ({done})")
    repo.set_job_meta("last_batch_run", str(int(time.time() * 1000)))
    return {"patientsProcessed": done}
