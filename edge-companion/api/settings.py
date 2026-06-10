from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel

from core.config import get_setting, load_config, save_config, set_setting
from db import get_repo
from scheduler.night_batch import in_night_window, is_enabled, next_run_iso, reschedule, set_scheduler_enabled, status_message

router = APIRouter(prefix="/v1/settings", tags=["settings"])


class SettingsBody(BaseModel):
    default_model: str | None = None
    night_batch_enabled: bool | None = None
    night_start_hour: int | None = None
    night_start_minute: int | None = None
    night_end_hour: int | None = None
    night_end_minute: int | None = None
    night_skip_on_battery: bool | None = None
    auto_sync: bool | None = None


@router.get("")
async def get_settings() -> dict:
    cfg = load_config()
    repo = get_repo()
    return {
        **cfg,
        "cursor": repo.current_revision(),
        "lastNightBatch": repo.get_job_meta("last_night_batch"),
        "lastBatchRun": repo.get_job_meta("last_batch_run"),
        "nextNightRun": next_run_iso(),
        "nightBatchInWindow": in_night_window(),
        "nightBatchStatus": status_message(),
    }


@router.put("")
async def update_settings(body: SettingsBody) -> dict:
    cfg = load_config()
    for k, v in body.model_dump(exclude_none=True).items():
        cfg[k] = v
    save_config(cfg)
    if body.night_batch_enabled is not None:
        set_scheduler_enabled(body.night_batch_enabled)
    reschedule()
    return await get_settings()
