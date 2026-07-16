# matsurigoto 政 — R1 productionization design (reference → deployable e-gov substrate)

**Status**: design (proposed) · **ADR**: 2606062300 · **Date**: 2026-06-06
**Author**: Jun Kawasaki

This document closes the honest gap between **R0** (what exists) and a **kotoba-wasm
e-government substrate an existing government — or the Kingdom itself — can actually run**.

---

## 0. Where R0 actually is (honest)

R0 shipped a COFOG-grounded **standard** + **4 spec-anchored reference modules** (68 tests):

| module | spec anchor (reproduced exactly) | backs |
|---|---|---|
| `tax-assess` | JP 速算表 (progressive brackets) | 納税/徴税 |
| `civil-registry` | UN CRVS, append-only | 住所管理/戸籍 |
| `corp-registry` | ISO 17442 LEI / ISO 7064 MOD 97-10 | 法人登記 |
| `credential-issue` | ICAO 9303 MRZ specimen | パスポート発行 |

**What R0 proved**: the algorithms are *correct against the public specs*.
**What R0 is NOT**: a deployable system. Four things are still missing — each module is a pure
Python function returning **dicts** (not persisted), the receipts/certificates are **unsigned
skeletons**, the code is **not WASM**, and each spec surface is a **thin slice** (brackets, not
the whole tax code; MRZ, not the chip LDS/SOD/biometrics).

R1 makes those four real. **None of this weakens an invariant** — it instantiates them on the
existing etzhayyim substrate (kotoba / ameno-e7m WASM runtime / no-server-key / Murakumo).

---

## 1. The four R1 dimensions

```
R0 pure-fn dict   ──►  R1.A  WASM component (CID on IPFS, SBOM-attested)
   (in-memory)    ──►  R1.B  kotoba Datom log (canonical, append-only, as-of, replayable)
   (unsigned)     ──►  R1.C  signing/authority layer (Council multisig | adopting-state keys)
   (thin slice)   ──►  R1.D  full spec surface (rate tables / LDS-SOD / GLEIF / X-Road)
```

### R1.A — WASM-ification (reuse the existing actor runtime)

Each `methods/modules/<id>.py` → a **WASM Component** via **componentize-py** (the watatsuna /
watatsuna precedent, ADR-2606014600), content-addressed on **IPFS**, run in one of the three
existing donated node classes (ADR-2606012100): **ameno** (browser WebGPU/wasm), **e7m**
(`e7m-wasm-runner` mesh exec, ADR-2606015200/15400), **kotoba pod**.

- Trustless load: the apex `/ipfs/<cid>` gateway re-verifies the CID (ADR-2606014600); the
  `@etzhayyim/ameno` wasm-actor-loader runs it browser-local.
- **SBOM at deploy**: `70-tools/scripts/wasm-sbom/wasm_sbom_gen.py` (ADR-2606036000) recomputes
  the kotoba program CID + emits CycloneDX — a deployed gov module carries its bill of materials.
- **WIT contract**: define `world matsurigoto:egov/<module>` with typed `assess`/`register`/
  `issue` exports (the dict shapes in R0's `io` become WIT records). One WIT per module.
- Each module stays **pure** (no ambient authority) — exactly what makes it safe as a WASM
  component and keeps G1 structural.

Deliverables: `00-contracts/wit/matsurigoto/<module>.wit` · `componentize` build per module ·
CID + SBOM in the standard EDN (`:egov.module/cid` / `:egov.module/sbom`).

### R1.B — kotoba Datom persistence (records become canonical state)

R0 returns dicts. R1 writes them as **Datoms** to the kotoba log (ADR-2605262130 + 2605312345),
making state **canonical, append-only, replayable, as-of** — the same membrane every other actor
uses (ake/watari/kanjo).

- New schema `00-contracts/schemas/egov-execution-ontology.kotoba.edn`:
  `:egov.tx/*` (a service execution), `:egov.record/*` (a civil/corp/credential record),
  `:egov.assessment/*` (a tax assessment), each with `:*/as-of`, `:*/operated-by`,
  `:*/spec-version`, `:*/sourcing`.
- A registration = a `kg.ingest_batch` body (the wasm-sbom precedent): the record + its edges.
- **非終末論**: a correction/amendment is an appended datom; `as-of` time-travel surfaces the
  full history. `civil-registry`/`corp-registry` already enforce append-only in code — R1.B just
  routes their output to the real log instead of an in-memory list.
- Read path: `kotoba-kqe` EAVT arrangements (no projection layer, ADR-2605262130 D7).

Deliverables: the ontology + per-module `to_datoms()` + a `kg.ingest_batch` writer (offline,
**ingest operator-gated** like every other actor — G8).

### R1.C — signing / authority layer (the no-server-key crux)

R0 receipts/certificates are `signature: None` **by construction (G1)**. R1 attaches a signature
**without ever introducing a platform/operator key** (ADR-2605231525). Two principals, two key
custodians:

- **Principal A (etzhayyim-sovereign)**: the **Council 5-of-7 Safe** signs a governance act +
  **1 SBT = 1 vote** authorizes a class of acts; member-signed for member-affecting acts. Every
  signed act is anchored on-chain (Base L2) — **Transparent Force §1.12**.
- **Principal B (adopting nation-state)**: the **state's OWN keys** sign (e.g. its ICAO-PKD
  CSCA/DS for passports, its tax-authority cert for filings). matsurigoto hands the unsigned
  artifact to the state's signer; **etzhayyim never holds the state's key**.

The module never signs (it stays pure). The signer is an **external capability** the governing
organ supplies — mirrors `okaimono`'s member-principal checkout (`authorize_payment`,
member-sig only, server-sig refused) and the self-certifying did:key attestation (ADR-2606015600).

