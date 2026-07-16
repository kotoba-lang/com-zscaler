"""R0 scaffold tests for kotoba_kawase.

Mirrors ``50-infra/etzhayyim-kawase-pool/test/KawaseYuiPool.r0.t.sol``:
verifies that the constitutional-invariant exception types are defined,
that each public entry point raises :class:`NotYetImplemented` at R0
(honesty about state), and that the exception hierarchy lets callers
catch the common base class.
"""

from __future__ import annotations

import pytest

import kotoba_kawase as kk
from kotoba_kawase.exceptions import (
    JurisdictionNotActivated,
    KawaseError,
    NotAdherent,
    NotYetImplemented,
    OutOfBandFx,
    PerMonthCapBreached,
)


# ---------------------------------------------------------------------
#  Public-surface shape
# ---------------------------------------------------------------------


def test_public_surface_has_send_and_claim() -> None:
    assert callable(kk.send)
    assert callable(kk.claim)


def test_public_surface_exports_dataclasses() -> None:
    assert hasattr(kk, "SendReceipt")
    assert hasattr(kk, "ClaimReceipt")


def test_stable_enum_covers_all_planned_currencies() -> None:
    # The runtime Literal is reflected in __all__ so we just check the
    # symbol exists; the values are checked by the Lexicon enum.
    assert "Stable" in kk.__all__


# ---------------------------------------------------------------------
#  R0 honesty — every public call raises NotYetImplemented
# ---------------------------------------------------------------------


def test_send_raises_not_yet_implemented() -> None:
    with pytest.raises(NotYetImplemented) as exc_info:
        kk.send(
            from_did="did:web:alice.etzhayyim.com",
            to_did="did:web:bob.etzhayyim.com",
            src_amount_minor=10_000_000,
            src_stable="USDC",
            tgt_stable="EURC",
        )
    assert "send" in exc_info.value.phase
    assert "Bootstrap-Council" in exc_info.value.phase


def test_claim_raises_not_yet_implemented() -> None:
    with pytest.raises(NotYetImplemented) as exc_info:
        kk.claim(intent_cid="b" * 46, as_did="did:web:bob.etzhayyim.com")
    assert "claim" in exc_info.value.phase
    assert "Bootstrap-Council" in exc_info.value.phase


# ---------------------------------------------------------------------
#  Exception types — verify each constitutional-gate mapping is
#  reachable and carries the right metadata for caller introspection
# ---------------------------------------------------------------------


def test_not_adherent_exception_carries_did() -> None:
    exc = NotAdherent("did:web:alice.etzhayyim.com")
    assert exc.did == "did:web:alice.etzhayyim.com"
    assert "Adherent SBT" in str(exc)
    assert "G3" in str(exc)


def test_out_of_band_fx_carries_bps_triple() -> None:
    exc = OutOfBandFx(quoted_bps=9_400, chainlink_bps=9_200, max_band_bps=50)
    assert exc.quoted_bps == 9_400
    assert exc.chainlink_bps == 9_200
    assert exc.max_band_bps == 50
    assert "200 bps" in str(exc) or "diff=200" in str(exc)


def test_per_month_cap_breached_carries_amount_and_cap() -> None:
    exc = PerMonthCapBreached(
        sender_did="did:web:alice.etzhayyim.com",
        attempted=1_500_000_000,
        cap=1_000_000_000,
    )
    assert exc.attempted == 1_500_000_000
    assert exc.cap == 1_000_000_000
    assert "G9" in str(exc)


def test_jurisdiction_not_activated_carries_juris_pair() -> None:
    exc = JurisdictionNotActivated(sender_juris="USA", recipient_juris="EUR")
    assert exc.sender_juris == "USA"
    assert exc.recipient_juris == "EUR"
    assert "G14" in str(exc)


# ---------------------------------------------------------------------
#  Exception hierarchy — base class catches every gate-specific subtype
# ---------------------------------------------------------------------


@pytest.mark.parametrize(
    "exc_cls,args",
    [
        (NotAdherent, ("did:web:x",)),
        (OutOfBandFx, (9_400, 9_200, 50)),
        (PerMonthCapBreached, ("did:web:x", 2_000_000_000, 1_000_000_000)),
        (JurisdictionNotActivated, ("USA", "DEU")),
        (NotYetImplemented, ("test phase",)),
    ],
)
def test_every_exception_inherits_from_kawase_error(exc_cls, args) -> None:
    exc = exc_cls(*args)
    assert isinstance(exc, KawaseError)
    # And from Exception (caller can use `except Exception:`)
    assert isinstance(exc, Exception)


# ---------------------------------------------------------------------
#  Type-shape probe — the dataclasses are frozen + carry the audit fields
# ---------------------------------------------------------------------


def test_send_receipt_is_frozen_dataclass() -> None:
    receipt = kk.SendReceipt(
        intent_cid="b" * 46,
        from_did="did:web:alice.etzhayyim.com",
        to_did="did:web:bob.etzhayyim.com",
        src_stable="USDC",
        tgt_stable="EURC",
        src_amount_minor=10_000_000,
        tgt_amount_minor=9_200_000,
        fx_rate_bps=9_200,
        fx_rate_attestation_cid="b" * 46,
        estimated_match_wait_seconds=300,
        deposit_tx_hash="0x" + "a" * 64,
        block_number=1,
    )
    assert receipt.fx_rate_bps == 9_200
    with pytest.raises(Exception):
        receipt.fx_rate_bps = 9_300  # type: ignore[misc]


def test_claim_receipt_is_frozen_dataclass() -> None:
    receipt = kk.ClaimReceipt(
        intent_cid="b" * 46,
        recipient_did="did:web:bob.etzhayyim.com",
        tgt_stable="EURC",
        tgt_amount_minor=9_200_000,
        claim_tx_hash="0x" + "c" * 64,
        block_number=2,
    )
    assert receipt.tgt_amount_minor == 9_200_000
    with pytest.raises(Exception):
        receipt.tgt_amount_minor = 9_300_000  # type: ignore[misc]
