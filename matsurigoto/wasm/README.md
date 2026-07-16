# matsurigoto 政 — egov WASM components (componentize-py, R1.A)

The matsurigoto egov service modules built as **WASI Component-Model components** with
**componentize-py**, per ADR-2606062300 R1.A and the `00-contracts/wit/matsurigoto/egov.wit`
contract. Same componentize-py path as `watatsuna`'s WASM actor (ADR-2606014600) — see that
directory's README for the honest size/exec-tier boundary this shares.

The pure business logic these apps embed is a faithful port of
`methods/modules/{tax_assess,civil_registry,corp_registry,credential_issue,benefit_disburse}.cljc`
(the R0 reference implementations), reshaped to the simplified record types `egov.wit` declares.
Nothing here signs anything (G1) — every module returns an `unsigned-artifact` (`proof =
none`); the governing organ (Council 5-of-7 Safe for principal A / the adopting state's own
key for principal B) signs externally per ADR-2605231525.

## Files

- `tax_assess_app.py` / `civil_registry_app.py` / `corp_registry_app.py` /
  `credential_issue_app.py` / `benefit_disburse_app.py` — one app per WIT world; each exports
  a class named after its WIT interface (`Tax` / `Civil` / `Corp` / `Credential` / `Benefit`)
  that componentize-py binds to the world's export.
- `build.sh` — componentize-py build + jco transpile + IPFS CID, for all 5 modules.
- `verify.mjs` — headless: runs the transpiled components and checks each against its public
  spec anchor (JP 速算表 bracket arithmetic, ICAO 9303 UTOPIA/ERIKSSON specimen exact match,
  ISO 7064 MOD 97-10 LEI self-issuance + tamper rejection, UN CRVS validation rules, and
  `benefit-disburse`'s structural cash≡0 invariant under `sovereign-governance`).
- `{tax-assess,civil-registry,corp-registry,credential-issue,benefit-disburse}.meta.json` —
  recorded CID + size + tier per module (committed; the `.wasm` binaries and `transpiled-*/`
  are gitignored — rebuild via `build.sh`).

## Build & verify

```bash
./build.sh          # -> 5x <world>.wasm + transpiled-<world>/ + CID, per module
node verify.mjs      # asserts all 5 against their reference specs
```

## The honest boundary this shares with watatsuna (ADR-2606014600)

Each module bundles CPython (~17.5 MB), so each is a **multi-block dag-pb CID**, not a raw
single-block CID:

| | tsumugi (Rust) | matsurigoto modules (componentize-py) |
|---|---|---|
| size | 23.5 KB | ~17.5 MB each |
| CID | raw single-block (`bafkrei…`) | dag-pb multi-block (`bafybei…`) |
| apex `/ipfs` gateway verify | ✅ (sha256→CID) | ❌ needs full UnixFS verifier |
| browser-local (ameno T1) | ✅ | ❌ |
| exec tier | **T1 browser-local** + mesh | **T2 donated-mesh** (full IPFS node) |

## Honest scope (R1.A)

Pure compute only — every export is a pure function; none of the five modules exports a
`sign` function (the structural G1 no-operator-master-key guarantee, encoded in the WIT
surface itself: `unsigned-artifact.proof` is always `none`). **Live record-write and
signing are still host-side, Council + operator gated** (principal A: Council Lv7+;
principal B: the adopting state's own authority) — this directory only advances R1.A
(WASM-ification); R1.B (kotoba Datom persistence), R1.C (the external signing/authority
layer), and R1.D (full per-jurisdiction rate tables) are separately tracked in
`ROADMAP-R1.md`. The `.wasm` binaries + `transpiled-*/` are gitignored (rebuild via
`build.sh`); live IPFS pinning + mesh execution remain operator-gated.