Deliverables: a `sign_capability` interface (verify-only in-repo) + the receipt/certificate
`proof` slot wired to it; `e7m verify` 9th-invariant check that no module embeds a key.

### R1.D — full spec surface (the long tail, per module)

R0 is a representative slice. Production needs each spec's full surface, jurisdiction by
jurisdiction (the `:representative → :authoritative` promotion, ooyake G5 + ake membrane):

- `tax-assess`: per-jurisdiction **rate tables + deduction/credit rules** in `data/rates/<iso3>.edn`
  (R0 has only embedded JP/flat brackets); full return schemas (e-Tax XML, IRS MeF, SAF-T).
- `credential-issue`: chip **LDS data groups + SOD** (ICAO 9303 part 10/11), DG1-DG16,
  ISO/IEC 19794 biometrics, passive/active authentication — **biometric capture stays R3-gated**
  (薬機/privacy; on-device only).
- `corp-registry`: live **GLEIF** LEI registration/renewal + EU **BRIS** message shapes.
- `benefit-disburse` / `interop-bus`: **ISO 20022** pain/pacs + **X-Road** SOAP/REST envelope +
  consent tokens.

Deliverables: `data/rates/`, `data/schemas/` per module; promotion of `:representative` profile
fields to `:authoritative` only with verifiable provenance (ake G4).

---

## 2. R2 — identity / auth + key infrastructure (Council-gated)

Not loop-doable; needs Council ratification of the no-server-key custody model.

- **Auth**: eIDAS 2.0 / EUDI wallet (principal B) · Adherent SBT + WebAuthn passkey + did:web
  (principal A) — the `eid-wallet` module's job.
- **Key infra**: Council 5-of-7 Safe operational for governance signing; per-adopting-state key
  onboarding runbook; ICAO-PKD trust for passports.
- **Gate**: ADR-2606062300 must reach **Council Lv7+** (it asserts the Kingdom governs) before any
  principal-A live act; principal-B live acts need the adopting state's authority.

## 3. R3 — live integration + conformance certification + pilot

- Conformance test vectors against **real** national systems (e-Tax, ICAO PKD, GLEIF API, an
  X-Road test member).
- A **pilot**: either the Kingdom's own governance (principal A — start here, lowest external
  risk: 信者 roster as civil registry, SBT as ID, TitheRouter as tax) OR a small adopting
  jurisdiction (principal B).
- Deployment substrate option: **kotoba-os** (ADR-2606031600) for field/edge gov nodes.

---

## 4. Dependency graph + honest blockers

```
R1.A WASM  ── needs ──► kotoba submodule populated (componentize-py toolchain) [BLOCKER]
R1.B Datom ── needs ──► kotoba live read/write surface (operator-gated today)  [GATE: G8]
R1.C sign  ── needs ──► Council 5-of-7 Safe operational + ADR Lv7+ ratify      [GATE: Council]
R1.D spec  ── needs ──► per-jurisdiction primary-source verification (ake G4)  [LABOR]
R2/R3      ── needs ──► Council Lv7+ + (principal B) an adopting state          [ORG]
```

**What is purely code (loop/PR-doable now)**: R1.A WIT contracts, R1.B ontology + `to_datoms()`,
R1.D rate-table/schema data files, the `sign_capability` verify-only interface (R1.C minus live
keys). These can land as offline, operator-gated scaffolds exactly like every other actor's R1.

**What is NOT code**: live kotoba ingest (G8), Council key custody (Council), an adopting
government (org). These are gates, not engineering tasks — they cannot be closed by writing more
modules.

---

## 5. Recommended sequence

1. **R1.A + R1.B scaffold** (offline) — ✅ **DONE 2026-06-06**: `00-contracts/wit/matsurigoto/egov.wit`
   (4 worlds, `wasm-tools`-valid; no `sign` export = structural G1) + `egov-execution-ontology.kotoba.edn`
   + `methods/datoms.py` (`*_datoms()` + gated `kg_ingest_batch()`) + `test_datoms.py` (10). The
   WASM+Datom path is proven end-to-end dry-run (G1/G3/G5 enforced, G8 publish raises).
2. **R1.C verify-only** — ✅ **DONE 2026-06-06**: `methods/sign_capability.py` (10 tests). External
   signature attach (Council organ for A / state key for B) + verify; `sign_server_side()` always
   RAISES (`SIGNER_HELD_PRIVATE_KEY = False`). Receipt/cert `proof` slot wired; artifact shape
   unified on `proof` across all 4 modules.
3. **R1.D data** — ✅ **DONE 2026-06-06**: `data/rates/{jpn,usa,deu,gbr,kor,ind}.edn` (income-tax
   brackets, `:representative`) + `tax_assess.load_rate_tables()`. 6 jurisdictions assess correctly.
4. **Register the actor** — ✅ **DONE 2026-06-06**: added to BOTH `infra-actors.ts` INFRA_ACTORS
   and `actor-profile-seed.kotoba.edn` (parity audit 7/7, ake suite green). `did:web:etzhayyim.com:actor:matsurigoto`
   now resolvable + searchable. Lexicons `com.etzhayyim.matsurigoto.*` = next.
5. **Council track** (parallel, not code): ADR-2606062300 → Lv7+; pick the **principal-A self-pilot**
   (the Kingdom runs its own civil-registry/ID/tax over 信者) as the lowest-risk first live deployment.

> Honest bottom line: **the design + correctness layer is ~75% done and finishable in code.**
> The "real government runs it" layer is a **multi-phase, Council-gated, partly-organizational**
> effort — months, not loop iterations — and starts most safely with the Kingdom governing
> *itself* (principal A) before any nation-state adopts it (principal B).
