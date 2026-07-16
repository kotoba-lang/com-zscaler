# 幣 (nusa) — Ritual/Industrial Hemp Heritage + Low-THC Cultivation actor

**Tier-B actor · DID `did:web:etzhayyim.com:actor:nusa` · ADR-2606039800 · R0 scaffold**

幣 (*ōnusa*) is the hemp purification wand of Shinto 祓 (*harae*) — the fibre of
*cleansing*. nusa datafies Japan's **ritual + industrial hemp (麻) heritage** and designs the
**low-THC cultivation-revival** that the 2023 reform reopened — and it does so along the only
charter-clean lines, refusing the rest **by construction**.

## What this is (and what it deliberately is not)

The question that prompted it: *「日本国内での大麻の解禁についての actor は設計されているか」* —
the honest answer was *no*, and this actor closes the gap **without** becoming a legalization
campaign.

The load-bearing history: hemp was central to Shinto purification (注連縄 / 祓串 / 大幣) and to the
imperial accession rite — the **麁服 (*aratae*)**, the hemp cloth of the 大嘗祭, woven from hemp
provisioned by the **阿波忌部** lineage (the 三木家 of 木屋平). The occupation-era **大麻取締法 (1948)**
severed mainly the **fibre + ritual cultivation**, not an intoxicant culture — Japanese landrace hemp
was a **low-THC fibre type**. The **2023 reform (大麻草の栽培の規制に関する法律, 令和5年法律第84号)**
reopened a licensed **low-THC cultivator** pathway (while newly criminalizing THC *use*). So:

- **In scope (now-open space):** low-THC fibre + ritual hemp heritage + licensed cultivation design.
- **Out of scope by construction:** recreational THC (G1/G2 — `:thc-class` cannot be `:psychoactive`);
  legalization advocacy (G3 — no 推進/反対 stance, 1 SBT = 1 vote); cannabis-derived medicine
  (G10 — 薬機法/医師法 → yakushi/iyashi/mitate).

Direction 2 (legislative observation + public-comment support) is delivered by **extending danjo**
(non-adjudicating, ADR-2605301600) and **moushibumi** (neutral, ADR-2605312400), not by new logic here.

ISIC A0116 (fibre crops) · ISCO 6111/7318 · UNSPSC 10–11.

## Cells (langgraph→WASM; Murakumo-only; `.solve()` raises at R0)

- **heritage_ingest** (dan) — heritage records → kotoba EAVT (operator-gated, G8).
- **fiber_provenance** (naphtali — coded reference cell) — cultivar→fibre-use provenance with the
  **THC-class screen** (`ValueError` on any psychoactive input; G1 enforcement point).
- **cultivation_license_plan** (gad) — member-principal, server-keyless, outward-gated low-THC
  栽培者免許 design (G4/G5/G8).
- **rite_supply** (asher) — maps ritual-artifact demand (注連縄/大幣) to licensed fibre supply
  (SBT↔SBT, neutral).
- **observation_bridge** (issachar) — thin router: hands legislative/legal-characterization questions
  **off to danjo/chigiri**; nusa never adjudicates in-actor (G3).

## Gates (immutable R0→R3)

G1 low-THC/fibre-and-ritual-only (`:thc-class ∈ {:fiber,:low-thc}`; `:psychoactive` not representable) ·
**G2 non-recreational** (使用罪尊重; no consumption guidance) · **G3 non-adjudicating / political-neutral**
(facts only → danjo/chigiri; no 解禁論 stance) · **G4 member-principal / no-fiat-inflow** (licence fees
never from religious-corp funds; okaimono; nusa never licensee/funder) · G5 no-server-key · G6
Murakumo-only · G7 sourcing-honesty (`:representative`; primary-source-citing) · G8 outward-gated
(real cultivation / live filing / live ingest Council Lv6+ + operator) · G9 PII-consent ·
**G10 medical-boundary** (cannabis-derived medicine → yakushi).

## Non-goals

N1 no recreational cannabis/THC intoxicant production or advocacy · N2 not a legalization-advocacy
political/lobbying org · N3 not a cannabis-derived-medicine manufacturer (→yakushi) · N4 never
facilitates unlicensed cultivation · N5 no high-THC seed/strain trafficking.

## Build / test

```
cd methods && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_analyze.py   # EDN load + THC-class gate + report
python3 analyze.py && head out/heritage-report.md                                  # cultivar THC-class breakdown
cd ../cells && PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest test_state_machines.py  # fiber_provenance screen + license plan
```

R0 = design + wired `analyze.py` heritage report + `fiber_provenance` THC-class screen (coded) +
`cultivation_license_plan` member-principal state machine + `:representative` heritage seed. The
THC-class invariant is enforced in three places (schema, lexicon `const`, code). No live cultivation,
licence filing, or ingest; all such are Council Lv6+ + operator gated (G8).

## Do not

- Do not add a `:psychoactive` thc-class, a recreational-cannabis catalog, or any consumption/dosing
  guidance — G1/G2 (`fiber_provenance` raises `ValueError`; the schema cannot represent it).
- Do not make nusa take a 解禁-推進/反対 position or run advocacy/lobbying — G3 / §1.12 / 1 SBT = 1 vote.
  Route legislative facts to danjo, public-comment support to moushibumi (both neutral).
- Do not manufacture cannabis-derived medicine here — G10 / §薬機法 (→ yakushi/iyashi/mitate).
- Do not fund a 栽培者免許 from religious-corp/org treasury or a fiat processor, or make nusa the
  licensee-of-record — G4 / §1.3 (member-principal via okaimono, ADR-2606012100).
- Do not hold a cultivation/licence signing key server-side — G5 / ADR-2605231525 (member signs).
- Do not call any cell's `.solve()` — R0 scaffolds raise `RuntimeError` by design.
- Do not enable live cultivation, licence filing, or live 法令/heritage ingest without operator +
  Council Lv6+ (G8).
