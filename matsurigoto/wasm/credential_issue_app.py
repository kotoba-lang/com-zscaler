"""matsurigoto 政 `credential-issue` actor — WASI component (componentize-py, ADR-2606062300 R1.A).

Mirrors `methods/modules/credential_issue.cljc` (ICAO Doc 9303 TD3 MRZ builder, real 7-3-1
weighted check-digit algorithm). Reproduces the ICAO 9303 UTOPIA/ERIKSSON worked example
exactly. G1: the document is always UNSIGNED here — the issuing state signs the chip/SOD
with its own ICAO-PKD key.

componentize-py binds this module's `Credential` class to the WIT `credential` interface export.
"""

from wit_world.exports.credential import Mrz, Passport
from wit_world.imports.types import UnsignedArtifact
from componentize_py_types import Err

SERVER_HELD_AUTHORITY = False  # G1

WEIGHTS = [7, 3, 1]


def _char_value(ch: str) -> int:
    if ch == "<":
        return 0
    if ch.isdigit():
        return ord(ch) - ord("0")
    if "A" <= ch <= "Z":
        return ord(ch) - 55  # 'A' -> 10
    raise Err(f"MRZ char must be [0-9A-Z<], got {ch!r}")


def _mrz_check_digit(data: str) -> str:
    total = sum(_char_value(ch) * WEIGHTS[i % 3] for i, ch in enumerate(data))
    return str(total % 10)


def _pad(s: str, n: int) -> str:
    s = s.upper().replace(" ", "<")
    return (s + "<" * n)[:n]


def _build_td3_mrz(doc_number, issuing_state, nationality, surname, given_names,
                   dob_yymmdd, sex, expiry_yymmdd, personal_number="") -> Mrz:
    if len(issuing_state) != 3 or len(nationality) != 3:
        raise Err("issuing_state and nationality must be 3-letter ICAO codes")
    if len(dob_yymmdd) != 6 or len(expiry_yymmdd) != 6:
        raise Err("dates must be YYMMDD (6 digits)")
    if sex not in ("M", "F", "<"):
        raise Err("sex must be M, F, or < (unspecified)")

    name_field = _pad(f"{surname}<<{given_names}", 39)
    line1 = f"P<{issuing_state.upper()}{name_field}"
    doc = _pad(doc_number, 9)
    c_doc = _mrz_check_digit(doc)
    c_dob = _mrz_check_digit(dob_yymmdd)
    c_exp = _mrz_check_digit(expiry_yymmdd)
    pers = _pad(personal_number, 14)
    c_pers = _mrz_check_digit(pers)
    composite_input = f"{doc}{c_doc}{dob_yymmdd}{c_dob}{expiry_yymmdd}{c_exp}{pers}{c_pers}"
    c_composite = _mrz_check_digit(composite_input)
    line2 = (f"{doc}{c_doc}{nationality.upper()}{dob_yymmdd}{c_dob}{sex}"
            f"{expiry_yymmdd}{c_exp}{pers}{c_pers}{c_composite}")
    return Mrz(line1=line1, line2=line2)


def _validate_td3_line2(line2: str) -> bool:
    if len(line2) != 44:
        return False
    try:
        doc, c_doc = line2[0:9], line2[9:10]
        dob, c_dob = line2[13:19], line2[19:20]
        exp, c_exp = line2[21:27], line2[27:28]
        pers, c_pers = line2[28:42], line2[42:43]
        c_comp = line2[43:44]
        if _mrz_check_digit(doc) != c_doc:
            return False
        if _mrz_check_digit(dob) != c_dob:
            return False
        if _mrz_check_digit(exp) != c_exp:
            return False
        if _mrz_check_digit(pers) != c_pers:
            return False
        composite_input = f"{doc}{c_doc}{dob}{c_dob}{exp}{c_exp}{pers}{c_pers}"
        return _mrz_check_digit(composite_input) == c_comp
    except Exception:
        return False


def _unsigned_document(kind: str, subject: str, mrz: Mrz) -> UnsignedArtifact:
    return UnsignedArtifact(
        kind=kind,
        subject=subject,
        record_id=mrz.line1,
        proof=None,                              # G1 — issuing state signs the SOD (ICAO PKD)
        status="issued-unsigned",
    )


class Credential:
    def issue_passport(self, doc_number, issuing_state, nationality, surname, given_names,
                       dob, sex, expiry, subject_did, personal_number) -> Passport:
        if not doc_number:
            raise Err("passport: doc_number required")
        if not surname:
            raise Err("passport: surname required")
        mrz = _build_td3_mrz(doc_number, issuing_state, nationality, surname, given_names,
                             dob, sex, expiry, personal_number)
        return Passport(mrz=mrz, document=_unsigned_document("Passport", subject_did, mrz))

    def validate_mrz(self, line2: str) -> bool:
        return _validate_td3_line2(line2)
