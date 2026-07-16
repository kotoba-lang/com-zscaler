"""Axis 8 — Wellbecoming (動的軌跡 / 子・孫 priority).

Realized by: dynamic trajectory across generations. Observable: MGI compute
script, LANDS.md (inalienable inheritance), MEMBERS.md (multi-generation roster).
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, count_glob


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    if has(repo, "LANDS.md"):
        score += 2; ev.append("LANDS.md present (inalienable inheritance roster)")
    if has(repo, "MEMBERS.md"):
        score += 2; ev.append("MEMBERS.md present (multi-generation member roster)")
    mgi_scripts = count_glob(repo, "**/*mgi*") + count_glob(repo, "**/*MGI*")
    if mgi_scripts >= 1:
        score += 2; ev.append(f"MGI artefact(s) present ({mgi_scripts})")
    if has(repo, "_observations/mgi"):
        score += 2; ev.append("_observations/mgi/ tracking directory present")
    # Multi-generation references in CHARTER-RIDER.md
    if "Gen" in (repo / "_observations").read_text(encoding="utf-8", errors="ignore") if (repo / "_observations").is_file() else False:
        score += 1
    # 子孫 / multi-generation tokens anywhere in CLAUDE.md
    txt = (repo / "CLAUDE.md").read_text(encoding="utf-8", errors="ignore") if has(repo, "CLAUDE.md") else ""
    if "子・孫" in txt or "multi-generation" in txt.lower():
        score += 2; ev.append("CLAUDE.md affirms multi-generational priority")
    score = min(score, 10)

    nxt = "First operative MGI report 2027-02-09" if score >= 9 \
        else "Author MGI compute script"
    return AxisReading(axis="wellbecoming", score=score, evidence=ev, next_action=nxt, leverage=2)
