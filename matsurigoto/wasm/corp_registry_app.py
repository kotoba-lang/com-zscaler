"""matsurigoto 政 `corp-registry` actor — WASI component (componentize-py, ADR-2606062300 R1.A).

Mirrors `methods/modules/corp_registry.cljc` (ISO 17442 LEI issuance with a real ISO 7064
MOD 97-10 check-digit computation + registry-number assignment). G1: certificate always
UNSIGNED. G5 append-only (非終末論): a change is an appended amendment, never modeled here
(the WIT surface exposes only the initial incorporation).

componentize-py binds this module's `Corp` class to the WIT `corp` interface export.
"""

from wit_world.exports.corp import Incorporation
from wit_world.imports.types import UnsignedArtifact
from componentize_py_types import Err

SERVER_HELD_AUTHORITY = False  # G1


def _to_digits(s: str) -> str:
    """ISO 7064 numeric form: digits stay, A-Z -> 10..35."""
    out = []
    for ch in s:
        if ch.isdigit():
            out.append(ch)
        elif "A" <= ch <= "Z":
            out.append(str(ord(ch) - 55))
        else:
            raise Err(f"LEI char must be [0-9A-Z], got {ch!r}")
    return "".join(out)


def _mod97(numeric_str: str) -> int:
    return int(numeric_str) % 97


def _compute_lei_check_digits(base18: str) -> str:
    if len(base18) != 18:
        raise Err(f"LEI base must be 18 chars, got {len(base18)}")
    m = _mod97(_to_digits(base18 + "00"))
    c = 98 - m
    return f"0{c}" if c < 10 else str(c)


def _validate_lei(lei: str) -> bool:
    if not isinstance(lei, str) or len(lei) != 20:
        return False
    try:
        return _mod97(_to_digits(lei)) == 1
    except Exception:
        return False


def _assign_lei(lou_prefix: str, entity_id12: str) -> str:
    if len(lou_prefix) != 4:
        raise Err("LOU prefix must be 4 chars")
    if len(entity_id12) != 12:
        raise Err("entity id must be 12 chars")
    base = (lou_prefix + "00" + entity_id12).upper()
    return base + _compute_lei_check_digits(base)


def _unsigned_certificate(kind: str, subject: str, record_id: str) -> UnsignedArtifact:
    return UnsignedArtifact(
        kind=kind,
        subject=subject,
        record_id=record_id,
        proof=None,                              # G1
        status="issued-unsigned",
    )


class Corp:
    def register_incorporation(self, entity_name, officers, capital, articles, address,
                               jurisdiction, sequence) -> Incorporation:
        if not entity_name:
            raise Err("incorporation: entity_name required")
        if not officers:
            raise Err("incorporation: at least one officer required")
        if capital < 0:
            raise Err("incorporation: capital must be >= 0")
        if not articles:
            raise Err("incorporation: articles required")
        if not address:
            raise Err("incorporation: address required")
        if sequence < 0:
            raise Err("incorporation: sequence must be >= 0")

        registry_number = f"{jurisdiction.upper()}-{sequence:08d}"
        eid = f"{sequence:012d}"[:12].rjust(12, "0").upper()
        lei = _assign_lei("EZHY", eid)
        return Incorporation(
            registry_number=registry_number,
            lei=lei,
            immutable=True,                       # G5
            certificate=_unsigned_certificate("IncorporationCertificate", registry_number, registry_number),
        )

    def validate_lei(self, lei: str) -> bool:
        return _validate_lei(lei)
