"""matsurigoto 政 `benefit-disburse` actor — WASI component (componentize-py, ADR-2606062300 R1.A).

Mirrors `methods/modules/benefit_disburse.cljc` (COFOG division 10 social-protection
entitlement ASSESSMENT — OpenG2P government-to-person pattern, generalized to also express
etzhayyim's own non-cash Basic High Income doctrine, ADR-2605301020). G1: certificate always
UNSIGNED. Structural cash≡0 proof under `sovereign-governance`: `cash-transfer` is a valid
enum case in the WIT surface (principal B needs it for an ordinary state G2P programme), but
this module raises if a caller tries to pair it with `sovereign-governance` (principal A).

componentize-py binds this module's `Benefit` class to the WIT `benefit` interface export.
"""

from wit_world.exports.benefit import (
    EntitlementCategory, DisbursementMedium, Entitlement, ImputedValue,
)
from wit_world.imports.types import AuthorityMode, UnsignedArtifact
from componentize_py_types import Err

SERVER_HELD_AUTHORITY = False  # G1

# principal A (etzhayyim itself) may express ONLY the two non-cash media (ADR-2605301020).
_NON_CASH_MEDIA = {DisbursementMedium.IN_KIND_SERVICE, DisbursementMedium.COMMONS_ASSET_ACCESS}


def _unsigned_certificate(category: EntitlementCategory, claimant_did: str) -> UnsignedArtifact:
    return UnsignedArtifact(
        kind="EntitlementCertificate",
        subject=claimant_did,
        record_id=category.name,
        proof=None,                              # G1 — this module signs nothing
        status="assessed-unsigned",
    )


class Benefit:
    def assess_entitlement(self, claimant_did, category, medium, evidence_basis, for_principal) -> Entitlement:
        if not claimant_did:
            raise Err("entitlement: claimant_did is required")
        if not evidence_basis:
            raise Err("entitlement: evidence_basis is required (G2 spec-derived-only)")
        if for_principal == AuthorityMode.SOVEREIGN_GOVERNANCE and medium not in _NON_CASH_MEDIA:
            raise Err(
                f"entitlement: medium must be one of {[m.name for m in _NON_CASH_MEDIA]} "
                f"under sovereign-governance (ADR-2605301020 non-cash invariant), got {medium.name}"
            )
        return Entitlement(
            category=category,
            medium=medium,
            claimant_did=claimant_did,
            evidence_basis=evidence_basis,
            for_principal=for_principal,
            certificate=_unsigned_certificate(category, claimant_did),
        )

    def compute_imputed_value(self, units_consumed, unit_reference_price_usd_micros) -> ImputedValue:
        if units_consumed < 0:
            raise Err("imputed_value: units_consumed must be >= 0")
        if unit_reference_price_usd_micros < 0:
            raise Err("imputed_value: unit_reference_price_usd_micros must be >= 0")
        return ImputedValue(
            units_consumed=units_consumed,
            unit_reference_price_usd_micros=unit_reference_price_usd_micros,
            total_value_usd_micros=int(units_consumed * unit_reference_price_usd_micros),
            accounting_only=True,
        )
