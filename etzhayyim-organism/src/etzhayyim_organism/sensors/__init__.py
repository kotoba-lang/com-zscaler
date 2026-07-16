"""Per-axis sensors. Each sensor inspects repo state and returns AxisReading."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from .common import AxisReading
from . import (
    autopoiesis,
    metabolism,
    homeostasis,
    active_inference,
    reproduction,
    symbiosis,
    diversity,
    wellbecoming,
    antifragility,
    sanctification,
)

SENSORS = {
    "autopoiesis":      autopoiesis.read,
    "metabolism":       metabolism.read,
    "homeostasis":      homeostasis.read,
    "active_inference": active_inference.read,
    "reproduction":     reproduction.read,
    "symbiosis":        symbiosis.read,
    "diversity":        diversity.read,
    "wellbecoming":     wellbecoming.read,
    "antifragility":    antifragility.read,
    "sanctification":   sanctification.read,
}


def read_all(repo: Path) -> dict[str, AxisReading]:
    """Run every sensor against the repo and return readings keyed by axis."""
    return {key: fn(repo) for key, fn in SENSORS.items()}


__all__ = ["AxisReading", "SENSORS", "read_all"]
