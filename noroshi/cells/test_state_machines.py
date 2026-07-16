"""Tests for the noroshi active_alignment cell state machine (ADR-2606051600). Stdlib + pytest only."""

from __future__ import annotations

import pytest

from active_alignment.state_machine import (
    AlignPhase,
    transition_commit_job,
    transition_run_alignment,
    transition_verify_laser_safety,
)


def _run_safety(**kw):
    return transition_verify_laser_safety({"cell_state": kw})


# ── G3/N1: civilian-use gate ─────────────────────────────────────────────────
def test_class1_alignment_passes_safety():
    out = _run_safety(use="alignment", laser_class="1")
    assert out["cell_state"]["phase"] == AlignPhase.LASER_SAFE.value
    assert out["next_node"] == "run_alignment"


@pytest.mark.parametrize("use", ["weapon", "directed-energy", "dazzle", "fire-control"])
def test_weaponisation_use_refused(use):
    with pytest.raises(ValueError):
        _run_safety(use=use, laser_class="1")


def test_unknown_use_refused():
    with pytest.raises(ValueError):
        _run_safety(use="mystery", laser_class="1")


# ── G5: hazardous-class interlock gate ───────────────────────────────────────
def test_hazardous_class_without_interlock_refused():
    with pytest.raises(ValueError):
        _run_safety(use="soldering", laser_class="4", interlock=False)


def test_hazardous_class_without_attestation_refused():
    with pytest.raises(ValueError):
        _run_safety(use="trimming", laser_class="3B", interlock=True, attestation_ref="")


def test_hazardous_class_fully_attested_passes():
    out = _run_safety(use="soldering", laser_class="4", interlock=True,
                      attestation_ref="attest:noroshi-lsm-001")
    assert out["cell_state"]["phase"] == AlignPhase.LASER_SAFE.value


# ── alignment + G7 commit ────────────────────────────────────────────────────
def test_full_happy_path_commits_member_signed_dry_run_job():
    s1 = _run_safety(use="alignment", laser_class="1")
    s1["cell_state"]["coupling_loss_db"] = 0.97
    s2 = transition_run_alignment(s1)
    s2["cell_state"]["member_sig"] = "m:ed25519:demo"
    s3 = transition_commit_job(s2)
    job = s3["cell_state"]["payload"]["job"]
    assert s3["cell_state"]["phase"] == AlignPhase.JOB_COMMITTED.value
    assert job["serverHeldKey"] is False
    assert job["dryRun"] is True
    assert job["memberSig"] == "m:ed25519:demo"


def test_server_signature_refused():
    s1 = _run_safety(use="alignment", laser_class="1")
    s2 = transition_run_alignment(s1)
    s2["cell_state"]["member_sig"] = "m:sig"
    s2["cell_state"]["server_sig"] = "s:sig"
    with pytest.raises(ValueError):
        transition_commit_job(s2)


def test_commit_without_member_signature_refused():
    s1 = _run_safety(use="alignment", laser_class="1")
    s2 = transition_run_alignment(s1)
    with pytest.raises(ValueError):
        transition_commit_job(s2)


def test_negative_coupling_loss_rejected():
    s1 = _run_safety(use="alignment", laser_class="1")
    s1["cell_state"]["coupling_loss_db"] = -1.0
    with pytest.raises(ValueError):
        transition_run_alignment(s1)


def test_solve_raises_at_r0():
    from active_alignment.cell import ActiveAlignmentCell
    with pytest.raises(RuntimeError):
        ActiveAlignmentCell().solve({})
