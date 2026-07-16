"""kotoba_kawase — adherent-to-adherent remittance facade (R0 scaffold).

This is the R0 path-fill: every public call raises
:class:`~kotoba_kawase.exceptions.NotYetImplemented` until R1 lands. The
shape of the API surface is fixed here so reviewers can verify the
constitutional invariants from ADR-2605282200 (G3 / G4 / G5 / G7 / G9 /
G11 / G14) are honored at the interface level BEFORE R1 wires bodies.

Public surface::

    from kotoba_kawase import send, claim
    from kotoba_kawase.exceptions import (
        NotAdherent,
        OutOfBandFx,
        PerMonthCapBreached,
        JurisdictionNotActivated,
        NotYetImplemented,
    )

R1 wires::

    receipt = send(
        from_did="did:web:alice.etzhayyim.com",
        to_did="did:web:bob.etzhayyim.com",
        src_amount_minor=10_000_000,    # 10.00 USDC (6 decimals)
        src_stable="USDC",
        tgt_stable="EURC",
        memo=None,                       # optional, ADR-2605181100 envelope
    )
    # → SendReceipt(intent_cid="b...", tgt_amount_minor=9_200_000,
    #               fx_rate_bps=9200, estimated_match_wait_seconds=300, ...)

    claim_receipt = claim(
        intent_cid="b...",
        as_did="did:web:bob.etzhayyim.com",
    )
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal, Optional

from .exceptions import (
    JurisdictionNotActivated,
    KawaseError,
    NotAdherent,
    NotYetImplemented,
    OutOfBandFx,
    PerMonthCapBreached,
)

__all__ = (
    "send",
    "claim",
    "SendReceipt",
    "ClaimReceipt",
    "Stable",
    "JurisdictionNotActivated",
    "KawaseError",
    "NotAdherent",
    "NotYetImplemented",
    "OutOfBandFx",
    "PerMonthCapBreached",
)

# R1 / R2 / R3 stable enum mirrors the Lexicon enum exactly. R0 already
# allows the full set as type-level discrimination because the schema
# allows them; the runtime gating (which stables are deploy-active per
# R-cycle) is enforced by the deployed KawaseYuiPool address registry.
Stable = Literal["USDC", "EURC", "JPYC", "KRWO", "GBPe", "CHFe"]


@dataclass(frozen=True)
class SendReceipt:
    """Audit-trail record returned by :func:`send`.

    Mirrors the on-chain ``Deposited`` event + the
    ``com.etzhayyim.kawase.depositAttestation`` Lexicon record.
    """

    intent_cid: str
    from_did: str
    to_did: str
    src_stable: Stable
    tgt_stable: Stable
    src_amount_minor: int
    tgt_amount_minor: int
    fx_rate_bps: int
    fx_rate_attestation_cid: str
    estimated_match_wait_seconds: int
    deposit_tx_hash: str
    block_number: int


@dataclass(frozen=True)
class ClaimReceipt:
    """Audit-trail record returned by :func:`claim`."""

    intent_cid: str
    recipient_did: str
    tgt_stable: Stable
    tgt_amount_minor: int
    claim_tx_hash: str
    block_number: int


def send(
    from_did: str,
    to_did: str,
    src_amount_minor: int,
    src_stable: Stable,
    tgt_stable: Stable,
    memo: Optional[str] = None,
) -> SendReceipt:
    """Adherent ``from_did`` sends ``src_amount_minor`` of ``src_stable``
    to adherent ``to_did``, who will claim it as ``tgt_stable``.

    R1 will:

    1. Verify both ``from_did`` and ``to_did`` hold an Adherent SBT
       (G3 → :class:`NotAdherent`).
    2. Resolve the live Chainlink mid-market rate for
       ``src_stable``/``tgt_stable`` and check the quote against the
       constitutional ±band (G4 → :class:`OutOfBandFx`).
    3. Verify the sender's rolling 30-day USD-equivalent total + this
       call ≤ Constitution.getMutable(KAWASE_PER_MONTH_CAP_USD_MINOR)
       (G9 → :class:`PerMonthCapBreached`).
    4. Verify the sender↔recipient jurisdiction pair has an active
       Council Lv7+ unanimity ``jurisdictionAttestation`` (G14 →
       :class:`JurisdictionNotActivated`).
    5. If ``memo`` is set, encrypt it via the ADR-2605181100
       XChaCha20-Poly1305 envelope to the recipient DID.
    6. Compute the intent CID (deterministic over inputs + Charter Rider
       §2(a)-(h) scan result).
    7. Dispatch ``KawaseYuiPool.deposit(intent_cid, to_did,
       src_amount_minor, fx_rate_bps, fx_rate_attestation_cid)``.
    8. Return :class:`SendReceipt`.

    R0 raises :class:`NotYetImplemented` so callers know to wait for
    Bootstrap Council ratification (RFP closes 2026-06-19).
    """
    # Argument shape probe — keeps the type-checker honest at R0.
    _ = (from_did, to_did, src_amount_minor, src_stable, tgt_stable, memo)
    raise NotYetImplemented(
        "kotoba_kawase.send — R1 body lands post-Bootstrap-Council ratify"
    )


def claim(intent_cid: str, as_did: str) -> ClaimReceipt:
    """Recipient ``as_did`` claims the matched amount for ``intent_cid``.

    R1 will:

    1. Verify ``as_did`` holds an Adherent SBT (G3).
    2. Load the intent record from the on-chain pool; reject if unknown,
       claimed, or recipient mismatch.
    3. Dispatch ``KawaseYuiPool.claim(intent_cid)`` from ``as_did``'s
       ERC-4337 Smart Account.
    4. Return :class:`ClaimReceipt`.

    R0 raises :class:`NotYetImplemented`.
    """
    _ = (intent_cid, as_did)
    raise NotYetImplemented(
        "kotoba_kawase.claim — R1 body lands post-Bootstrap-Council ratify"
    )
