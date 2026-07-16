"""Axis 9 — Anti-fragility (反脆弱 / Reformed Just War).

Realized by: chaos engineering charter + transparent force registry +
demonstrated recovery from real failures. Observable: chaos charter, force-rd
package, Scenario rotation breadth.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, read_text


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    chaos = list(repo.glob("90-docs/*chaos*charter*.md"))
    if chaos:
        score += 3; ev.append(f"Chaos charter: {chaos[0].relative_to(repo)}")
        body = chaos[0].read_text(encoding="utf-8", errors="ignore")
        n_scenarios = body.count("## scenario") + body.count("## Scenario")
        if n_scenarios >= 10:
            score += 2; ev.append(f"≥10 chaos scenarios ({n_scenarios})")
    if has(repo, "60-apps/etzhayyim-transparent-force-rd"):
        score += 3; ev.append("Transparent Force R&D registry scaffolded")
    if has(repo, "50-infra/etzhayyim-force-authorization"):
        score += 2; ev.append("Force-authorization on-chain scaffold present")
    score = min(score, 10)

    nxt = "Execute Gen 1 Scenario 1 (network partition) at 2026-08-13" if score >= 9 \
        else "Scaffold chaos charter and transparent-force registry"
    return AxisReading(axis="antifragility", score=score, evidence=ev, next_action=nxt, leverage=2)
