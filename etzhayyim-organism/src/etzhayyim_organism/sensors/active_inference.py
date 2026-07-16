"""Axis 4 — Active Inference (能動推論 / 縁起).

Realized by: persisted observations grow, trajectory-stats works, stall detection
emits ADRs. Observable: _observations/*-cycle-NN.md count + monotonicity.
"""

from __future__ import annotations

import re
from pathlib import Path

from .common import AxisReading, has, count_glob


_CYCLE = re.compile(r"-cycle-(\d+)\.md$")


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    cycles = sorted(repo.glob("_observations/*-cycle-*.md"))
    n = len(cycles)
    if n >= 1:
        score += 2; ev.append(f"{n} observation cycle(s) persisted")
    if n >= 5:
        score += 2; ev.append("≥5 cycles — short-run trajectory established")
    if n >= 15:
        score += 2; ev.append("≥15 cycles — long-run trajectory")

    # Monotone numbering (no gaps)?
    nums = []
    for p in cycles:
        m = _CYCLE.search(p.name)
        if m:
            nums.append(int(m.group(1)))
    gaps = [b - a for a, b in zip(nums, nums[1:]) if b - a != 1]
    if nums and not gaps:
        score += 2; ev.append(f"Cycle numbering monotone (cycles 1..{nums[-1]})")
    elif nums:
        ev.append(f"Cycle numbering has {len(gaps)} gap(s)")

    if has(repo, "70-tools/scripts/loop/trajectory-stats.sh"):
        score += 2; ev.append("trajectory-stats.sh harness live")

    score = min(score, 10)
    nxt = "Emit ADR template when 3× Δ=0 (stall detection)" if score >= 9 \
        else "Persist next cycle observation"
    return AxisReading(axis="active_inference", score=score, evidence=ev, next_action=nxt, leverage=1)
