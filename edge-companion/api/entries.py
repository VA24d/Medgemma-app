from __future__ import annotations

import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from db import get_repo

router = APIRouter(prefix="/v1", tags=["entries"])


class EntryBody(BaseModel):
    id: int | None = None
    patientId: int
    entryType: str = "MANUAL"
    title: str = ""
    content: str = ""
    imagePaths: str = ""
    analysisResult: str = ""
    visitSummary: str = ""
    status: str = "pending"
    cloudProcessedAt: int = 0


class ImageUpload(BaseModel):
    index: int = 0
    base64: str


@router.post("/patients/{patient_id}/entries")
async def create_entry(patient_id: int, body: EntryBody) -> dict:
    repo = get_repo()
    if not repo.get_patient(patient_id):
        raise HTTPException(404, "Patient not found")
    now = int(time.time() * 1000)
    data = body.model_dump()
    data["patientId"] = patient_id
    data["id"] = repo.next_id("medical_entries")
    data["createdAt"] = now
    data["updatedAt"] = now
    return {"entry": repo.upsert_entry(data, source="web")}


@router.put("/entries/{entry_id}")
async def update_entry(entry_id: int, body: EntryBody) -> dict:
    repo = get_repo()
    existing = repo.get_entry(entry_id)
    if not existing:
        raise HTTPException(404, "Entry not found")
    data = body.model_dump()
    data["id"] = entry_id
    data["createdAt"] = existing["createdAt"]
    data["updatedAt"] = int(time.time() * 1000)
    return {"entry": repo.upsert_entry(data, source="web")}


@router.delete("/entries/{entry_id}")
async def delete_entry(entry_id: int) -> dict:
    repo = get_repo()
    repo.delete_entry(entry_id)
    return {"ok": True}


@router.post("/entries/{entry_id}/images")
async def upload_image(entry_id: int, body: ImageUpload) -> dict:
    repo = get_repo()
    if not repo.get_entry(entry_id):
        raise HTTPException(404, "Entry not found")
    path = repo.save_entry_image(entry_id, body.index, body.base64)
    entry = repo.get_entry(entry_id)
    if entry:
        paths = [p for p in (entry.get("imagePaths") or "").split(",") if p]
        while len(paths) <= body.index:
            paths.append("")
        paths[body.index] = path
        entry["imagePaths"] = ",".join(paths)
        entry["updatedAt"] = int(time.time() * 1000)
        repo.upsert_entry(entry, source="web")
    return {"ok": True, "path": path}


@router.get("/entries/{entry_id}/images/{index}")
async def get_image(entry_id: int, index: int) -> dict:
    repo = get_repo()
    b64 = repo.load_entry_image_b64(entry_id, index)
    if not b64:
        raise HTTPException(404, "Image not found")
    return {"base64": b64}
