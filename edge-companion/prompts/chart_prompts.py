from __future__ import annotations

from datetime import datetime
from typing import Any

IMAGING_TYPES = {"XRAY", "HISTOPATHOLOGY", "MRI"}

PATIENT_NOTES_MAX = 900
ENTRY_FIELD_MAX = 3500
DIAG_IN_PROMPT_MAX = 650


def vision_prompt(entry_type: str) -> str:
    if entry_type == "HISTOPATHOLOGY":
        return """You are a pathology AI. Systematically describe this histopathology slide for clinical documentation.
Report: 1) Stain type, 2) Tissue architecture, 3) Cellular morphology, 4) Mitotic figures, 5) Inflammatory infiltrate, 6) Vascular/stromal changes, 7) Dysplasia or malignancy with location.
Be precise and clinically useful. No code blocks."""
    if entry_type == "MRI":
        return """You are a radiologist AI specialising in MRI. Describe this MRI for clinical documentation.
Report: sequence/plane, region, signal characteristics, focal lesions, mass effect, enhancement, incidental findings.
Be precise. No code blocks."""
    return """You are a radiologist AI. Systematically describe this X-ray for clinical documentation.
Report: 1) Orientation and quality, 2) Bony structures, 3) Soft tissue, 4) Lung fields, 5) Cardiac silhouette, 6) Mediastinum, 7) Pleural spaces, 8) Abnormal findings with location.
Be precise. No code blocks."""


def text_entry_prompt(entry: dict) -> str:
    labels = {
        "RECORDING": "voice recording / transcription",
        "MANUAL": "clinical note",
        "DOCUMENT": "medical document",
    }
    type_label = labels.get(entry.get("entryType", ""), entry.get("entryType", "").lower())
    return f"""You are a specialist AI medical assistant. Analyse this {type_label} entry for chart documentation.
Title: {entry.get('title', '')}
Content: {entry.get('content', '')}

Provide:
1) Key clinical findings
2) Clinical significance
3) Recommended follow-up if any

Be concise. No code blocks."""


def effective_visit_line(entry: dict) -> str:
    v = (entry.get("visitSummary") or "").strip()
    if v:
        return v
    notes = (entry.get("content") or "").strip().replace("\n", " ")
    if len(notes) > 280:
        return notes[:277] + "…"
    if notes:
        return notes
    ai = (entry.get("analysisResult") or "").strip()
    if ai:
        suffix = "…" if len(ai) > 220 else ""
        return f"AI/imaging note: {ai[:220]}{suffix}"
    return "(No summary line — see full chart.)"


def needs_vision_processing(entry: dict, force: bool) -> bool:
    if entry.get("entryType") not in IMAGING_TYPES:
        return False
    paths = entry.get("imagePaths", "")
    if not paths and not entry.get("id"):
        return False
    if force:
        return True
    return not (entry.get("analysisResult") or "").strip() or int(entry.get("cloudProcessedAt") or 0) == 0


def needs_text_processing(entry: dict, force: bool) -> bool:
    if entry.get("entryType") in IMAGING_TYPES:
        return False
    if force:
        return True
    return not (entry.get("visitSummary") or "").strip() or int(entry.get("cloudProcessedAt") or 0) == 0


def build_longitudinal_prompt(patient: dict, entries: list[dict]) -> str:
    timeline_parts = []
    for e in sorted(entries, key=lambda x: x.get("createdAt", 0)):
        dt = datetime.fromtimestamp(e.get("createdAt", 0) / 1000).strftime("%Y-%m-%d")
        line = effective_visit_line(e)
        ai = ""
        ar = e.get("analysisResult") or ""
        if ar:
            ai = f"\n  Imaging/AI: {ar[:800]}"
        timeline_parts.append(
            f"[{dt}][{e.get('entryType', '')}] {e.get('title', '')}\n  → {line}{ai}"
        )
    timeline = "\n".join(timeline_parts)
    allergies = patient.get("allergies") or "None"
    return f"""You are a specialist AI medical assistant. Generate a concise clinical prognosis from this enriched chart.

PATIENT: {patient.get('name', '')}
DOB: {patient.get('dateOfBirth', '')}  Gender: {patient.get('gender', '')}
Allergies: {allergies if allergies else 'None'}

ENTRIES ({len(entries)}, oldest→newest):
{timeline}

Provide in Markdown:
1. **Summary of findings**
2. **Diagnosis / differentials** (with confidence)
3. **Disease progression** (improving / stable / deteriorating)
4. **Recommended next steps**
5. **Red flags** to monitor

Be clinically precise. No chain-of-thought."""


def fmt_date(ms: int) -> str:
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d")
