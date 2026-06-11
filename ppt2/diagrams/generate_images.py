#!/usr/bin/env python3
"""Export Mermaid diagrams from architecture.md to PNG via mermaid.ink."""

from __future__ import annotations

import base64
import re
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ARCH_MD = HERE / "architecture.md"
OUT_DIR = HERE / "png"


def extract_mermaid_blocks(md_text: str) -> list[tuple[str, str]]:
    """Return [(slug, mermaid_source), ...] in document order."""
    pattern = re.compile(
        r"## Diagram (\d+) —[^\n]*\n\n(?:[^\n]*\n\n)?```mermaid\n(.*?)```",
        re.DOTALL,
    )
    blocks: list[tuple[str, str]] = []
    for num, body in pattern.findall(md_text):
        first_line = body.strip().split("\n")[0]
        if "flowchart" in first_line or "sequenceDiagram" in first_line:
            slug = f"diagram_{num}"
            blocks.append((slug, body.strip()))
    return blocks


def render_png(mermaid: str, out_path: Path) -> None:
    encoded = base64.urlsafe_b64encode(mermaid.encode("utf-8")).decode("utf-8")
    url = f"https://mermaid.ink/img/{encoded}?type=png&bgColor=white&width=1400"
    req = urllib.request.Request(url, headers={"User-Agent": "MedVeda-diagram-export/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        out_path.write_bytes(resp.read())


def main() -> int:
    if not ARCH_MD.exists():
        print(f"Missing {ARCH_MD}", file=sys.stderr)
        return 1

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    md = ARCH_MD.read_text(encoding="utf-8")
    blocks = extract_mermaid_blocks(md)
    if not blocks:
        print("No mermaid blocks found.", file=sys.stderr)
        return 1

    for slug, source in blocks:
        out = OUT_DIR / f"{slug}.png"
        print(f"Rendering {slug} -> {out.name} ...")
        try:
            render_png(source, out)
            print(f"  OK ({out.stat().st_size // 1024} KB)")
        except Exception as e:
            print(f"  FAILED: {e}", file=sys.stderr)
            return 1

    print(f"\nDone. {len(blocks)} PNGs in {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
