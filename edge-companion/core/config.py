from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent
CONFIG_PATH = ROOT / "config.json"
DATA_DIR = ROOT / "data"
DB_PATH = DATA_DIR / "companion.db"
IMAGES_DIR = DATA_DIR / "images"

DEFAULT_MODEL = "MedGemma1.5:latest"
HOST = "0.0.0.0"
PORT = 8787
OLLAMA_BASE = "http://127.0.0.1:11434"


def load_config() -> dict[str, Any]:
    if CONFIG_PATH.exists():
        with CONFIG_PATH.open(encoding="utf-8") as f:
            return json.load(f)
    return {}


def save_config(cfg: dict[str, Any]) -> None:
    with CONFIG_PATH.open("w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)


def get_setting(key: str, default: Any = None) -> Any:
    return load_config().get(key, default)


def set_setting(key: str, value: Any) -> dict[str, Any]:
    cfg = load_config()
    cfg[key] = value
    save_config(cfg)
    return cfg
