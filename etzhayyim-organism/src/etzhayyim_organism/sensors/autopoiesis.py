"""Axis 1 — Autopoiesis (自己創出 / 無教会 万人祭司).

Realized by: organisation can reproduce itself without hierarchical clergy.
Observable: CLAUDE.md + bootstrap docs + Council scaffolding + harness scripts.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, count_glob


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    if has(repo, "CLAUDE.md"):
        score += 2; ev.append("CLAUDE.md present (operator memory)")
    if has(repo, "COUNCIL.md") and has(repo, "COUNCIL-BOOTSTRAP-RFP.md"):
        score += 2; ev.append("Council scaffolded (5-seat religious evaluation body)")
    if has(repo, "MEMBERS.md"):
        score += 1; ev.append("MEMBERS.md roster present")
    if has(repo, "FORK-BOOTSTRAP.md"):
        score += 1; ev.append("FORK-BOOTSTRAP.md present (八百万 propagation enabled)")
    loop_scripts = count_glob(repo, "70-tools/scripts/loop/*.sh")
    if loop_scripts >= 1:
        score += 2; ev.append(f"{loop_scripts} loop script(s) (self-rescoring harness)")
    if has(repo, "_observations") and count_glob(repo, "_observations/*-cycle-*.md") >= 3:
        score += 2; ev.append("≥3 persisted cycles (autopoiesis demonstrated longitudinally)")
    score = min(score, 10)

    nxt = "Confirm Council Seats 2-5 by 2026-06-19 (RFP close)" if score >= 9 \
        else "Verify CLAUDE.md + Council + observations scaffold integrity"

    return AxisReading(axis="autopoiesis", score=score, evidence=ev, next_action=nxt, leverage=2)
