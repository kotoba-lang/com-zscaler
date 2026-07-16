"""Phase state machine for the noroshi (烽) active_alignment cell — the safety-critical coded cell.

A photonic packaging robot aligns an optical fibre to an on-chip grating coupler. Before energising
the alignment laser it must pass a hard laser-safety gate, and the resulting packaging job must be
member-signed with NO server-held key. The gates are enforced here as pure, unit-tested transitions;
the cell's .solve() raises until Council activation (live actuation is G8-gated).

Invariants enforced (the load-bearing four):
  G3/N1 — civilian-use: weaponisation / directed-energy / dazzle / fire-control uses are
          unrepresentable; only civilian photonic-fab uses may energise a laser.
  G5    — laser-safety: any IEC 60825 hazardous class (2/3R/3B/4) requires a physical enclosure
          interlock AND a safety attestation before energising. Soft-safety, not a certified system.
  G7    — no-server-key: the packaging job is member/operator-signed; serverHeldKey=false; a server
          signature is refused by construction (ADR-2605231525).
  G8    — outward-gated: the job is dry-run only at R0; live actuation is Council Lv6+ (Lv7+ for
          Class-3B/4 near humans) + operator gated.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any

PERMITTED_USES = ("alignment", "soldering", "trimming", "inspection", "comms")
FORBIDDEN_USES = ("weapon", "directed-energy", "dazzle", "fire-control")
HAZARDOUS_CLASSES = ("2", "3R", "3B", "4")


class AlignPhase(Enum):
    INIT = "init"
    LASER_SAFE = "laser_safe"
    ALIGNED = "aligned"
    JOB_COMMITTED = "job_committed"


@dataclass
class AlignState:
    phase: str = AlignPhase.INIT.value
    robot_id: str = "noroshi-aligner-01"
    op: str = "active-align"
    # laser-safety inputs
    laser_class: str = "1"
    use: str = "alignment"
    interlock: bool = False
    attestation_ref: str = ""
    # alignment result (filled by the search; mirrors methods/active_alignment.py)
    coupling_loss_db: float = 0.0
    # job commit inputs
    member_sig: str = ""
    server_sig: str = ""            # G7: must remain empty
    server_held_key: bool = False   # G7: always false
    payload: dict = field(default_factory=dict)


def _state(d: dict[str, Any]) -> AlignState:
    return AlignState(**d.get("cell_state", {}))


def transition_verify_laser_safety(state: dict[str, Any]) -> dict[str, Any]:
    """G3/N1 + G5: refuse to energise unless the use is civilian and any hazardous class is interlocked."""
    cs = _state(state)
    cs.use = state.get("use", cs.use)
    cs.laser_class = state.get("laser_class", cs.laser_class)
    cs.interlock = state.get("interlock", cs.interlock)
    cs.attestation_ref = state.get("attestation_ref", cs.attestation_ref)

    if cs.use in FORBIDDEN_USES or cs.use not in PERMITTED_USES:
        raise ValueError(
            f"N1 violation: laser use {cs.use!r} is not a permitted civilian fab use; "
            "weaponisation / directed-energy can never be energised (Mission Charter §1.12)"
        )
    if cs.laser_class in HAZARDOUS_CLASSES:
        if not cs.interlock:
            raise ValueError(
                f"G5 violation: a Class-{cs.laser_class} laser requires a physical enclosure "
                "interlock before energising"
            )
        if not cs.attestation_ref:
            raise ValueError(
                f"G5 violation: a Class-{cs.laser_class} laser requires an operator safety "
                "attestation reference before energising"
            )

    cs.phase = AlignPhase.LASER_SAFE.value
    return {"cell_state": cs.__dict__, "next_node": "run_alignment"}


def transition_run_alignment(state: dict[str, Any]) -> dict[str, Any]:
    """Run (a stand-in for) the Hooke-Jeeves active-alignment search; record the achieved loss."""
    cs = _state(state)
    cs.coupling_loss_db = float(state.get("coupling_loss_db", cs.coupling_loss_db))
    if cs.coupling_loss_db < 0.0:
        raise ValueError("coupling loss must be ≥ 0 dB")
    cs.phase = AlignPhase.ALIGNED.value
    cs.payload["alignment"] = {"couplingLossDb": cs.coupling_loss_db}
    return {"cell_state": cs.__dict__, "next_node": "commit_job"}


def transition_commit_job(state: dict[str, Any]) -> dict[str, Any]:
    """G7: commit a member-signed packaging job with NO server-held key; refuse any server signature."""
    cs = _state(state)
    cs.member_sig = state.get("member_sig", cs.member_sig)
    cs.server_sig = state.get("server_sig", cs.server_sig)
    cs.server_held_key = False               # G7 invariant

    if cs.server_sig:
        raise ValueError("G7 violation: server signature refused (no-server-key, ADR-2605231525)")
    if not cs.member_sig:
        raise ValueError("G7 violation: a member/operator signature is required to commit a packaging job")

    cs.phase = AlignPhase.JOB_COMMITTED.value
    cs.payload["job"] = {
        "robotId": cs.robot_id,
        "op": cs.op,
        "use": cs.use,
        "laserClass": cs.laser_class,
        "interlock": cs.interlock,
        "couplingLossDb": cs.coupling_loss_db,
        "memberSig": cs.member_sig,
        "serverHeldKey": False,
        "dryRun": True,        # G8: R0 plan/replay only
    }
    return {"cell_state": cs.__dict__, "next_node": "end"}
