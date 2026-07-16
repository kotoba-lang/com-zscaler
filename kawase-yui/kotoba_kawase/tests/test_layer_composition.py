"""Cross-layer composition smoke test for kawase-yui R0.

Asserts the layers laid down in iterations 1-6 actually compose:

- Iter 1: G7 lint hook exists and is executable
- Iter 1: 8 Lexicons live under 00-contracts/lexicons/com/etzhayyim/kawase/
- Iter 2: ConstitutionKeys.sol carries KAWASE_MAX_BAND_BPS + KAWASE_PER_MONTH_CAP_USD_MINOR
- Iter 3: KawaseYuiPool.sol scaffold exists and references the Constitution keys
- Iter 4: kotoba_kawase package importable and raises NotYetImplemented on send/claim
- Iter 5: each of the 5 kawase_* Pregel cells raises RuntimeError on import
- Iter 6: 20-actors/kawase-yui/ has README + manifest.jsonld with DID
           did:web:kawase-yui.etzhayyim.com

This test does NOT need a running Murakumo fleet, a Foundry / forge
install, or network access. It only validates that the kawase-yui R0
surface is structurally consistent across the 7 layers.

Why this matters: any future commit that breaks a cross-layer
constitutional invariant (e.g., removes the G7 lint hook without
landing R1, or drops a Lexicon, or unguards a cell prematurely) will
fail one of these assertions BEFORE the more expensive forge / pytest
suites run.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


_REPO_ROOT = Path(__file__).resolve().parents[4]


# ---------------------------------------------------------------------
#  Iter 1: G7 lint hook
# ---------------------------------------------------------------------


def test_g7_lint_hook_exists_and_runs_clean() -> None:
    hook = _REPO_ROOT / "70-tools/scripts/lint/verify_no_commercial_remittance.py"
    assert hook.is_file(), f"G7 lint hook missing at {hook}"
    # Hook should exit 0 on a clean tree.
    result = subprocess.run(
        [sys.executable, str(hook)],
        capture_output=True,
        text=True,
        cwd=_REPO_ROOT,
    )
    assert result.returncode == 0, (
        f"G7 lint hook failed unexpectedly. stdout={result.stdout!r} "
        f"stderr={result.stderr!r}"
    )
    assert "gate: clean" in result.stdout


# ---------------------------------------------------------------------
#  Iter 1: 8 Lexicons under com.etzhayyim.kawase.*
# ---------------------------------------------------------------------


_EXPECTED_LEXICONS = (
    "depositAttestation",
    "withdrawIntent",
    "matchExecution",
    "fxRateAttestation",
    "poolStateReport",
    "rebalanceAttestation",
    "jurisdictionAttestation",
    "silenKawaseReview",
)


def test_all_eight_lexicons_present() -> None:
    base = _REPO_ROOT / "00-contracts/lexicons/com/etzhayyim/kawase"
    assert base.is_dir(), f"Lexicon dir missing at {base}"
    for name in _EXPECTED_LEXICONS:
        lex_path = base / f"{name}.json"
        assert lex_path.is_file(), f"Lexicon missing: {lex_path}"
        # Each Lexicon parses as JSON and carries the canonical id.
        with lex_path.open() as f:
            data = json.load(f)
        expected_id = f"com.etzhayyim.kawase.{name}"
        assert data.get("id") == expected_id, (
            f"{lex_path}: id={data.get('id')!r} expected={expected_id!r}"
        )


# ---------------------------------------------------------------------
#  Iter 2: Constitution wiring
# ---------------------------------------------------------------------


def test_constitution_keys_carries_kawase_constants() -> None:
    keys_sol = _REPO_ROOT / (
        "50-infra/etzhayyim-chain-contracts/src/ConstitutionKeys.sol"
    )
    text = keys_sol.read_text(encoding="utf-8")
    assert "KAWASE_MAX_BAND_BPS" in text, (
        "ConstitutionKeys.sol must declare KAWASE_MAX_BAND_BPS (G4)"
    )
    assert "KAWASE_PER_MONTH_CAP_USD_MINOR" in text, (
        "ConstitutionKeys.sol must declare KAWASE_PER_MONTH_CAP_USD_MINOR (G9)"
    )


def test_deploy_scripts_wire_kawase_constants() -> None:
    for script_rel in (
        "50-infra/etzhayyim-chain-contracts/script/Deploy.s.sol",
        "50-infra/etzhayyim-chain-contracts/script/DeployReligiousCorp.s.sol",
    ):
        script = _REPO_ROOT / script_rel
        text = script.read_text(encoding="utf-8")
        assert "KAWASE_MAX_BAND_BPS" in text, (
            f"{script_rel} must wire KAWASE_MAX_BAND_BPS"
        )
        assert "KAWASE_PER_MONTH_CAP_USD_MINOR" in text, (
            f"{script_rel} must wire KAWASE_PER_MONTH_CAP_USD_MINOR"
        )


# ---------------------------------------------------------------------
#  Iter 3: KawaseYuiPool.sol scaffold
# ---------------------------------------------------------------------


def test_kawase_pool_scaffold_present_and_references_constitution_keys() -> None:
    pool = _REPO_ROOT / "50-infra/etzhayyim-kawase-pool/src/KawaseYuiPool.sol"
    assert pool.is_file(), f"KawaseYuiPool.sol scaffold missing at {pool}"
    text = pool.read_text(encoding="utf-8")
    # The scaffold must reference both Constitution keys via the
    # maxBandBpsKey / monthlyCapKey immutables — not hard-code the
    # keccak hashes.
    assert "maxBandBpsKey" in text, "Pool must read max-band via Constitution key"
    assert "monthlyCapKey" in text, "Pool must read monthly cap via Constitution key"
    # The R0 scaffold must revert NotYetImplemented on all 3 entry points.
    assert text.count("NotYetImplemented") >= 3, (
        "Pool R0 scaffold must revert NotYetImplemented on deposit/claim/rebalance"
    )
    # The onlyAdherent and onlyCouncilSafe modifiers must be defined.
    assert "modifier onlyAdherent" in text
    assert "modifier onlyCouncilSafe" in text


# ---------------------------------------------------------------------
#  Iter 4: kotoba_kawase Python facade
# ---------------------------------------------------------------------


def test_kotoba_kawase_send_raises_not_yet_implemented() -> None:
    # We're running from inside the kotoba_kawase package's tests/ dir,
    # so import works directly via the package's own conftest.
    import kotoba_kawase as kk
    from kotoba_kawase.exceptions import NotYetImplemented

    try:
        kk.send(
            from_did="did:web:alice.etzhayyim.com",
            to_did="did:web:bob.etzhayyim.com",
            src_amount_minor=10_000_000,
            src_stable="USDC",
            tgt_stable="EURC",
        )
    except NotYetImplemented as e:
        assert "Bootstrap-Council" in e.phase
    else:
        raise AssertionError("kotoba_kawase.send must raise NotYetImplemented at R0")


def test_kotoba_kawase_claim_raises_not_yet_implemented() -> None:
    import kotoba_kawase as kk
    from kotoba_kawase.exceptions import NotYetImplemented

    try:
        kk.claim(intent_cid="b" * 46, as_did="did:web:bob.etzhayyim.com")
    except NotYetImplemented as e:
        assert "Bootstrap-Council" in e.phase
    else:
        raise AssertionError("kotoba_kawase.claim must raise NotYetImplemented at R0")


# ---------------------------------------------------------------------
#  Iter 5: 5 Pregel cells all raise RuntimeError on import
# ---------------------------------------------------------------------


_EXPECTED_CELLS = (
    "kawase_pool_match",
    "kawase_fx_oracle_watcher",
    "kawase_rebalance_proposer",
    "kawase_jurisdiction_compliance",
    "kawase_silen_review",
)


def test_every_kawase_cell_raises_runtime_error_on_import() -> None:
    base = _REPO_ROOT / "40-engine/kotoba/crates/kotoba-kotodama/cells"
    for cell_name in _EXPECTED_CELLS:
        cell_dir = base / cell_name
        assert cell_dir.is_dir(), f"Cell dir missing: {cell_dir}"
        cell_py = cell_dir / "cell.py"
        assert cell_py.is_file(), f"cell.py missing: {cell_py}"

        # Subprocess so each cell starts with a fresh import context
        # (avoids the import-cache contamination across cells in one
        # Python process).
        result = subprocess.run(
            [
                sys.executable,
                "-c",
                f"import sys; sys.path.insert(0, '{cell_dir}'); import cell",
            ],
            capture_output=True,
            text=True,
            cwd=_REPO_ROOT,
        )
        assert result.returncode != 0, (
            f"{cell_name}.cell imported cleanly — expected RuntimeError "
            f"(stdout={result.stdout!r})"
        )
        # The error message must mention scaffold-only + the ADR id so
        # reviewers see the R0-honesty marker.
        combined = result.stdout + result.stderr
        assert "scaffold-only" in combined, (
            f"{cell_name}.cell raised but message missing 'scaffold-only': "
            f"{combined!r}"
        )
        assert "ADR-2605282200" in combined, (
            f"{cell_name}.cell raised but message missing 'ADR-2605282200': "
            f"{combined!r}"
        )


# ---------------------------------------------------------------------
#  Iter 6: actor root README + manifest.jsonld
# ---------------------------------------------------------------------


def test_actor_root_readme_and_manifest_present() -> None:
    root = _REPO_ROOT / "20-actors/kawase-yui"
    assert root.is_dir(), f"Actor root missing: {root}"

    readme = root / "README.md"
    assert readme.is_file(), f"README.md missing: {readme}"
    readme_text = readme.read_text(encoding="utf-8")
    assert "did:web:kawase-yui.etzhayyim.com" in readme_text
    assert "ADR-2605282200" in readme_text

    manifest = root / "manifest.jsonld"
    assert manifest.is_file(), f"manifest.jsonld missing: {manifest}"
    with manifest.open() as f:
        data = json.load(f)
    assert data.get("id") == "did:web:kawase-yui.etzhayyim.com"
    assert data.get("@type") == "ActorManifest"
    assert data.get("tier") == "Tier-B"


# ---------------------------------------------------------------------
#  Cross-cutting: all 7 R0 layers materialized (no remaining (reserved)
#  markers in deps.toml for kawase-* paths)
# ---------------------------------------------------------------------


def test_deps_toml_has_no_kawase_reserved_markers() -> None:
    """Every kawase scaffold path should be materialized by R0 — no
    (reserved) markers left on actual path= assignments. Description
    fields are allowed to discuss the marker convention.
    """
    deps_text = (_REPO_ROOT / "deps.toml").read_text(encoding="utf-8")
    for line in deps_text.splitlines():
        stripped = line.strip()
        # Only check lines that are actual TOML `path = "..."` assignments.
        if not stripped.startswith("path = "):
            continue
        if "kawase" in stripped and "(reserved)" in stripped:
            raise AssertionError(
                f"deps.toml still has a (reserved) marker on a kawase path "
                f"after R0 completion: {line!r}"
            )


# ---------------------------------------------------------------------
#  Iter 8: cross-actor reverse-references — sibling actor manifests
#  must mention kawase-yui in their respective cross-actor fields so
#  the relation graph is bidirectional. If any of these break, the
#  manifest.jsonld was edited without updating the cross-reference.
# ---------------------------------------------------------------------


def test_wakai_manifest_mentions_kawase_yui_sibling() -> None:
    manifest = _REPO_ROOT / "20-actors/wakai/manifest.jsonld"
    with manifest.open() as f:
        data = json.load(f)
    cross = data.get("crossActor", {})
    assert "kawase-yui" in cross, (
        "wakai.crossActor must include kawase-yui as the cross-border mutual-aid sibling"
    )
    text = cross["kawase-yui"]
    assert "ADR-2605282200" in text
    assert "mutual-aid" in text.lower()


def test_chigiri_manifest_mentions_kawase_yui_in_crossActorProcedure() -> None:
    manifest = _REPO_ROOT / "20-actors/chigiri/manifest.jsonld"
    with manifest.open() as f:
        data = json.load(f)
    procedures = data.get("crossActorProcedure", [])
    matches = [p for p in procedures if "kawase-yui" in p]
    assert matches, (
        "chigiri.crossActorProcedure must include did:web:kawase-yui.etzhayyim.com "
        "as the consumer of ipLicenseClaim (G14) + disputeMediation (G11) cross-actor"
    )
    entry = matches[0]
    assert "G14" in entry
    assert "ADR-2605282200" in entry


def test_toritate_manifest_mentions_kawase_yui_in_crossActorBoundary() -> None:
    manifest = _REPO_ROOT / "20-actors/toritate/manifest.jsonld"
    with manifest.open() as f:
        data = json.load(f)
    boundary = data.get("crossActorBoundary", [])
    matches = [b for b in boundary if "kawase-yui" in b]
    assert matches, (
        "toritate.crossActorBoundary must include kawase-yui as the source of "
        "ledgerEntry purpose=kawase-mutual-aid + annual silenKawaseReview "
        "consumption"
    )
    entry = matches[0]
    assert "ADR-2605282200" in entry
    assert "kawase-mutual-aid" in entry


def test_kawase_yui_manifest_back_references_three_siblings() -> None:
    """Symmetry check — kawase-yui's own manifest must reference all three
    sibling actors that just gained a forward-reference."""
    manifest = _REPO_ROOT / "20-actors/kawase-yui/manifest.jsonld"
    with manifest.open() as f:
        data = json.load(f)
    cross = data.get("crossActorProcedure", [])
    needed = ("chigiri", "toritate", "wakai")
    for name in needed:
        found = any(f"did:web:{name}.etzhayyim.com" in c for c in cross)
        assert found, (
            f"kawase-yui.crossActorProcedure must reference "
            f"did:web:{name}.etzhayyim.com for relation-graph symmetry"
        )


# ---------------------------------------------------------------------
#  Iter 9: Documentation completeness — every kawase surface must
#  be discoverable from the conventional index points.
# ---------------------------------------------------------------------


def test_lexicon_dir_has_readme() -> None:
    """Sibling actors (wakai / chigiri / mitate) all carry a README.md
    inside their Lexicon directory. kawase parity: same convention.
    """
    readme = _REPO_ROOT / "00-contracts/lexicons/com/etzhayyim/kawase/README.md"
    assert readme.is_file(), f"Lexicon dir README missing: {readme}"
    text = readme.read_text(encoding="utf-8")
    # Must enumerate all 8 Lexicons + reference the master ADR.
    for lex in _EXPECTED_LEXICONS:
        assert lex in text, f"Lexicon dir README must mention `{lex}`"
    assert "ADR-2605282200" in text


def test_adr_index_lists_kawase_yui() -> None:
    """The canonical ADR index at 90-docs/adr/README.md must carry a
    row for ADR-2605282200 so the kawase-yui charter is discoverable
    from the docs index.
    """
    index = _REPO_ROOT / "90-docs/adr/README.md"
    text = index.read_text(encoding="utf-8")
    # ADR ID + a stable phrase from the canonical title
    assert "2605282200" in text, "ADR index must list 2605282200"
    assert "kawase-yui" in text, "ADR index entry must mention kawase-yui"


def test_actor_claude_md_present_with_r1_runbook() -> None:
    """Sibling actors (wakai / chigiri / toritate) all carry a CLAUDE.md
    with an R1 Activation Triggers section that operators read before
    bringing the actor to R1. kawase parity: same convention.
    """
    claude_md = _REPO_ROOT / "20-actors/kawase-yui/CLAUDE.md"
    assert claude_md.is_file(), f"Actor CLAUDE.md missing: {claude_md}"
    text = claude_md.read_text(encoding="utf-8")
    # Must include the constitutional discipline section + R1 runbook
    # + cross-actor coordination + build & deploy smoke tests.
    required_sections = (
        "Identity",
        "Constitutional Discipline",
        "Architecture",
        "R1 Activation Triggers",
        "R1 Cell Activation Order",
        "Cross-Actor Coordination",
        "Build & Deploy",
    )
    for section in required_sections:
        assert section in text, (
            f"Actor CLAUDE.md must contain '{section}' section "
            f"(sibling-actor convention)"
        )
    # Must reference the master ADR + the mKOTO compute-cost layer
    assert "ADR-2605282200" in text
    assert "ADR-2605282100" in text
