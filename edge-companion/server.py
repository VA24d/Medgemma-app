"""
Med Veda Edge Companion — local GPU API, sync hub, and web app.
"""
from __future__ import annotations

import asyncio
import json
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import FileResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles

from api import chat, entries, legacy, patients, process, settings, sync
from core import events
from core.config import DEFAULT_MODEL, HOST, PORT, get_setting
from core.ollama import lan_ip, ollama_healthy
from db.schema import init_db
from scheduler.night_batch import start_scheduler, startup_catchup

WEB_DIST = Path(__file__).resolve().parent / "web" / "dist"


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    start_scheduler()
    asyncio.create_task(startup_catchup())
    yield


app = FastAPI(title="Med Veda Edge Companion", version="2.0.0", lifespan=lifespan)

app.include_router(sync.router)
app.include_router(patients.router)
app.include_router(entries.router)
app.include_router(chat.router)
app.include_router(process.router)
app.include_router(settings.router)
app.include_router(legacy.router)


@app.get("/health")
async def health() -> dict:
    ollama_ok, models = await ollama_healthy()
    if not ollama_ok:
        events.log_event("error", "Ollama health fail")
    return {
        "status": "ok" if ollama_ok else "degraded",
        "ollama_ok": ollama_ok,
        "models": models,
        "default_model": get_setting("default_model", DEFAULT_MODEL),
        "uptime_sec": int(time.time() - events.start_time),
        "last_phone_ping_sec_ago": int(time.time() - events.last_phone_ping)
        if events.last_phone_ping
        else None,
        "lan_ip": lan_ip(),
        "companion_port": PORT,
        "phone_url_wifi": f"http://{lan_ip()}:{PORT}",
        "phone_url_usb": f"http://127.0.0.1:{PORT}",
        "sync_enabled": True,
        "night_batch_enabled": get_setting("night_batch_enabled", True),
    }


@app.get("/v1/events")
async def sse_events(request: Request) -> StreamingResponse:
    async def gen():
        last = 0
        while True:
            if await request.is_disconnected():
                break
            payload = [e.to_dict() for e in list(events.events)[last:]]
            if payload:
                last = len(events.events)
                yield f"data: {json.dumps(payload)}\n\n"
            else:
                yield f"data: {json.dumps([{'kind': 'heartbeat', 'ts': time.time()}])}\n\n"
            await asyncio.sleep(1)

    return StreamingResponse(gen(), media_type="text/event-stream")


@app.get("/v1/history")
async def history() -> dict:
    return {"events": [e.to_dict() for e in reversed(list(events.events))]}


# SPA static files — API routes registered above take precedence
if WEB_DIST.exists():
    assets_dir = WEB_DIST / "assets"
    if assets_dir.exists():
        app.mount("/assets", StaticFiles(directory=assets_dir), name="assets")

    @app.get("/{full_path:path}")
    async def spa_fallback(full_path: str):
        if full_path.startswith("v1/") or full_path == "health":
            return {"error": "not found"}
        index = WEB_DIST / "index.html"
        if index.exists():
            return FileResponse(index)
        return {"error": "web app not built — run npm run build in edge-companion/web"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
