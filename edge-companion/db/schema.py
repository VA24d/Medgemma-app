from __future__ import annotations

import sqlite3
from pathlib import Path

from core.config import DATA_DIR, DB_PATH, IMAGES_DIR

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS sync_revision (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    revision INTEGER NOT NULL DEFAULT 0
);
INSERT OR IGNORE INTO sync_revision (id, revision) VALUES (1, 0);

CREATE TABLE IF NOT EXISTS sync_meta (
    device_id TEXT PRIMARY KEY,
    last_cursor INTEGER NOT NULL DEFAULT 0,
    last_sync_at INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tombstones (
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    deleted_at INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE IF NOT EXISTS patients (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    date_of_birth TEXT NOT NULL DEFAULT '',
    gender TEXT NOT NULL DEFAULT '',
    medical_record_number TEXT NOT NULL DEFAULT '',
    phone_number TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    address TEXT NOT NULL DEFAULT '',
    blood_group TEXT NOT NULL DEFAULT '',
    allergies TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS medical_entries (
    id INTEGER PRIMARY KEY,
    patient_id INTEGER NOT NULL,
    entry_type TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    image_paths TEXT NOT NULL DEFAULT '',
    analysis_result TEXT NOT NULL DEFAULT '',
    visit_summary TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending',
    cloud_processed_at INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS diagnoses (
    id INTEGER PRIMARY KEY,
    patient_id INTEGER NOT NULL,
    diagnosis TEXT NOT NULL,
    generated_at INTEGER NOT NULL,
    scope TEXT NOT NULL DEFAULT 'FULL',
    entry_count INTEGER NOT NULL DEFAULT 0,
    model_name TEXT NOT NULL DEFAULT '',
    thinking_enabled INTEGER NOT NULL DEFAULT 0,
    revision INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS consultations (
    id INTEGER PRIMARY KEY,
    patient_id INTEGER NOT NULL,
    consultation_date INTEGER NOT NULL,
    chief_complaint TEXT NOT NULL,
    symptoms TEXT NOT NULL,
    vital_signs TEXT NOT NULL DEFAULT '',
    diagnosis TEXT NOT NULL DEFAULT '',
    prognosis TEXT NOT NULL DEFAULT '',
    ai_suggestions TEXT NOT NULL DEFAULT '',
    prescriptions TEXT NOT NULL DEFAULT '',
    follow_up_date INTEGER,
    notes TEXT NOT NULL DEFAULT '',
    voice_notes TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS entry_images (
    entry_id INTEGER NOT NULL,
    idx INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (entry_id, idx),
    FOREIGN KEY (entry_id) REFERENCES medical_entries(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""


def init_db() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    try:
        conn.executescript(SCHEMA_SQL)
        conn.commit()
    finally:
        conn.close()
