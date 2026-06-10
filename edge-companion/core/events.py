from __future__ import annotations

import time
from collections import deque
from dataclasses import asdict, dataclass, field
from typing import Any

events: deque["Event"] = deque(maxlen=200)
last_phone_ping: float = 0.0
start_time = time.time()


@dataclass
class Event:
    ts: float
    kind: str
    message: str
    patient_name: str = ""
    entry_title: str = ""
    entry_type: str = ""
    image_preview_b64: str = ""
    output_preview: str = ""
    extra: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return asdict(self)


def log_event(
    kind: str,
    message: str,
    *,
    patient_name: str = "",
    entry_title: str = "",
    entry_type: str = "",
    image_preview_b64: str = "",
    output_preview: str = "",
    **extra: Any,
) -> None:
    events.append(
        Event(
            ts=time.time(),
            kind=kind,
            message=message,
            patient_name=patient_name,
            entry_title=entry_title,
            entry_type=entry_type,
            image_preview_b64=image_preview_b64[:120_000] if image_preview_b64 else "",
            output_preview=output_preview[:4000] if output_preview else "",
            extra=extra,
        )
    )
