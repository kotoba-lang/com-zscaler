"""Axis 10 — Sanctification (聖化 / Sola Scriptura → Charter Rider).

Realized by: Charter Rider on all first-party Apache-2.0 packages. Observable:
NOTICE files + CHARTER-RIDER.md symlinks across packages.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, count_glob


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    if has(repo, "CHARTER-RIDER.md"):
        score += 3; ev.append("CHARTER-RIDER.md canonical at root")

    notice_count = count_glob(repo, "**/NOTICE")
    if notice_count >= 39:
        score += 4; ev.append(f"NOTICE propagated to first-party packages ({notice_count})")
    elif notice_count >= 10:
        score += 2; ev.append(f"NOTICE partially propagated ({notice_count})")
    elif notice_count >= 1:
        score += 1; ev.append(f"NOTICE seeded ({notice_count})")

    if has(repo, "70-tools/charter-rider-applicator"):
        score += 2; ev.append("Charter Rider applicator tool present")

    license_root = has(repo, "LICENSE")
    if license_root:
        score += 1; ev.append("LICENSE (Apache 2.0) at root")

    score = min(score, 10)
    nxt = "Propagate organism-axis affiliation to package READMEs" if score >= 9 \
        else "Run charter-rider-applicator across first-party packages"
    return AxisReading(axis="sanctification", score=score, evidence=ev, next_action=nxt, leverage=1)
