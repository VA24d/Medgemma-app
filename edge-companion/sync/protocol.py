from __future__ import annotations

from typing import Any

from db.repository import Repository


def apply_push(repo: Repository, payload: dict, device_id: str, source: str = "phone") -> dict:
    for p in payload.get("patients", []):
        repo.upsert_patient(p, source=source)
    for e in payload.get("entries", []):
        repo.upsert_entry(e, source=source)
    for d in payload.get("diagnoses", []):
        repo.upsert_diagnosis(d, source=source)
    for c in payload.get("consultations", []):
        repo.upsert_consultation(c)
    for img in payload.get("images", []):
        eid = img.get("entryId") or img.get("entry_id")
        idx = img.get("index", 0)
        b64 = img.get("base64", "")
        if eid and b64:
            repo.save_entry_image(int(eid), int(idx), b64)
    for t in payload.get("tombstones", []):
        et = t.get("entityType") or t.get("entity_type")
        eid = t.get("entityId") or t.get("entity_id")
        if et == "patient" and eid:
            repo.delete_patient(int(eid))
        elif et == "entry" and eid:
            repo.delete_entry(int(eid))
    cursor = repo.current_revision()
    repo.update_device_sync(device_id, cursor)
    return {"ok": True, "cursor": cursor}


def apply_full_bootstrap(repo: Repository, payload: dict, device_id: str) -> dict:
    return apply_push(repo, payload, device_id, source="phone")
