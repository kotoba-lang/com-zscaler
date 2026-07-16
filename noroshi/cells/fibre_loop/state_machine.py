"""Phase state machine for the noroshi (烽) fibre_loop cell — the fibre-laying operational loop.

A cable-lay plow / ROV lays fibre-optic cable along a planned route, the fibre is actively aligned to
a coupler, the two fibre ends are fusion-spliced, and the resulting segment is committed as a
member-signed, server-keyless, dry-run job. The gates are enforced here as pure, unit-tested
transitions; the cell's .solve() raises until Council activation (live actuation is G8-gated).

Phase order: lay → align → splice → member-signed dry-run commit.

Invariants enforced (the load-bearing ones, reusing the substrate gates):
  N1/G3 — civilian-use: the lay/align/splice use must be in the civilian fibre allowlist; a force use
          (weapon / directed-energy / fire-control) is unrepresentable (assert_civilian, closed-world).
  G5    — laser-safety: the align phase inherits the IEC 60825 laser interlock from the EXISTING
          methods/active_alignment.py aligner (a hazardous class needs interlock + attestation).
  G7    — no-server-key: the segment commit is member/operator-signed (require_member_signature); a
          server signature is refused by construction (ADR-2605231525); serverHeldKey=False.
  G8    — witness quorum + outward-gating: ≥2 independent robot DIDs (witness_quorum_ok); the job is
          dry-run only at R0 (live actuation is Council Lv6+).
"""

from __future__ import annotations

import pathlib
import sys
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

# Reuse the audited shared infra-robotics safety gates (assert_civilian / require_member_signature /
# witness_quorum_ok) — the same gates used by methods/fibre_loop.py. From cells/fibre_loop the
# substrate lives at parents[3]/"kuni-umi"/"robotics".
_ROBOTICS = pathlib.Path(__file__).resolve().parents[3] / "kuni-umi" / "robotics"
if str(_ROBOTICS) not in sys.path:
    sys.path.insert(0, str(_ROBOTICS))

from safety import (  # noqa: E402
    assert_civilian,
    require_member_signature,
    witness_quorum_ok,
)

# Mirror the methods-core constants/model locally (same convention as the active_alignment cell, which
# keeps its own copy of the laser-safety enums). Kept identical to methods/fibre_loop.py.
PERMITTED_USES = ("lay", "align", "splice", "inspect", "repair", "bury")
SPLICE_LOSS_MAX_DB = 0.10
_SPLICE_K_OFFSET = 0.0016
_SPLICE_K_ANGLE = 0.012


def splice_loss_db(lateral_offset_um: float, cleave_angle_deg: float) -> float:
    """Fusion-splice insertion loss (dB) — grows with lateral offset² and cleave-angle². Mirrors core."""
    off = abs(lateral_offset_um)
    ang = abs(cleave_angle_deg)
    return round(_SPLICE_K_OFFSET * off * off + _SPLICE_K_ANGLE * ang * ang, 6)


class FibrePhase(Enum):
    INIT = "init"
    LAID = "laid"
    ALIGNED = "aligned"
    SPLICED = "spliced"
    SEGMENT_COMMITTED = "segment_committed"


@dataclass
class FibreState:
    phase: str = FibrePhase.INIT.value
    robot_id: str = "noroshi-cablelay-01"
    op: str = "lay-align-splice"
    use: str = "lay"
    # lay result
    track_converged: bool = False
    final_xte_m: float = 0.0
    # align result (from the reused Hooke-Jeeves aligner; mirrors methods/active_alignment.py)
    coupling_loss_db: float = 0.0
    align_converged: bool = False
    # splice inputs/result
    splice_offset_um: float = 0.0
    splice_cleave_angle_deg: float = 0.0
    splice_loss_db: float = 0.0
    splice_passed: bool = False
    # commit inputs
    member_sig: str = ""
    server_sig: str = ""             # G7: must remain empty
    server_held_key: bool = False    # G7: always false
    witness_sigs: list = field(default_factory=list)
    payload: dict = field(default_factory=dict)


def _state(d: dict[str, Any]) -> FibreState:
    return FibreState(**d.get("cell_state", {}))


