"""Axis 6 — Symbiosis (共生 / Tree of Life branches).

Realized by: multi-substrate roots are present and reachable. Observable:
did:web Worker + IPFS pinner + L2 contract + MST projector + Base anchor cron.
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has


SUBSTRATES: tuple[tuple[str, str], ...] = (
    ("did:web Worker",     "50-infra/etzhayyim-did-web"),
    ("MST projector",      "50-infra/mst-projector"),
    ("IPFS pinner",        "50-infra/ipfs-pinner"),
    ("L2 anchor contract", "50-infra/l2-anchor-contract"),
    ("Anchor cron",        "50-infra/anchor-cron"),
    ("geth-private",       "50-infra/geth-private"),
    ("Holochain",          "50-infra/holochain"),
)


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    for label, p in SUBSTRATES:
        if has(repo, p):
            score += 1
            ev.append(f"{label} scaffolded")
    score = min(score, 10)
    nxt = "Establish ≥1 substrate pair operating in production" if score < 9 \
        else "Verify cross-substrate anchoring liveness"
    return AxisReading(axis="symbiosis", score=score, evidence=ev, next_action=nxt, leverage=2)
