from __future__ import annotations

import time
from copy import deepcopy
from typing import Any, Optional


def _ts(d: dict, key: str = "updatedAt") -> int:
    return int(d.get(key) or 0)


def merge_patient(existing: Optional[dict], incoming: dict, source: str) -> dict:
    if existing is None:
        out = deepcopy(incoming)
        now = int(time.time() * 1000)
        if not out.get("createdAt"):
            out["createdAt"] = now
        if not out.get("updatedAt"):
            out["updatedAt"] = now
        return out
    if _ts(incoming) >= _ts(existing):
        return deepcopy(incoming)
    return deepcopy(existing)


def merge_entry(existing: Optional[dict], incoming: dict, source: str) -> dict:
    if existing is None:
        out = deepcopy(incoming)
        now = int(time.time() * 1000)
        if not out.get("createdAt"):
            out["createdAt"] = now
        if not out.get("updatedAt"):
            out["updatedAt"] = now
        return out

    out = deepcopy(existing)
    crud_incoming = _ts(incoming)
    crud_existing = _ts(existing)
    ai_in = int(incoming.get("cloudProcessedAt") or 0)
    ai_ex = int(existing.get("cloudProcessedAt") or 0)

    if crud_incoming >= crud_existing:
        for k in ("patientId", "entryType", "title", "content", "imagePaths", "status", "createdAt", "updatedAt"):
            if k in incoming:
                out[k] = incoming[k]

    if ai_in >= ai_ex:
        for k in ("analysisResult", "visitSummary", "cloudProcessedAt", "status"):
            if k in incoming and incoming.get(k):
                out[k] = incoming[k]
    elif ai_ex > ai_in:
        out["analysisResult"] = existing.get("analysisResult", "")
        out["visitSummary"] = existing.get("visitSummary", "")
        out["cloudProcessedAt"] = ai_ex

    out["updatedAt"] = max(crud_incoming, crud_existing, ai_in, ai_ex)
    return out


def merge_diagnosis(existing: Optional[dict], incoming: dict, source: str) -> dict:
    if existing is None:
        return deepcopy(incoming)
    if int(incoming.get("generatedAt") or 0) >= int(existing.get("generatedAt") or 0):
        return deepcopy(incoming)
    return deepcopy(existing)
