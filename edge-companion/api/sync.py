from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel, Field

from db import get_repo
from sync.protocol import apply_full_bootstrap, apply_push

router = APIRouter(prefix="/v1/sync", tags=["sync"])


class SyncPushRequest(BaseModel):
    device_id: str = "unknown"
    patients: list[dict] = Field(default_factory=list)
    entries: list[dict] = Field(default_factory=list)
    diagnoses: list[dict] = Field(default_factory=list)
    consultations: list[dict] = Field(default_factory=list)
    images: list[dict] = Field(default_factory=list)
    tombstones: list[dict] = Field(default_factory=list)


@router.post("/push")
async def sync_push(req: SyncPushRequest) -> dict:
    repo = get_repo()
    return apply_push(repo, req.model_dump(), req.device_id, source=req.device_id)


@router.get("/pull")
async def sync_pull(since: int = 0, device_id: str = "unknown") -> dict:
    repo = get_repo()
    data = repo.pull_since(since)
    repo.update_device_sync(device_id, data["cursor"])
    return data


@router.post("/full")
async def sync_full(req: SyncPushRequest) -> dict:
    repo = get_repo()
    return apply_full_bootstrap(repo, req.model_dump(), req.device_id)