def transition_lay(state: dict[str, Any]) -> dict[str, Any]:
    """N1/G3: refuse a non-civilian use, then record the cross-track-tracking outcome."""
    cs = _state(state)
    cs.use = state.get("use", cs.use)
    assert_civilian(cs.use, PERMITTED_USES)   # raises SafetyError on a non-civilian / force use

    cs.track_converged = bool(state.get("track_converged", cs.track_converged))
    cs.final_xte_m = float(state.get("final_xte_m", cs.final_xte_m))
    if not cs.track_converged:
        raise ValueError("lay phase: the plow has not converged onto the planned route")
    cs.phase = FibrePhase.LAID.value
    cs.payload["lay"] = {"trackConverged": cs.track_converged, "finalXteM": cs.final_xte_m}
    return {"cell_state": cs.__dict__, "next_node": "run_align"}


def transition_run_align(state: dict[str, Any]) -> dict[str, Any]:
    """Record the achieved coupling loss from the reused Hooke-Jeeves aligner (G5 gate runs in core)."""
    cs = _state(state)
    cs.coupling_loss_db = float(state.get("coupling_loss_db", cs.coupling_loss_db))
    cs.align_converged = bool(state.get("align_converged", cs.align_converged))
    if cs.coupling_loss_db < 0.0:
        raise ValueError("coupling loss must be ≥ 0 dB")
    if not cs.align_converged:
        raise ValueError("align phase: the active-alignment search did not converge")
    cs.phase = FibrePhase.ALIGNED.value
    cs.payload["align"] = {"couplingLossDb": cs.coupling_loss_db}
    return {"cell_state": cs.__dict__, "next_node": "run_splice"}


def transition_run_splice(state: dict[str, Any]) -> dict[str, Any]:
    """Evaluate the fusion splice against the acceptance threshold (reuses the core loss model)."""
    cs = _state(state)
    cs.splice_offset_um = float(state.get("splice_offset_um", cs.splice_offset_um))
    cs.splice_cleave_angle_deg = float(state.get("splice_cleave_angle_deg", cs.splice_cleave_angle_deg))
    cs.splice_loss_db = splice_loss_db(cs.splice_offset_um, cs.splice_cleave_angle_deg)
    cs.splice_passed = cs.splice_loss_db <= SPLICE_LOSS_MAX_DB
    if not cs.splice_passed:
        raise ValueError(
            f"splice phase: loss {cs.splice_loss_db} dB exceeds the acceptance threshold "
            f"{SPLICE_LOSS_MAX_DB} dB"
        )
    cs.phase = FibrePhase.SPLICED.value
    cs.payload["splice"] = {"lossDb": cs.splice_loss_db, "passed": cs.splice_passed}
    return {"cell_state": cs.__dict__, "next_node": "commit_segment"}


def transition_commit_segment(state: dict[str, Any]) -> dict[str, Any]:
    """G7 + G8: commit a member-signed, witness-quorum'd segment with NO server-held key (dry-run)."""
    cs = _state(state)
    cs.member_sig = state.get("member_sig", cs.member_sig)
    cs.server_sig = state.get("server_sig", cs.server_sig)
    cs.witness_sigs = list(state.get("witness_sigs", cs.witness_sigs))
    cs.server_held_key = False               # G7 invariant

    require_member_signature(cs.member_sig, cs.server_sig)  # G7: raises on server-sig or missing member-sig
    wq = witness_quorum_ok(cs.witness_sigs)                 # G8: ≥2 independent robot DIDs
    if not wq["ok"]:
        raise ValueError(f"G8 violation: {wq['reason']}")

    cs.phase = FibrePhase.SEGMENT_COMMITTED.value
    cs.payload["segment"] = {
        "robotId": cs.robot_id,
        "op": cs.op,
        "use": cs.use,
        "finalXteM": cs.final_xte_m,
        "couplingLossDb": cs.coupling_loss_db,
        "spliceLossDb": cs.splice_loss_db,
        "splicePassed": cs.splice_passed,
        "memberSig": cs.member_sig,
        "witnessOk": True,
        "serverHeldKey": False,
        "dryRun": True,        # G8: R0 plan/replay only
    }
    return {"cell_state": cs.__dict__, "next_node": "end"}
