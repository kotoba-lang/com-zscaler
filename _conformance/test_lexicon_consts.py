"""Cross-actor lexicon-const conformance guard for the labor-liberation robotics wave.

ADR-2606032100 / ADR-2606032130. A maturation guard, NOT a cell. It scans the actor
lexicons (`20-actors/<actor>/lex/*.edn`) and the give lexicon JSON, and asserts that every
constitutional invariant pinned as a constant is still pinned to the correct value — so no
future edit can silently weaken a `const` (e.g. flip privacy `onDeviceOnly` to false, or
make the Displacement Dividend pay cash by removing `cashStipendUsdMicros const 0`).

Pure stdlib, pure text scan (no EDN parser, no cross-dir imports) → robust + fast.
Run: `python3 20-actors/_conformance/test_lexicon_consts.py`
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ACTORS = ROOT / "20-actors"
GIVE = ROOT / "00-contracts" / "lexicons" / "com" / "etzhayyim" / "give"

# EDN: ":field {:type \"boolean\" :const true :description \"...\"}"
_EDN_CONST = re.compile(r":([A-Za-z][\w-]*)\s*\{[^{}]*?:const\s+(true|false|\d+)[^{}]*?\}")


def edn_consts(path: Path) -> dict[str, str]:
    """field -> const literal (as text) for every :const in an EDN lexicon."""
    text = path.read_text(encoding="utf-8")
    return {m.group(1): m.group(2) for m in _EDN_CONST.finditer(text)}


def lex(actor: str, name: str) -> dict[str, str]:
    return edn_consts(ACTORS / actor / "lex" / f"{name}.edn")


# ─── the constitutional const invariants that must never be weakened ──────────
# (field, expected-const-text). Each maps to a gate/non-goal in ADR-2606032100/2606032130.
EDN_EXPECTATIONS = {
    # sanae — regenerative-only (G9) + seed sovereignty (N5)
    ("sanae", "weedingPassRecord", "herbicideFree"): "true",
    ("sanae", "seedingAttestation", "patented"): "false",
    # hataori — fair-labor-provenance (G9) + no IP gatekeeping (N5)
    ("hataori", "fairLaborProvenance", "noWorkerBelowBhi"): "true",
    ("hataori", "cuttingPlanAttestation", "patented"): "false",
    # kiyome — privacy-by-construction (G9) + no biometric (N5)
    ("kiyome", "cleaningPassAttestation", "onDeviceOnly"): "true",
    ("kiyome", "cleaningPassAttestation", "imageryRetained"): "false",
    ("kiyome", "siteAssessmentRecord", "onDeviceOnly"): "true",
    ("kiyome", "siteAssessmentRecord", "biometricCapture"): "false",
}


def test_edn_const_invariants_hold():
    for (actor, name, field), expected in EDN_EXPECTATIONS.items():
        consts = lex(actor, name)
        assert field in consts, f"{actor}/{name}: missing :const field {field!r} (invariant unguarded!)"
        assert consts[field] == expected, (
            f"{actor}/{name}.{field}: const is {consts[field]!r}, must be {expected!r} "
            f"(constitutional invariant weakened — see ADR-2606032100)"
        )


def test_dividend_cash_is_const_zero():
    """The on-chain proof N1 holds: the Displacement Dividend lexicon pins cash to 0."""
    spec = json.loads((GIVE / "displacementTenureAttestation.json").read_text())
    props = spec["defs"]["main"]["record"]["properties"]
    assert props["cashStipendUsdMicros"]["const"] == 0, (
        "cashStipendUsdMicros const must be 0 (cash≡0 / N1, ADR-2605301020 §5)"
    )
    # and it must be required, so it cannot be omitted to dodge the proof
    assert "cashStipendUsdMicros" in spec["defs"]["main"]["record"]["required"]


def test_every_actor_has_its_gate_lexicons():
    """Each wave actor must ship the lexicon that carries its defining gate."""
    must_exist = {
        "sanae": ["weedingPassRecord", "soilRegenerationReport", "seedingAttestation"],
        "hataori": ["fairLaborProvenance", "finishedLotAttestation"],
        "kiyome": ["cleaningPassAttestation", "siteAssessmentRecord", "wasteSegregationRecord"],
    }
    for actor, names in must_exist.items():
        for n in names:
            assert (ACTORS / actor / "lex" / f"{n}.edn").exists(), f"{actor}: missing lex/{n}.edn"


def test_coded_cells_keep_solve_gated():
    """R0 invariant: every coded cell's .solve() raises (no accidental live activation).

    Scoped to cells still implemented in Python. The ADR-2606160842 py→clj wave has now
    migrated all three labour-liberation coded cells — sanae (autonomous_weeding) + kiyome
    (surface_cleaning) + hataori (finishing_packing) — to .cljc state machines; their langgraph
    cell.py R0 wrappers were pruned as dead scaffold and their gating now lives in the cljc cell
    tests (the R0-gating moved with them). No Python coded cells remain in this wave.
    """
    coded = []  # all wave coded cells migrated to cljc; re-add a path here if a Python cell returns
    for p in coded:
        src = p.read_text(encoding="utf-8")
        assert "raise RuntimeError" in src, f"{p}: .solve() must raise RuntimeError at R0"
        assert "R0 scaffold" in src, f"{p}: missing R0 scaffold marker"


if __name__ == "__main__":
    import sys
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    failed = 0
    for fn in fns:
        try:
            fn(); print(f"PASS {fn.__name__}")
        except AssertionError as e:
            failed += 1; print(f"FAIL {fn.__name__}: {e}")
    print(f"\n{len(fns) - failed}/{len(fns)} passed")
    sys.exit(1 if failed else 0)
