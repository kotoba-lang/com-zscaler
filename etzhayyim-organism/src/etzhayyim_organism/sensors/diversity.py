"""Axis 7 — Diversity (多様性 / 八百万-kami).

Realized by: variation in cells, apps, protocols. Observable: count of
distinct cell directories, app directories, and protocol packages.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, count_glob


def read(repo: Path) -> AxisReading:
    cells = count_glob(repo, "40-engine/kotoba/crates/kotoba-kotodama/cells/*/")
    apps = count_glob(repo, "60-apps/*/")
    proto = count_glob(repo, "10-protocol/*/")
    infra = count_glob(repo, "50-infra/*/")

    score = 0
    ev: list[str] = []
    if cells >= 10:
        score += 3; ev.append(f"{cells} kotodama cells (八百万 variation)")
    elif cells >= 5:
        score += 2; ev.append(f"{cells} kotodama cells")
    if apps >= 10:
        score += 3; ev.append(f"{apps} apps")
    elif apps >= 1:
        score += 1; ev.append(f"{apps} apps")
    if proto >= 5:
        score += 2; ev.append(f"{proto} protocol packages")
    if infra >= 15:
        score += 2; ev.append(f"{infra} infra components")
    score = min(score, 10)

    nxt = "Exercise idle yorishiro_* cells end-to-end" if score >= 9 \
        else "Add at least one more cell / app / protocol package"
    return AxisReading(axis="diversity", score=score, evidence=ev, next_action=nxt, leverage=1)
