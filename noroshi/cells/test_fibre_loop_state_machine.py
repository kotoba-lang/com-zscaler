"""Tests for the noroshi fibre_loop cell state machine (ADR-2606051600). Stdlib + pytest only.

Reuses the shared substrate gates (assert_civilian / require_member_signature / witness_quorum_ok) via
the cell's path shim; nothing here re-implements them.
"""

from __future__ import annotations

import pytest

from fibre_loop.state_machine import (
    SPLICE_LOSS_MAX_DB,
    FibrePhase,
    splice_loss_db,
    transition_commit_segment,
    transition_lay,
    transition_run_align,
    transition_run_splice,
)
from safety import SafetyError


def _lay(**kw):
    base = {"use": "lay", "track_converged": True, "final_xte_m": 0.0}
    base.update(kw)
    return transition_lay({"cell_state": base})


# ── N1/G3: civilian-use gate on the lay phase ────────────────────────────────
def test_lay_civilian_use_advances():
    out = _lay()
    assert out["cell_state"]["phase"] == FibrePhase.LAID.value
    assert out["next_node"] == "run_align"


@pytest.mark.parametrize("use", ["weapon", "directed-energy", "fire-control"])
def test_lay_force_use_refused(use):
    with pytest.raises(SafetyError):
        _lay(use=use)


def test_lay_unknown_use_refused():
    with pytest.raises(SafetyError):
        _lay(use="mystery")


def test_lay_not_converged_refused():
    with pytest.raises(ValueError):
        _lay(track_converged=False)


# ── align phase ──────────────────────────────────────────────────────────────
def test_align_records_loss_and_advances():
    s1 = _lay()
    s1["cell_state"]["coupling_loss_db"] = 0.97
    s1["cell_state"]["align_converged"] = True
    out = transition_run_align(s1)
    assert out["cell_state"]["phase"] == FibrePhase.ALIGNED.value
    assert out["next_node"] == "run_splice"


def test_align_negative_loss_rejected():
    s1 = _lay()
    s1["cell_state"]["coupling_loss_db"] = -1.0
    s1["cell_state"]["align_converged"] = True
    with pytest.raises(ValueError):
        transition_run_align(s1)


def test_align_not_converged_rejected():
    s1 = _lay()
    s1["cell_state"]["coupling_loss_db"] = 0.97
    s1["cell_state"]["align_converged"] = False
    with pytest.raises(ValueError):
        transition_run_align(s1)


# ── splice phase ─────────────────────────────────────────────────────────────
def test_splice_loss_grows_with_offset_and_angle():
    assert splice_loss_db(0.0, 0.0) < splice_loss_db(3.0, 0.0)
    assert splice_loss_db(0.0, 0.0) < splice_loss_db(0.0, 2.0)


def _aligned():
    s1 = _lay()
    s1["cell_state"]["coupling_loss_db"] = 0.97
    s1["cell_state"]["align_converged"] = True
    return transition_run_align(s1)


def test_splice_passes_when_well_aligned():
    s2 = _aligned()
    s2["cell_state"]["splice_offset_um"] = 0.4
    s2["cell_state"]["splice_cleave_angle_deg"] = 0.3
    out = transition_run_splice(s2)
    assert out["cell_state"]["phase"] == FibrePhase.SPLICED.value
    assert out["cell_state"]["splice_passed"] is True
    assert out["cell_state"]["splice_loss_db"] <= SPLICE_LOSS_MAX_DB


def test_splice_over_threshold_refused():
    s2 = _aligned()
    s2["cell_state"]["splice_offset_um"] = 15.0
    with pytest.raises(ValueError):
        transition_run_splice(s2)


# ── commit phase: G7 no-server-key + G8 witness quorum ───────────────────────
def _spliced():
    s2 = _aligned()
    s2["cell_state"]["splice_offset_um"] = 0.4
    s2["cell_state"]["splice_cleave_angle_deg"] = 0.3
    return transition_run_splice(s2)


def test_full_happy_path_commits_member_signed_dry_run_segment():
    s3 = _spliced()
    s3["cell_state"]["member_sig"] = "m:ed25519:demo"
    s3["cell_state"]["witness_sigs"] = ["did:web:robot-a", "did:web:robot-b"]
    out = transition_commit_segment(s3)
    seg = out["cell_state"]["payload"]["segment"]
    assert out["cell_state"]["phase"] == FibrePhase.SEGMENT_COMMITTED.value
    assert seg["serverHeldKey"] is False     # G7
    assert seg["dryRun"] is True             # G8
    assert seg["witnessOk"] is True
    assert seg["memberSig"] == "m:ed25519:demo"


def test_server_signature_refused():
    s3 = _spliced()
    s3["cell_state"]["member_sig"] = "m:sig"
    s3["cell_state"]["server_sig"] = "s:sig"
    s3["cell_state"]["witness_sigs"] = ["did:web:robot-a", "did:web:robot-b"]
    with pytest.raises(SafetyError):
        transition_commit_segment(s3)


def test_commit_without_member_signature_refused():
    s3 = _spliced()
    s3["cell_state"]["witness_sigs"] = ["did:web:robot-a", "did:web:robot-b"]
    with pytest.raises(SafetyError):
        transition_commit_segment(s3)


def test_commit_without_witness_quorum_refused():
    s3 = _spliced()
    s3["cell_state"]["member_sig"] = "m:sig"
    s3["cell_state"]["witness_sigs"] = ["did:web:robot-a"]   # quorum < 2 (G8)
    with pytest.raises(ValueError):
        transition_commit_segment(s3)


def test_solve_raises_at_r0():
    from fibre_loop.cell import FibreLoopCell
    with pytest.raises(RuntimeError):
        FibreLoopCell().solve({})
