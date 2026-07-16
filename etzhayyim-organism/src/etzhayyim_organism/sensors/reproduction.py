"""Axis 5 — Reproduction (生殖 / 八百万 propagation).

Realized by: fork-bootstrap path is documented AND at least one sister-corp
fork exists. Observable: FORK-BOOTSTRAP.md + sister-corp registrations.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, read_text


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    fb = "FORK-BOOTSTRAP.md"
    if has(repo, fb):
        score += 3; ev.append(f"{fb} present")
        body = read_text(repo, fb)
        if "did:web:" in body:
            score += 1; ev.append("Fork bootstrap mentions did:web (identity-rotated forks)")
        if len(body) > 2000:
            score += 1; ev.append("Fork bootstrap is substantive (>2000 chars)")

    # Sister-corp registry — not yet populated, would live here
    if has(repo, "SISTER-CORPS.md"):
        score += 4; ev.append("SISTER-CORPS.md present (first observed sister-corp registration)")

    score = min(score, 10)
    nxt = "Author first SISTER-CORPS.md registration template" if score < 9 \
        else "Maintain sister-corp registry"
    return AxisReading(axis="reproduction", score=score, evidence=ev, next_action=nxt, leverage=3)
