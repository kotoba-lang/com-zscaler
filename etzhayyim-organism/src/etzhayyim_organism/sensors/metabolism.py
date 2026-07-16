"""Axis 2 — Metabolism (代謝 / 産霊 musuhi).

Realized by: donation inflow → 10% tithe → public-fund redistribution loop is
deployable. Observable: TitheRouter / PublicFund / ChartersCompliance code +
deployment scripts. Score caps until Base Sepolia deploy is observed on-chain.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, count_glob


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    tithe = "50-infra/etzhayyim-tithe-router"
    fund  = "50-infra/etzhayyim-public-fund"
    comp  = "50-infra/etzhayyim-charters-compliance"
    if has(repo, tithe):
        score += 2; ev.append(f"{tithe}/ scaffolded")
    if has(repo, fund):
        score += 2; ev.append(f"{fund}/ scaffolded")
    if has(repo, comp):
        score += 1; ev.append(f"{comp}/ scaffolded")

    # Constitution test coverage (110/110 tests claimed in CLAUDE.md)
    constitution_sol = list(repo.glob("50-infra/**/Constitution.sol"))
    if constitution_sol:
        score += 1; ev.append(f"Constitution.sol present ({len(constitution_sol)} location(s))")

    # On-chain anchor — testnet/mainnet not yet deployed per CLAUDE.md
    if any((repo / p / "broadcast").exists() for p in [tithe, fund, comp]):
        score += 4; ev.append("On-chain Foundry broadcast present (testnet/mainnet deploy observed)")
    score = min(score, 10)

    nxt = "Deploy TitheRouter to Base Sepolia (post-Council)" if score < 9 \
        else "Confirm first observed tithe routing tx"
    return AxisReading(axis="metabolism", score=score, evidence=ev, next_action=nxt, leverage=3)
