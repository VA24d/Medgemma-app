from __future__ import annotations

import base64
import sqlite3
import time
from pathlib import Path
from typing import Any, Optional

from core.config import DB_PATH, IMAGES_DIR
from sync.merge import merge_diagnosis, merge_entry, merge_patient


def _conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def _bump_revision(conn: sqlite3.Connection) -> int:
    conn.execute("UPDATE sync_revision SET revision = revision + 1 WHERE id = 1")
    row = conn.execute("SELECT revision FROM sync_revision WHERE id = 1").fetchone()
    return int(row["revision"])


class Repository:
    def bump_revision(self) -> int:
        with _conn() as conn:
            rev = _bump_revision(conn)
            conn.commit()
            return rev

    def current_revision(self) -> int:
        with _conn() as conn:
            row = conn.execute("SELECT revision FROM sync_revision WHERE id = 1").fetchone()
            return int(row["revision"])

    def next_id(self, table: str) -> int:
        with _conn() as conn:
            row = conn.execute(f"SELECT COALESCE(MAX(id), 0) + 1 AS n FROM {table}").fetchone()
            return int(row["n"])

    # ── Patients ──

    def list_patients(self, search: str = "") -> list[dict]:
        with _conn() as conn:
            if search.strip():
                q = f"%{search.strip()}%"
                rows = conn.execute(
                    """SELECT * FROM patients WHERE name LIKE ? OR medical_record_number LIKE ?
                       OR phone_number LIKE ? OR allergies LIKE ? ORDER BY name""",
                    (q, q, q, q),
                ).fetchall()
            else:
                rows = conn.execute("SELECT * FROM patients ORDER BY name").fetchall()
            return [self._patient_row(r) for r in rows]

    def get_patient(self, pid: int) -> Optional[dict]:
        with _conn() as conn:
            row = conn.execute("SELECT * FROM patients WHERE id = ?", (pid,)).fetchone()
            return self._patient_row(row) if row else None

    def upsert_patient(self, data: dict, source: str = "web") -> dict:
        with _conn() as conn:
            existing = None
            if data.get("id"):
                row = conn.execute("SELECT * FROM patients WHERE id = ?", (data["id"],)).fetchone()
                if row:
                    existing = self._patient_row(row)
            merged = merge_patient(existing, data, source)
            rev = _bump_revision(conn)
            merged["revision"] = rev
            conn.execute(
                """INSERT INTO patients (id, name, date_of_birth, gender, medical_record_number,
                   phone_number, email, address, blood_group, allergies, notes, created_at, updated_at, revision)
                   VALUES (:id, :name, :dateOfBirth, :gender, :medicalRecordNumber, :phoneNumber, :email,
                   :address, :bloodGroup, :allergies, :notes, :createdAt, :updatedAt, :revision)
                   ON CONFLICT(id) DO UPDATE SET
                   name=excluded.name, date_of_birth=excluded.date_of_birth, gender=excluded.gender,
                   medical_record_number=excluded.medical_record_number, phone_number=excluded.phone_number,
                   email=excluded.email, address=excluded.address, blood_group=excluded.blood_group,
                   allergies=excluded.allergies, notes=excluded.notes, updated_at=excluded.updated_at,
                   revision=excluded.revision""",
                self._patient_to_sql(merged),
            )
            conn.commit()
            return merged

    def delete_patient(self, pid: int) -> None:
        with _conn() as conn:
            rev = _bump_revision(conn)
            now = int(time.time() * 1000)
            conn.execute(
                "INSERT OR REPLACE INTO tombstones (entity_type, entity_id, deleted_at, revision) VALUES (?,?,?,?)",
                ("patient", pid, now, rev),
            )
            conn.execute("DELETE FROM patients WHERE id = ?", (pid,))
            conn.commit()

    # ── Entries ──

    def list_entries(self, patient_id: int) -> list[dict]:
        with _conn() as conn:
            rows = conn.execute(
                "SELECT * FROM medical_entries WHERE patient_id = ? ORDER BY created_at",
                (patient_id,),
            ).fetchall()
            return [self._entry_row(r) for r in rows]

    def get_entry(self, eid: int) -> Optional[dict]:
        with _conn() as conn:
            row = conn.execute("SELECT * FROM medical_entries WHERE id = ?", (eid,)).fetchone()
            return self._entry_row(row) if row else None

    def upsert_entry(self, data: dict, source: str = "web") -> dict:
        with _conn() as conn:
            existing = None
            if data.get("id"):
                row = conn.execute("SELECT * FROM medical_entries WHERE id = ?", (data["id"],)).fetchone()
                if row:
                    existing = self._entry_row(row)
            merged = merge_entry(existing, data, source)
            rev = _bump_revision(conn)
            merged["revision"] = rev
            conn.execute(
                """INSERT INTO medical_entries (id, patient_id, entry_type, title, content, image_paths,
                   analysis_result, visit_summary, status, cloud_processed_at, created_at, updated_at, revision)
                   VALUES (:id, :patientId, :entryType, :title, :content, :imagePaths, :analysisResult,
                   :visitSummary, :status, :cloudProcessedAt, :createdAt, :updatedAt, :revision)
                   ON CONFLICT(id) DO UPDATE SET
                   patient_id=excluded.patient_id, entry_type=excluded.entry_type, title=excluded.title,
                   content=excluded.content, image_paths=excluded.image_paths,
                   analysis_result=excluded.analysis_result, visit_summary=excluded.visit_summary,
                   status=excluded.status, cloud_processed_at=excluded.cloud_processed_at,
                   updated_at=excluded.updated_at, revision=excluded.revision""",
                self._entry_to_sql(merged),
            )
            conn.commit()
            return merged

    def delete_entry(self, eid: int) -> None:
        with _conn() as conn:
            rev = _bump_revision(conn)
            now = int(time.time() * 1000)
            conn.execute(
                "INSERT OR REPLACE INTO tombstones (entity_type, entity_id, deleted_at, revision) VALUES (?,?,?,?)",
                ("entry", eid, now, rev),
            )
            conn.execute("DELETE FROM medical_entries WHERE id = ?", (eid,))
            conn.commit()

    def save_entry_image(self, entry_id: int, idx: int, b64: str) -> str:
        raw = base64.b64decode(b64)
        path = IMAGES_DIR / f"{entry_id}_{idx}.jpg"
        path.write_bytes(raw)
        with _conn() as conn:
            rev = _bump_revision(conn)
            conn.execute(
                """INSERT INTO entry_images (entry_id, idx, file_path, revision) VALUES (?,?,?,?)
                   ON CONFLICT(entry_id, idx) DO UPDATE SET file_path=excluded.file_path, revision=excluded.revision""",
                (entry_id, idx, str(path), rev),
            )
            conn.commit()
        return str(path)

    def load_entry_image_b64(self, entry_id: int, idx: int = 0) -> str:
        with _conn() as conn:
            row = conn.execute(
                "SELECT file_path FROM entry_images WHERE entry_id = ? AND idx = ?",
                (entry_id, idx),
            ).fetchone()
        if not row:
            return ""
        p = Path(row["file_path"])
        if not p.exists():
            return ""
        return base64.b64encode(p.read_bytes()).decode("ascii")

    def first_image_b64(self, entry: dict) -> str:
        eid = entry.get("id", 0)
        if eid:
            b64 = self.load_entry_image_b64(eid, 0)
            if b64:
                return b64
        return ""

    # ── Diagnoses ──

    def list_diagnoses(self, patient_id: int) -> list[dict]:
        with _conn() as conn:
            rows = conn.execute(
                "SELECT * FROM diagnoses WHERE patient_id = ? ORDER BY generated_at DESC",
                (patient_id,),
            ).fetchall()
            return [self._diagnosis_row(r) for r in rows]

    def upsert_diagnosis(self, data: dict, source: str = "web") -> dict:
        with _conn() as conn:
            existing = None
            if data.get("id"):
                row = conn.execute("SELECT * FROM diagnoses WHERE id = ?", (data["id"],)).fetchone()
                if row:
                    existing = self._diagnosis_row(row)
            merged = merge_diagnosis(existing, data, source)
            rev = _bump_revision(conn)
            merged["revision"] = rev
            if not merged.get("id"):
                merged["id"] = self.next_id("diagnoses")
            conn.execute(
                """INSERT INTO diagnoses (id, patient_id, diagnosis, generated_at, scope, entry_count,
                   model_name, thinking_enabled, revision)
                   VALUES (:id, :patientId, :diagnosis, :generatedAt, :scope, :entryCount,
                   :modelName, :thinkingEnabled, :revision)
                   ON CONFLICT(id) DO UPDATE SET
                   diagnosis=excluded.diagnosis, generated_at=excluded.generated_at, scope=excluded.scope,
                   entry_count=excluded.entry_count, model_name=excluded.model_name,
                   thinking_enabled=excluded.thinking_enabled, revision=excluded.revision""",
                self._diagnosis_to_sql(merged),
            )
            conn.commit()
            return merged

    # ── Consultations ──

    def upsert_consultation(self, data: dict) -> dict:
        with _conn() as conn:
            rev = _bump_revision(conn)
            if not data.get("id"):
                data["id"] = self.next_id("consultations")
            data["revision"] = rev
            conn.execute(
                """INSERT INTO consultations (id, patient_id, consultation_date, chief_complaint, symptoms,
                   vital_signs, diagnosis, prognosis, ai_suggestions, prescriptions, follow_up_date, notes,
                   voice_notes, created_at, updated_at, revision)
                   VALUES (:id, :patientId, :consultationDate, :chiefComplaint, :symptoms, :vitalSigns,
                   :diagnosis, :prognosis, :aiSuggestions, :prescriptions, :followUpDate, :notes,
                   :voiceNotes, :createdAt, :updatedAt, :revision)
                   ON CONFLICT(id) DO UPDATE SET
                   patient_id=excluded.patient_id, consultation_date=excluded.consultation_date,
                   chief_complaint=excluded.chief_complaint, symptoms=excluded.symptoms,
                   vital_signs=excluded.vital_signs, diagnosis=excluded.diagnosis,
                   prognosis=excluded.prognosis, ai_suggestions=excluded.ai_suggestions,
                   prescriptions=excluded.prescriptions, follow_up_date=excluded.follow_up_date,
                   notes=excluded.notes, voice_notes=excluded.voice_notes,
                   updated_at=excluded.updated_at, revision=excluded.revision""",
                self._consultation_to_sql(data),
            )
            conn.commit()
            return data

    # ── Sync pull ──

    def pull_since(self, since: int) -> dict:
        with _conn() as conn:
            patients = [
                self._patient_row(r)
                for r in conn.execute("SELECT * FROM patients WHERE revision > ?", (since,)).fetchall()
            ]
            entries = [
                self._entry_row(r)
                for r in conn.execute("SELECT * FROM medical_entries WHERE revision > ?", (since,)).fetchall()
            ]
            diagnoses = [
                self._diagnosis_row(r)
                for r in conn.execute("SELECT * FROM diagnoses WHERE revision > ?", (since,)).fetchall()
            ]
            consultations = [
                self._consultation_to_api(self._consultation_row(r))
                for r in conn.execute("SELECT * FROM consultations WHERE revision > ?", (since,)).fetchall()
            ]
            tombstones = [
                {"entityType": r["entity_type"], "entityId": r["entity_id"], "deletedAt": r["deleted_at"]}
                for r in conn.execute("SELECT * FROM tombstones WHERE revision > ?", (since,)).fetchall()
            ]
            images = []
            for r in conn.execute("SELECT * FROM entry_images WHERE revision > ?", (since,)).fetchall():
                p = Path(r["file_path"])
                if p.exists():
                    images.append({
                        "entryId": r["entry_id"],
                        "index": r["idx"],
                        "base64": base64.b64encode(p.read_bytes()).decode("ascii"),
                    })
            rev = self.current_revision()
        return {
            "cursor": rev,
            "patients": patients,
            "entries": entries,
            "diagnoses": diagnoses,
            "consultations": consultations,
            "tombstones": tombstones,
            "images": images,
        }

    def update_device_sync(self, device_id: str, cursor: int) -> None:
        with _conn() as conn:
            now = int(time.time() * 1000)
            conn.execute(
                """INSERT INTO sync_meta (device_id, last_cursor, last_sync_at) VALUES (?,?,?)
                   ON CONFLICT(device_id) DO UPDATE SET last_cursor=excluded.last_cursor, last_sync_at=excluded.last_sync_at""",
                (device_id, cursor, now),
            )
            conn.commit()

    def get_job_meta(self, key: str, default: str = "") -> str:
        with _conn() as conn:
            row = conn.execute("SELECT value FROM job_meta WHERE key = ?", (key,)).fetchone()
            return row["value"] if row else default

    def set_job_meta(self, key: str, value: str) -> None:
        with _conn() as conn:
            conn.execute(
                "INSERT INTO job_meta (key, value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (key, value),
            )
            conn.commit()

    def entry_count(self, patient_id: int) -> int:
        with _conn() as conn:
            row = conn.execute(
                "SELECT COUNT(*) AS c FROM medical_entries WHERE patient_id = ?", (patient_id,)
            ).fetchone()
            return int(row["c"])

    def all_patients_with_entries(self) -> list[dict]:
        with _conn() as conn:
            rows = conn.execute(
                """SELECT p.* FROM patients p
                   WHERE EXISTS (SELECT 1 FROM medical_entries e WHERE e.patient_id = p.id)
                   ORDER BY p.name"""
            ).fetchall()
            return [self._patient_row(r) for r in rows]

    # ── Row mappers ──

    @staticmethod
    def _patient_row(r: sqlite3.Row) -> dict:
        return {
            "id": r["id"],
            "name": r["name"],
            "dateOfBirth": r["date_of_birth"],
            "gender": r["gender"],
            "medicalRecordNumber": r["medical_record_number"],
            "phoneNumber": r["phone_number"],
            "email": r["email"],
            "address": r["address"],
            "bloodGroup": r["blood_group"],
            "allergies": r["allergies"],
            "notes": r["notes"],
            "createdAt": r["created_at"],
            "updatedAt": r["updated_at"],
            "revision": r["revision"],
        }

    @staticmethod
    def _patient_to_sql(d: dict) -> dict:
        return {
            "id": d.get("id") or 0,
            "name": d.get("name", ""),
            "dateOfBirth": d.get("dateOfBirth", ""),
            "gender": d.get("gender", ""),
            "medicalRecordNumber": d.get("medicalRecordNumber", ""),
            "phoneNumber": d.get("phoneNumber", ""),
            "email": d.get("email", ""),
            "address": d.get("address", ""),
            "bloodGroup": d.get("bloodGroup", ""),
            "allergies": d.get("allergies", ""),
            "notes": d.get("notes", ""),
            "createdAt": d.get("createdAt", int(time.time() * 1000)),
            "updatedAt": d.get("updatedAt", int(time.time() * 1000)),
            "revision": d.get("revision", 0),
        }

    @staticmethod
    def _entry_row(r: sqlite3.Row) -> dict:
        return {
            "id": r["id"],
            "patientId": r["patient_id"],
            "entryType": r["entry_type"],
            "title": r["title"],
            "content": r["content"],
            "imagePaths": r["image_paths"],
            "analysisResult": r["analysis_result"],
            "visitSummary": r["visit_summary"],
            "status": r["status"],
            "cloudProcessedAt": r["cloud_processed_at"],
            "createdAt": r["created_at"],
            "updatedAt": r["updated_at"],
            "revision": r["revision"],
        }

    @staticmethod
    def _entry_to_sql(d: dict) -> dict:
        return {
            "id": d.get("id") or 0,
            "patientId": d["patientId"],
            "entryType": d.get("entryType", "MANUAL"),
            "title": d.get("title", ""),
            "content": d.get("content", ""),
            "imagePaths": d.get("imagePaths", ""),
            "analysisResult": d.get("analysisResult", ""),
            "visitSummary": d.get("visitSummary", ""),
            "status": d.get("status", "pending"),
            "cloudProcessedAt": d.get("cloudProcessedAt", 0),
            "createdAt": d.get("createdAt", int(time.time() * 1000)),
            "updatedAt": d.get("updatedAt", int(time.time() * 1000)),
            "revision": d.get("revision", 0),
        }

    @staticmethod
    def _diagnosis_row(r: sqlite3.Row) -> dict:
        return {
            "id": r["id"],
            "patientId": r["patient_id"],
            "diagnosis": r["diagnosis"],
            "generatedAt": r["generated_at"],
            "scope": r["scope"],
            "entryCount": r["entry_count"],
            "modelName": r["model_name"],
            "thinkingEnabled": bool(r["thinking_enabled"]),
            "revision": r["revision"],
        }

    @staticmethod
    def _diagnosis_to_sql(d: dict) -> dict:
        return {
            "id": d.get("id") or 0,
            "patientId": d["patientId"],
            "diagnosis": d.get("diagnosis", ""),
            "generatedAt": d.get("generatedAt", int(time.time() * 1000)),
            "scope": d.get("scope", "FULL"),
            "entryCount": d.get("entryCount", 0),
            "modelName": d.get("modelName", ""),
            "thinkingEnabled": 1 if d.get("thinkingEnabled") else 0,
            "revision": d.get("revision", 0),
        }

    @staticmethod
    def _consultation_row(r: sqlite3.Row) -> dict:
        return {
            "id": r["id"],
            "patientId": r["patient_id"],
            "consultationDate": r["consultation_date"],
            "chiefComplaint": r["chief_complaint"],
            "symptoms": r["symptoms"],
            "vitalSigns": r["vital_signs"],
            "diagnosis": r["diagnosis"],
            "prognosis": r["prognosis"],
            "aiSuggestions": r["ai_suggestions"],
            "prescriptions": r["prescriptions"],
            "followUpDate": r["follow_up_date"],
            "notes": r["notes"],
            "voiceNotes": r["voice_notes"],
            "createdAt": r["created_at"],
            "updatedAt": r["updated_at"],
            "revision": r["revision"],
        }

    @staticmethod
    def _consultation_to_sql(d: dict) -> dict:
        return {
            "id": d.get("id") or 0,
            "patientId": d["patientId"],
            "consultationDate": d.get("consultationDate", int(time.time() * 1000)),
            "chiefComplaint": d.get("chiefComplaint", ""),
            "symptoms": d.get("symptoms", ""),
            "vitalSigns": d.get("vitalSigns", ""),
            "diagnosis": d.get("diagnosis", ""),
            "prognosis": d.get("prognosis", ""),
            "aiSuggestions": d.get("aiSuggestions", ""),
            "prescriptions": d.get("prescriptions", ""),
            "followUpDate": d.get("followUpDate"),
            "notes": d.get("notes", ""),
            "voiceNotes": d.get("voiceNotes", ""),
            "createdAt": d.get("createdAt", int(time.time() * 1000)),
            "updatedAt": d.get("updatedAt", int(time.time() * 1000)),
            "revision": d.get("revision", 0),
        }

    @staticmethod
    def _consultation_to_api(d: dict) -> dict:
        return d
