"""LangGraph Pregel wrapper for the noroshi (烽) active_alignment cell.

R0 scaffold: .solve() raises until Council activation (ADR-2606051600 §Roadmap). The cell drives a
photonic packaging robot to actively align a fibre to a grating coupler under a laser-safety interlock
(G3/G5) and commits a member-signed, server-keyless packaging job (G7), dry-run only (G8). It is NOT a
certified IEC 60825 safety controller.
"""

from __future__ import annotations

from typing import Any

from .state_machine import (
    transition_commit_job,
    transition_run_alignment,
    transition_verify_laser_safety,
)


class ActiveAlignmentCell:
    """Laser-safe, server-keyless photonic active-alignment session. G3/G5/G7/G8/N1."""

    def __init__(self) -> None:
        self._steps = [
            transition_verify_laser_safety,
            transition_run_alignment,
            transition_commit_job,
        ]

    def solve(self, input_state: dict[str, Any]) -> dict[str, Any]:
        raise RuntimeError(
            "noroshi R0 scaffold: activate active_alignment via Council ADR "
            "(post-2606051600 ratification; live actuation Lv6+, Class-3B/4 lasers Lv7+)"
        )
