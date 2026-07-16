"""LangGraph Pregel wrapper for the noroshi (烽) fibre_loop cell.

R0 scaffold: .solve() raises until Council activation (ADR-2606051600 §Roadmap). The cell lays
fibre-optic cable along a planned route (cross-track tracking), actively aligns the fibre to a coupler
under the laser-safety interlock (G5, reusing methods/active_alignment.py), evaluates a fusion splice,
and commits a member-signed, witness-quorum'd, server-keyless segment (G7/G8) — dry-run only (G8). It
is NOT a certified IEC 60825 safety controller.
"""

from __future__ import annotations

from typing import Any

from .state_machine import (
    transition_commit_segment,
    transition_lay,
    transition_run_align,
    transition_run_splice,
)


class FibreLoopCell:
    """Civilian, laser-safe, server-keyless fibre lay → align → splice session. N1/G3/G5/G7/G8."""

    def __init__(self) -> None:
        self._steps = [
            transition_lay,
            transition_run_align,
            transition_run_splice,
            transition_commit_segment,
        ]

    def solve(self, input_state: dict[str, Any]) -> dict[str, Any]:
        raise RuntimeError(
            "noroshi R0 scaffold: activate fibre_loop via Council ADR "
            "(post-2606051600 ratification; live cable-laying actuation Lv6+, Class-3B/4 lasers Lv7+)"
        )
