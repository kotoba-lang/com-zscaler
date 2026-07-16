"""matsurigoto 政 `civil-registry` actor — WASI component (componentize-py, ADR-2606062300 R1.A).

Mirrors `methods/modules/civil_registry.cljc` (UN CRVS + OpenCRVS pure-function validation +
append-only record construction). G1: certificates are always returned UNSIGNED — the
governing organ signs with its own key. G5 append-only (非終末論): every call constructs a
new immutable record; nothing is overwritten.

componentize-py binds this module's `Civil` class to the WIT `civil` interface export.
"""

from wit_world.exports.civil import VitalRecord, Registration
from wit_world.imports.types import UnsignedArtifact
from componentize_py_types import Err

SERVER_HELD_AUTHORITY = False  # G1


def _iso(s: str) -> str:
    """ISO-8601 strings sort lexically; we only need ordering + non-future checks."""
    if not isinstance(s, str) or len(s) < 4 or not s[:4].isdigit():
        raise Err(f"timestamp must be ISO-8601, got {s!r}")
    return s


def _unsigned_certificate(kind: str, subject: str, record_id: str) -> UnsignedArtifact:
    return UnsignedArtifact(
        kind=f"{kind.capitalize()}Certificate",
        subject=subject,
        record_id=record_id,
        proof=None,                              # G1 — this module signs nothing
        status="issued-unsigned",
    )


def _record(kind: str, record_id: str, occurred_at: str) -> VitalRecord:
    return VitalRecord(record_id=record_id, vital_kind=kind, occurred_at=occurred_at,
                       immutable=True)            # G5 — appended, never overwritten


class Civil:
    def register_birth(self, record_id, child, parents, place, occurred_at, now) -> Registration:
        if not child:
            raise Err("birth: child is required")
        if not parents:
            raise Err("birth: at least one parent is required")
        if not place:
            raise Err("birth: place is required")
        if _iso(occurred_at) > _iso(now):
            raise Err("birth: occurrence cannot be in the future")
        return Registration(entry=_record("birth", record_id, occurred_at),
                            certificate=_unsigned_certificate("birth", child, record_id))

    def register_death(self, record_id, decedent, place, occurred_at, now, cause) -> Registration:
        if not decedent:
            raise Err("death: decedent is required")
        if not place:
            raise Err("death: place is required")
        if _iso(occurred_at) > _iso(now):
            raise Err("death: occurrence cannot be in the future")
        return Registration(entry=_record("death", record_id, occurred_at),
                            certificate=_unsigned_certificate("death", decedent, record_id))

    def register_marriage(self, record_id, partner_a, partner_b, place, occurred_at, now) -> Registration:
        if not partner_a or not partner_b:
            raise Err("marriage: two partners are required")
        if partner_a == partner_b:
            raise Err("marriage: partners must be distinct")
        if not place:
            raise Err("marriage: place is required")
        if _iso(occurred_at) > _iso(now):
            raise Err("marriage: occurrence cannot be in the future")
        return Registration(entry=_record("marriage", record_id, occurred_at),
                            certificate=_unsigned_certificate("marriage", record_id, record_id))

    def register_residency(self, record_id, person, new_address, occurred_at, now, prior_address) -> Registration:
        if not person:
            raise Err("residency: person is required")
        if not new_address:
            raise Err("residency: new_address is required")
        if _iso(occurred_at) > _iso(now):
            raise Err("residency: occurrence cannot be in the future")
        return Registration(entry=_record("residency", record_id, occurred_at),
                            certificate=_unsigned_certificate("residency", person, record_id))
