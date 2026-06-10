from __future__ import annotations

import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from db import get_repo

router = APIRouter(prefix="/v1/patients", tags=["patients"])


class PatientBody(BaseModel):
    id: int | None = None
    name: str
    dateOfBirth: str = ""
    gender: str = ""
    medicalRecordNumber: str = ""
    phoneNumber: str = ""
    email: str = ""
    address: str = ""
    bloodGroup: str = ""
    allergies: str = ""
    notes: str = ""


@router.get("")
async def list_patients(q: str = "") -> dict:
    repo = get_repo()
    patients = repo.list_patients(q)
    return {"patients": patients}


@router.get("/{patient_id}")
async def get_patient(patient_id: int) -> dict:
    repo = get_repo()
    p = repo.get_patient(patient_id)
    if not p:
        raise HTTPException(404, "Patient not found")
    entries = repo.list_entries(patient_id)
    diagnoses = repo.list_diagnoses(patient_id)
    return {"patient": p, "entries": entries, "diagnoses": diagnoses}


@router.post("")
async def create_patient(body: PatientBody) -> dict:
    repo = get_repo()
    now = int(time.time() * 1000)
    data = body.model_dump()
    data["id"] = repo.next_id("patients")
    data["createdAt"] = now
    data["updatedAt"] = now
    return {"patient": repo.upsert_patient(data, source="web")}


@router.put("/{patient_id}")
async def update_patient(patient_id: int, body: PatientBody) -> dict:
    repo = get_repo()
    existing = repo.get_patient(patient_id)
    if not existing:
        raise HTTPException(404, "Patient not found")
    data = body.model_dump()
    data["id"] = patient_id
    data["createdAt"] = existing["createdAt"]
    data["updatedAt"] = int(time.time() * 1000)
    return {"patient": repo.upsert_patient(data, source="web")}


@router.delete("/{patient_id}")
async def delete_patient(patient_id: int) -> dict:
    repo = get_repo()
    repo.delete_patient(patient_id)
    return {"ok": True}
