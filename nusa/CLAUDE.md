# nusa (幣) — ritual/industrial hemp heritage + low-THC cultivation actor

**DID**: `did:web:etzhayyim.com:actor:nusa` · **Tier**: B · **Status**: R0 · **ADR**: 2606039800

## What this is

The charter-clean answer to *「日本国内での大麻の解禁についての actor は設計されているか」*. It is
**not** a legalization actor. 幣 = ōnusa (the hemp purification wand). nusa datafies Japan's ritual +
industrial **hemp (麻)** heritage — Shinto purification (注連縄/祓串/大幣), the imperial 麁服 (*aratae*,
Daijōsai hemp cloth) + the 阿波忌部 lineage — and designs the **low-THC** cultivation-revival the 2023
reform reopened, while refusing the prohibited space **by construction**.

The three lines, which are the gate boundaries:
- low-THC fibre + ritual + heritage = **in scope**;
- recreational THC = **excluded by the `:thc-class` invariant** (G1/G2; 使用罪尊重);
- cannabis-derived medicine = **out of scope** (G10 → yakushi/iyashi/mitate);
- 解禁論 推進/反対 = **no stance** (G3 → danjo observes facts, moushibumi supports neutral public comment).

ISIC A0116 · ISCO 6111/7318 · UNSPSC 10–11.

## Cells (langgraph→WASM; Murakumo-only; `.solve()` raises at R0)

heritage_ingest (dan) · **fiber_provenance** (naphtali — coded; THC-class screen, `ValueError` on
psychoactive) · cultivation_license_plan (gad — member-principal state machine) · rite_supply (asher) ·
observation_bridge (issachar — routes legal questions to danjo/chigiri, never adjudicates).

## Gates (immutable)

G1 low-THC/fibre-and-ritual-only · G2 non-recreational · G3 non-adjudicating/political-neutral ·
G4 member-principal/no-fiat-inflow · G5 no-server-key · G6 Murakumo-only · G7 sourcing-honesty ·
G8 outward-gated · G9 PII-consent · G10 medical-boundary. (Full text: `manifest.edn` / README / ADR.)

## The G1 invariant lives in THREE places

1. **schema** `00-contracts/schemas/ritual-hemp-ontology.kotoba.edn` — `:hemp/thc-class :db/allowed [:fiber :low-thc]`.
2. **lexicon** `lex/hempCultivar.edn` / `lex/fiberProvenance.edn` — `thcClass` enum `["fiber" "low-thc"]` (no `psychoactive`).
3. **code** `cells/fiber_provenance/state_machine.py` + `methods/analyze.py` — raise/flag on any non-fibre class.

## Build / test

```
cd methods && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_analyze.py
cd cells   && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_state_machines.py
```

(Repo pytest plugin env is broken — `pydantic`/`langsmith`; the `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1`
prefix runs the suites in isolation.)

## Do not

- Do not introduce a `:psychoactive` thc-class, a recreational catalog, or consumption/dosing guidance — G1/G2.
- Do not take a 解禁 推進/反対 stance or run advocacy/lobbying — G3 / §1.12 (→ danjo/moushibumi, both neutral).
- Do not manufacture cannabis-derived medicine — G10 (→ yakushi).
- Do not fund a 栽培者免許 from the org/fiat or make nusa the licensee — G4 / §1.3 (member-principal, okaimono).
- Do not hold a licence signing key server-side — G5 (member signs).
- Do not call any cell `.solve()` — R0 raises `RuntimeError`.
- Do not enable live cultivation / licence filing / live ingest without operator + Council Lv6+ — G8.
