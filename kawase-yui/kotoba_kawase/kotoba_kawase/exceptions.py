"""Exception types for kotoba_kawase.

Each exception mirrors the corresponding Solidity error in
``50-infra/etzhayyim-kawase-pool/src/KawaseYuiPool.sol`` so a caller can
catch the Python exception by the same name it would see in a Solidity
revert log, preserving the constitutional-invariant naming.

Constitutional gates from ADR-2605282200 that map onto exceptions:

- G3  → :class:`NotAdherent`
- G4  → :class:`OutOfBandFx`
- G9  → :class:`PerMonthCapBreached`
- G11 → (no exception — there is no reverse / unwind path by design)
- G14 → :class:`JurisdictionNotActivated`

Plus R0 honesty:

- :class:`NotYetImplemented` — every public call site raises this until
  R1 lands (post-Bootstrap-Council ratify + KawaseYuiPool R1 deploy).
"""

from __future__ import annotations


class KawaseError(Exception):
    """Base class for every kotoba_kawase exception."""


class NotAdherent(KawaseError):
    """G3 — sender or recipient lacks an Adherent SBT.

    Mirrors ``NotAdherent(address)`` in KawaseYuiPool.sol. Raised by the
    pre-flight check before any KawaseYuiPool.deposit() dispatch.
    """

    def __init__(self, did: str) -> None:
        super().__init__(f"DID lacks Adherent SBT (G3): {did!r}")
        self.did = did


class OutOfBandFx(KawaseError):
    """G4 — quoted FX rate falls outside the Chainlink ±band tolerance.

    Mirrors ``OutOfBandFx(uint256,uint256,uint256)`` in KawaseYuiPool.sol.
    The band is read from Constitution.getConstant(KAWASE_MAX_BAND_BPS)
    which is constitutional (= 50 bps = ±0.5%); widening requires a
    constitutional amendment, not a governance proposal.
    """

    def __init__(
        self,
        quoted_bps: int,
        chainlink_bps: int,
        max_band_bps: int,
    ) -> None:
        diff = abs(quoted_bps - chainlink_bps)
        super().__init__(
            f"quoted={quoted_bps} bps vs chainlink={chainlink_bps} bps "
            f"(diff={diff} bps) exceeds maxBandBps={max_band_bps}"
        )
        self.quoted_bps = quoted_bps
        self.chainlink_bps = chainlink_bps
        self.max_band_bps = max_band_bps


class PerMonthCapBreached(KawaseError):
    """G9 — deposit would push rolling 30-day USD-equivalent past the cap.

    Mirrors ``PerMonthCapBreached(address,uint256,uint256)`` in
    KawaseYuiPool.sol. The cap is read from
    Constitution.getMutable(KAWASE_PER_MONTH_CAP_USD_MINOR) which is
    mutable — Council Lv6+ ≥3 can raise it between R-cycles.
    """

    def __init__(self, sender_did: str, attempted: int, cap: int) -> None:
        super().__init__(
            f"sender={sender_did} attempted={attempted} would breach "
            f"cap={cap} (G9, ADR-2605282200)"
        )
        self.sender_did = sender_did
        self.attempted = attempted
        self.cap = cap


class JurisdictionNotActivated(KawaseError):
    """G14 — sender↔recipient jurisdiction pair lacks a Council Lv7+
    unanimity-attested ``jurisdictionAttestation`` Lexicon record.

    No direct Solidity counterpart — G14 enforcement lives in the
    ``kawase_jurisdiction_compliance`` Pregel cell (off-chain). Raised
    by ``kotoba_kawase.send(...)`` pre-flight before any on-chain
    dispatch.
    """

    def __init__(self, sender_juris: str, recipient_juris: str) -> None:
        super().__init__(
            f"jurisdiction pair {sender_juris}->{recipient_juris} lacks "
            f"Council Lv7+ unanimity activation (G14, ADR-2605282200)"
        )
        self.sender_juris = sender_juris
        self.recipient_juris = recipient_juris


class NotYetImplemented(KawaseError):
    """R0 honesty — body lands at R1 (post-Bootstrap-Council ratify).

    Every public call site in :mod:`kotoba_kawase` raises this until the
    R1 wave wires real bodies. This mirrors the
    ``revert NotYetImplemented(string)`` pattern in KawaseYuiPool.sol so
    Python and Solidity callers see structurally identical R0-honesty
    failure modes.
    """

    def __init__(self, phase: str) -> None:
        super().__init__(f"NotYetImplemented({phase!r})")
        self.phase = phase
