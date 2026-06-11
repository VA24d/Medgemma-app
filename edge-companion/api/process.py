from __future__ import annotations

import asyncio

from fastapi import APIRouter, HTTPException

from processor.chart import process_all, process_patient
from processor.jobs import current_job, request_cancel, run_exclusive

router = APIRouter(prefix="/v1/process", tags=["process"])


async def _run_patient(pid: int, force: bool, backend: str) -> None:
    async def _inner():
        await process_patient(pid, force=force, backend=backend)

    await run_exclusive(_inner)


async def _run_all(force: bool, backend: str) -> None:
    async def _inner():
        await process_all(force=force, backend=backend)

    await run_exclusive(_inner)


@router.post("/patient/{patient_id}")
async def process_patient_route(patient_id: int, force: bool = False, backend: str = "") -> dict:
    if current_job.running:
        raise HTTPException(409, "Job already running")
    asyncio.create_task(_run_patient(patient_id, force, backend))
    return {"ok": True, "started": True, "patientId": patient_id}


@router.post("/all")
async def process_all_route(force: bool = False, backend: str = "") -> dict:
    if current_job.running:
        raise HTTPException(409, "Job already running")
    asyncio.create_task(_run_all(force, backend))
    return {"ok": True, "started": True}


@router.post("/cancel")
async def cancel_process() -> dict:
    request_cancel()
    return {"ok": True}


@router.get("/jobs/current")
async def job_current() -> dict:
    return current_job.to_dict()
