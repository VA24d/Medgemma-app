from __future__ import annotations

import asyncio
import logging
from datetime import datetime
from typing import Optional

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

from core.config import get_setting, load_config
from core.events import log_event
from core.ollama import ollama_healthy
from db import get_repo
from processor.chart import process_all
from processor.jobs import current_job, run_exclusive

logger = logging.getLogger("night_batch")
_scheduler: Optional[AsyncIOScheduler] = None


def is_enabled() -> bool:
    return bool(get_setting("night_batch_enabled", True))


def _on_battery() -> bool:
    if not get_setting("night_skip_on_battery", True):
        return False
    try:
        import ctypes

        class SYSTEM_POWER_STATUS(ctypes.Structure):
            _fields_ = [
                ("ACLineStatus", ctypes.c_byte),
                ("BatteryFlag", ctypes.c_byte),
                ("BatteryLifePercent", ctypes.c_byte),
                ("SystemStatusFlag", ctypes.c_byte),
                ("BatteryLifeTime", ctypes.c_ulong),
                ("BatteryFullLifeTime", ctypes.c_ulong),
            ]

        status = SYSTEM_POWER_STATUS()
        if ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status)):
            return status.ACLineStatus == 0
    except Exception:
        pass
    return False


def _minutes_since_midnight(dt: datetime) -> int:
    return dt.hour * 60 + dt.minute


def in_night_window(now: Optional[datetime] = None) -> bool:
    """True when local time is inside configured night window (e.g. 00:00–02:00)."""
    now = now or datetime.now().astimezone()
    cfg = load_config()
    start = int(cfg.get("night_start_hour", 0)) * 60 + int(cfg.get("night_start_minute", 0))
    end = int(cfg.get("night_end_hour", 2)) * 60 + int(cfg.get("night_end_minute", 0))
    cur = _minutes_since_midnight(now)
    if start <= end:
        return start <= cur < end
    # wraps midnight, e.g. 22:00–02:00
    return cur >= start or cur < end


def _ran_today() -> bool:
    last = get_repo().get_job_meta("last_night_batch", "")
    if not last:
        return False
    try:
        last_ms = int(last)
        last_dt = datetime.fromtimestamp(last_ms / 1000).astimezone()
        today = datetime.now().astimezone().date()
        return last_dt.date() == today
    except ValueError:
        return False


async def _night_job(*, reason: str = "scheduled") -> None:
    if not is_enabled():
        log_event("night_batch", "Skipped — disabled in settings")
        return
    if current_job.running:
        logger.info("Night batch skipped — job already running")
        return
    if _on_battery():
        log_event("night_batch", "Skipped — laptop on battery (plug in for night batch)")
        return
    ok, _ = await ollama_healthy()
    if not ok:
        log_event("night_batch", "Skipped — Ollama not running (start Ollama + companion)")
        return

    log_event("night_batch", f"Starting nightly GPU batch ({reason})")
    try:
        async def _run():
            return await process_all(force=False)

        result = await run_exclusive(_run)
        get_repo().set_job_meta("last_night_batch", str(int(datetime.now().timestamp() * 1000)))
        log_event("night_batch", f"Complete — {result.get('patientsProcessed', 0)} patients")
    except Exception as e:
        logger.exception("Night batch failed")
        log_event("error", f"Night batch: {e}")


def set_scheduler_enabled(enabled: bool) -> None:
    global _scheduler
    if _scheduler is None:
        return
    job = _scheduler.get_job("night_batch")
    if not job:
        return
    if enabled:
        _scheduler.resume_job("night_batch")
        logger.info("Night batch scheduler resumed")
    else:
        _scheduler.pause_job("night_batch")
        logger.info("Night batch scheduler paused")


async def startup_catchup() -> None:
    """
    If companion starts during the night window and batch has not run today,
    run once. Night batch only works while laptop is on and start.ps1 is running.
    """
    if not is_enabled():
        return
    if not in_night_window():
        return
    if _ran_today():
        logger.info("Night catch-up skipped — already ran today")
        return
    log_event("night_batch", "Catch-up: companion started during night window")
    await _night_job(reason="startup catch-up")


def start_scheduler() -> AsyncIOScheduler:
    global _scheduler
    cfg = load_config()
    hour = int(cfg.get("night_start_hour", 0))
    minute = int(cfg.get("night_start_minute", 0))
    enabled = is_enabled()
    _scheduler = AsyncIOScheduler()
    _scheduler.add_job(
        _night_job,
        CronTrigger(hour=hour, minute=minute),
        id="night_batch",
        replace_existing=True,
    )
    _scheduler.start()
    if not enabled:
        _scheduler.pause_job("night_batch")
    logger.info(
        "Night batch %s at %02d:%02d (requires laptop on + companion running)",
        "scheduled" if enabled else "paused",
        hour,
        minute,
    )
    return _scheduler


def reschedule() -> None:
    global _scheduler
    if _scheduler is None:
        return
    cfg = load_config()
    hour = int(cfg.get("night_start_hour", 0))
    minute = int(cfg.get("night_start_minute", 0))
    enabled = is_enabled()
    _scheduler.reschedule_job("night_batch", trigger=CronTrigger(hour=hour, minute=minute))
    set_scheduler_enabled(enabled)


def next_run_iso() -> Optional[str]:
    if not is_enabled():
        return None
    if _scheduler is None:
        return None
    job = _scheduler.get_job("night_batch")
    if not job or not job.next_run_time:
        return None
    return job.next_run_time.isoformat()


def status_message() -> str:
    if not is_enabled():
        return "Off — enable in Settings to schedule nightly GPU processing"
    if _scheduler is None:
        return "Scheduler not started"
    nxt = next_run_iso()
    window = (
        f"{int(get_setting('night_start_hour', 0)):02d}:"
        f"{int(get_setting('night_start_minute', 0)):02d}–"
        f"{int(get_setting('night_end_hour', 2)):02d}:"
        f"{int(get_setting('night_end_minute', 0)):02d}"
    )
    in_win = "in window now" if in_night_window() else "outside window"
    return (
        f"On ({window} local). Runs only while laptop is awake and companion is running. "
        f"Next: {nxt or '—'}. {in_win}."
    )
