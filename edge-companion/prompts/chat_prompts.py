from __future__ import annotations

import re
from typing import Any, Optional

from prompts.chart_prompts import DIAG_IN_PROMPT_MAX, ENTRY_FIELD_MAX, PATIENT_NOTES_MAX, effective_visit_line

LONGITUDINAL_PHRASES = [
    "progress", "summary", "summarize", "timeline", "longitudinal", "trajectory",
    "clinical course", "disease course", "course of illness",
    "overall picture", "overall summary",
    "how is the patient", "how's the patient", "hows the patient",
    "visit by visit", "interval change", "compared to prior", "before and after",
    "getting better", "getting worse", "improving", "deteriorat",
]

THINK_RE = re.compile(r"<unused94>thought>[\s\S]*?<unused95>")


def wants_longitudinal_question(msg: str) -> bool:
    n = msg.lower()
    return any(p in n for p in LONGITUDINAL_PHRASES)


def strip_thinking(text: str) -> str:
    out = THINK_RE.sub("", text)
    return out.replace("<unused94>", "").replace("<unused95>", "").replace("thought>", "").strip()


def build_patient_system_prompt(patient: dict, entries: list[dict], diagnoses: list[dict]) -> str:
    from prompts.chart_prompts import fmt_date

    patient_info_lines = [patient.get("name", "")]
    if patient.get("dateOfBirth"):
        patient_info_lines.append(f"DOB {patient['dateOfBirth']}")
    if patient.get("gender"):
        patient_info_lines.append(f"Gender {patient['gender']}")
    if patient.get("bloodGroup"):
        patient_info_lines.append(f"Blood {patient['bloodGroup']}")
    if patient.get("allergies"):
        patient_info_lines.append(f"Allergies {patient['allergies']}")
    if patient.get("notes"):
        n = patient["notes"]
        cap = PATIENT_NOTES_MAX
        patient_info_lines.append(f"Notes {n[:cap]}{'…' if len(n) > cap else ''}")
    patient_info = "\n".join(patient_info_lines)

    if diagnoses:
        diag_lines = []
        for d in diagnoses[:3]:
            clean = strip_thinking(d.get("diagnosis", ""))
            dt = fmt_date(d.get("generatedAt", 0))
            diag_lines.append(
                f"{dt} {d.get('scope', '')}: {clean[:DIAG_IN_PROMPT_MAX]}{'…' if len(clean) > DIAG_IN_PROMPT_MAX else ''}"
            )
        diag_summary = "\n".join(diag_lines)
    else:
        diag_summary = "None."

    if entries:
        blocks = []
        for e in sorted(entries, key=lambda x: x.get("createdAt", 0)):
            ai = ""
            if e.get("analysisResult"):
                ai = f"\nImaging/AI: {e['analysisResult'][:ENTRY_FIELD_MAX]}"
            headline = f"\nHeadline: {e['visitSummary']}\n" if e.get("visitSummary") else ""
            dt = fmt_date(e.get("createdAt", 0))
            content = (e.get("content") or "")[:ENTRY_FIELD_MAX]
            blocks.append(f"{dt} [{e.get('entryType', '')}] {e.get('title', '')}{headline}{content}{ai}")
        full_entry_block = "\n\n---\n\n".join(blocks)
    else:
        full_entry_block = "(No entries.)"

    return f"""Clinical assistant. Chart below is the only source; no filler intros. Markdown OK.

For progress/course/timeline: write a thorough narrative (multiple paragraphs if needed), cite dates and entry types in prose—avoid a separate boilerplate section titled "Each visit" unless the user explicitly asks for visit-by-visit bullets.

PATIENT
{patient_info}

ENTRIES ({len(entries)}, oldest→newest)
{full_entry_block}

SAVED IMPRESSIONS (may be incomplete vs entries)
{diag_summary}"""


def maybe_instant_reply(patient: dict, entry_count: int, user_message: str) -> Optional[str]:
    n = user_message.lower().strip()
    if len(n) > 160:
        return None

    needs_reasoning = [
        "progress", "summary", "diagnosis", "diagnose", "analyze", "analysis", "compare", "trend",
        "prognosis", "recommend", "should ", "x-ray", "xray", "image", "finding", "interpret",
        "worse", "better", "improving", "treatment plan", "what happened", "timeline", "course",
        "condition", "symptom", "why ", "how did", "explain",
    ]
    if any(x in n for x in needs_reasoning):
        return None

    if re.search(r"\b(how many entries|how many notes|number of entries|entry count|how many visits)\b", n):
        return f"### Chart snapshot\n- **Medical entries on file:** {entry_count}"

    identity = re.search(
        r"\b(who is|who's|what patient|which patient|patient name|identify the patient|patient id|"
        r"patient details?|demographics|tell me about (this )?patient|describe (this )?patient|"
        r"who am i looking at|what patient is this)\b",
        n,
    )
    if not identity:
        return None

    lines = ["### Patient profile *(from records)*", f"- **Name:** {patient.get('name', '')}"]
    for label, key in [
        ("MRN", "medicalRecordNumber"), ("DOB", "dateOfBirth"), ("Gender", "gender"),
        ("Blood group", "bloodGroup"), ("Phone", "phoneNumber"), ("Email", "email"),
        ("Address", "address"), ("Allergies", "allergies"), ("Notes", "notes"),
    ]:
        v = patient.get(key, "")
        if v:
            lines.append(f"- **{label}:** {v}")
    lines.append("")
    lines.append("*For imaging detail, ask a focused question.*")
    return "\n".join(lines)
