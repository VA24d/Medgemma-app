from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Optional

ProgressCb = Callable[[dict], None]


@dataclass
class JobState:
    running: bool = False
    phase: str = "idle"
    patient_name: str = ""
    current: int = 0
    total: int = 0
    entry_title: str = ""
    message: str = ""
    error: str = ""
    started_at: float = 0.0
    cancel_requested: bool = False

    def to_dict(self) -> dict:
        return {
            "running": self.running,
            "phase": self.phase,
            "patientName": self.patient_name,
            "current": self.current,
            "total": self.total,
            "entryTitle": self.entry_title,
            "message": self.message,
            "error": self.error,
            "startedAt": self.started_at,
        }


current_job = JobState()
_job_lock = asyncio.Lock()


async def run_exclusive(coro_factory: Callable[[], Any]) -> Any:
    global current_job
    async with _job_lock:
        if current_job.running:
            raise RuntimeError("Another processing job is already running")
        current_job = JobState(running=True, started_at=time.time())
        try:
            return await coro_factory()
        finally:
            current_job.running = False
            current_job.phase = "done"


def request_cancel() -> None:
    current_job.cancel_requested = True


def emit_progress(phase: str, message: str = "", **kwargs: Any) -> None:
    current_job.phase = phase
    current_job.message = message
    for k, v in kwargs.items():
        setattr(current_job, k if k != "patient_name" else "patient_name", v)
