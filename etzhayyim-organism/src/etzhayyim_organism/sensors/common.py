"""Shared types and helpers for sensors."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path


@dataclass
class AxisReading:
    """One axis observation at tick time."""

    axis: str
    score: int       # 0..10
    evidence: list[str] = field(default_factory=list)
    next_action: str = ""
    leverage: int = 1  # 1..3 — how much a single action could move this axis


def count_glob(repo: Path, pattern: str) -> int:
    return sum(1 for _ in repo.glob(pattern))


def has(repo: Path, rel: str) -> bool:
    return (repo / rel).exists()


def read_text(repo: Path, rel: str) -> str:
    p = repo / rel
    return p.read_text(encoding="utf-8") if p.exists() else ""
