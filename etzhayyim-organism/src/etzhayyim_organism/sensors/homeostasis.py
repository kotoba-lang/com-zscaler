"""Axis 3 — Homeostasis (恒常性 / 和).

Realized by: substrate boundary holds (no RisingWave/Postgres/Stripe in app code;
Apache 2.0 + Charter Rider on first-party packages; lefthook lints prohibitions).
"""

from __future__ import annotations

from pathlib import Path

from .common import AxisReading, has, count_glob, read_text


PROHIBITED_IMPORTS = (
    "from @atproto/api",
    "import { Stripe",
    "kysely",
    "@noble/ciphers",
    "@signalapp/libsignal-client",
)


def read(repo: Path) -> AxisReading:
    score = 0
    ev: list[str] = []
    if has(repo, "CHARTER-RIDER.md"):
        score += 2; ev.append("CHARTER-RIDER.md present at root")
    lefthook = read_text(repo, "lefthook.yml")
    n_hooks = lefthook.count("\n  - ")
    if n_hooks >= 4:
        score += 2; ev.append(f"lefthook.yml has ≥4 lint hooks ({n_hooks} observed)")
    elif "lefthook.yml" and lefthook:
        score += 1; ev.append(f"lefthook.yml present ({n_hooks} hooks)")

    notice_files = count_glob(repo, "**/NOTICE")
    if notice_files >= 20:
        score += 2; ev.append(f"NOTICE files propagated ({notice_files} found)")
    elif notice_files >= 1:
        score += 1; ev.append(f"NOTICE files present ({notice_files} found)")

    # ADR registry health
    adr_count = count_glob(repo, "90-docs/adr/*.md")
    if adr_count >= 30:
        score += 2; ev.append(f"ADR registry healthy ({adr_count} ADRs)")
    elif adr_count >= 10:
        score += 1; ev.append(f"ADR registry growing ({adr_count} ADRs)")

    if has(repo, "deps.toml"):
        score += 2; ev.append("deps.toml SSoT present")
    score = min(score, 10)

    nxt = "Council attestation gate on religious-corp identity PRs" if score >= 9 \
        else "Restore lefthook + NOTICE + CHARTER-RIDER scaffolding"
    return AxisReading(axis="homeostasis", score=score, evidence=ev, next_action=nxt, leverage=2)
