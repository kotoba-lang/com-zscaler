# 20-actors/iryo — CLAUDE.md

## Identity

- **Name**: iryo (医療 — レセプト / 医療保険請求 engine)
- **DID**: `did:web:iryo.etzhayyim.com`
- **Role**: Japan 国内 医療保険 **レセプト計算 + レセ電(レセプト電算)生成 + FHIR claim** engine.
  The **billing counterpart** to the `karute` 電子カルテ (EMR): karute holds the encrypted
  clinical record; iryo computes the レセプト FROM it and emits the online-claim stream.
- **ADR**: ADR-2606074000 (R0, 2026-06-07)
- **Tier**: Tier-B. L4 Care tier sibling of iyashi / yakushi / mitate / karute.
- **Form**: 任意団体 internal billing-tool substrate — a TOOL a licensed member 保険医療機関
  self-operates, NOT a state-licensed billing entity.

## Why iryo exists (resolving the iyashi G13 boundary)

`iyashi` (clinical care provider) carries **G13: NO insurance billing** because the
religious-corp itself is constitutionally NOT registered under 宗教法人法, and direct
insurance-billing inflow to the corp is excluded. The `karute` EMR therefore delegated
保険請求 to an (unbuilt) `iryo` vendor counterpart. iryo is that counterpart, built
charter-clean:

- the **licensed 保険医療機関 (a member clinic) is the billing PRINCIPAL** (G1);
- iryo is **open substrate / a tool** the clinic self-operates — it never originates a
  claim on its own key and the religious-corp takes no insurance-billing inflow (G7);
- this is the pattern of `warifu` (open card, member settles) / `toritsugi` (procedure
  concierge, default self-submit) / `chigiri` (legal-procedure substrate, not a law firm).

## Architecture

```
karute (encrypted PHI, codes-only hand-off: DID/AT-URI + consentCapabilityUri)
        │
        ▼
  ingest-billing 受理境界 (PHI-free allow-list gate + consent.capability structural gate)
        │
        ▼
  rezept   点数計算 — 区分集計 / 一部負担金 / 高額療養費
        │
        ▼
  receden  レセ電 — IR/RE/HO/KO/SY/SI/IY/TO + 件数照合 (PHI-free default)
        │
  validate 算定整合性チェック (non-adjudicating)
        │
        ▼ operator-gated 送信 (no-server-key)
  審査支払機関 (社会保険診療報酬支払基金 / 国保連)
```

4 Pregel cells (`ingest-billing` / `rezept` / `receden` / `validate`), all pure-stdlib + pywasm-ready.

### karute -> iryo hand-off boundary (`methods/handoff.cljc`, ADR-2605231401 Pattern 2)

karute's `requestIryoBilling` forwards to iryo's `ingestKaruteEncounterForBilling` via
`agent.invoke` (karute/actor-manifest.jsonld forwardToIryo step) — until this landed, iryo
had no receiving implementation at all (karute/MATURITY.md #11). `handoff/handle-ingest`:

1. **PHI-free intake gate (G2)** — the wire request may only carry the exact fields karute
   sends (`patientDid`/`encounterDid`/`facilityDid`/`serviceRequestUris`/
   `medicationRequestUris`/`consentCapabilityUri`); any other key fails closed. DIDs must be
   `did:`-prefixed, URIs `at://`-prefixed, and every string leaf must be ASCII-only (a
   smuggled Japanese name/free-text field is non-ASCII and gets rejected even if it happens
   to pass a prefix check).
2. **Consent-capability structural gate (G1/G7)** — given the already-resolved
   `com.etzhayyim.consent.capability` record, checks `purpose == "insurance-billing"`,
   `granteeDid == iryo's own DID`, `granterDid == request patientDid`, not revoked, not
   expired, and `scope`/`resourceUris` cover the requested resources.
3. **Result discipline (G3/G5)** — success only ever yields `iryoStatus:"pending"` (drafted
   into iryo's own intake queue, no online submission). Any gate failure yields
   `iryoStatus:"needs-info"` — never `"accepted"`/`"rejected"`, which are the
   審査支払機関's adjudication vocabulary, not iryo's.

**ingest-billing -> rezept auto-wiring (`methods/agent.cljc` `handle-ingest-billing`,
karute/MATURITY.md #11(c)).** When the intake gates pass AND the caller ALSO supplies an
already-resolved `"encounter"` (the same shape `handle-rezept` expects — codes/counts only,
no PHI), `handle-ingest-billing` automatically calls `handle-rezept` and attaches the result
under `"rezeptPreview"`; `iryoStatus` stays `"pending"` regardless (a draft preview, not an
adjudication). A malformed/unresolvable encounter (e.g. a code missing from the loaded
master) does not flip the already-accepted intake to a gate failure — it surfaces as
`"rezeptPreviewError"` instead, leaving `ack`/`iryoStatus` untouched. Without an `"encounter"`
the response is byte-for-byte unchanged (a bare hand-off gate check) — this is additive, not a
new gate, and `handoff.cljc` itself is unmodified.

**Ed25519 signature verification (`signature-gate`, karute/MATURITY.md #8).** `handoff.cljc`
now verifies the capability's Ed25519 signature — JDK `java.security` only, no third-party
crypto dep (the same approach already proven green in
`20-actors/kaiyaku/tools/issue_capability.cljc`). Verification is OPT-IN via an additional
already-resolved input, `"granterPublicKey"` (base64 raw 32-byte Ed25519 public key) —
the SAME already-resolved-input contract as `capability` itself. When supplied, a
missing/malformed signature or one that fails to verify (wrong key or a payload tampered
after signing) is rejected (`iryoStatus:"needs-info"`). When NOT supplied, this gate no-ops
and behavior is byte-for-byte unchanged (backward compatible with every pre-existing caller).
**Still out of scope**: obtaining `granterPublicKey` by resolving granterDid's DID document.
In this bridge `patientDid` is a rotating pseudonym did:web
(`iryo.methods.karte/rotating-pseudonym-did`, `did:web:patient.iryo.etzhayyim.com:<hash>`),
not a self-describing did:key, so obtaining its verification material means an HTTPS did:web
document fetch — network I/O, cross-repo, the same class of problem as PDS resolution below.
Also still open: byte-parity between `handoff/canonicalize-capability-payload` (this
namespace's own canonicalization) and the eventual real granter-side signer
(`@etzhayyim/sdk.signConsentCapability`, ADR-2605231401 Phase 2 — still a stub upstream, so
no reference bytes exist yet to check against).

**consentCapabilityUri structural self-consistency (`parse-at-uri`, this iteration).**
`capability-gate` now also verifies that `consentCapabilityUri` itself (a) parses as a
well-formed `at://<did>/<collection>/<rkey>` AT-URI, (b) its collection segment is exactly
`com.etzhayyim.consent.capability` (the canonical NSID — the lexicon states the record "is
stored at com.etzhayyim.consent.capability in the granter's PDS", ADR-2605231401), and (c)
its did segment equals the already-resolved `capability["granterDid"]`. This is a pure
string-parse — **no network I/O, no new dependency** — investigated and confirmed safe this
iteration (see below): it closes the STRUCTURAL half of PDS/AT-URI resolution (catching a
caller-supplied capability record that is inconsistent with the very URI the wire request
names), while the actual byte-fetch from a real PDS remains out of scope.

**Investigated this iteration: is `@etzhayyim/sdk` PDS resolution reachable?** Yes and no.
`@etzhayyim/sdk/pds` is a real (non-stub) re-export shim onto `@etzhayyim/atproto-client`
(= `kotoba-lang/atproto-client`, a separate GitHub repo), and that repo's `.cljc`
`resolve-pds`/`get-record` are genuinely testable (transport is host-injected `IHttp`, so a
fake transport can exercise them without real network in tests). But wiring it into iryo
would mean (1) a NEW cross-repo runtime dependency from an etzhayyim/root bb actor onto a
`kotoba-lang` package — iryo currently has no deps.edn / git-dep mechanism at all — and (2)
production code that performs a REAL HTTPS did:web fetch, which is a materially bigger and
riskier change than this repo's established "verify GIVEN an already-resolved input" pattern
(the same pattern `signature-gate` already uses for the public key). Given that, only the
network-free structural half was implemented this cycle; the actual fetch stays explicitly
out of scope below.

**Explicitly out of scope** (tracked separately, do not conflate): resolving a capability's
granter public key FROM granterDid (did:web document fetch, cross-repo network I/O) and
actually FETCHING `consentCapabilityUri`'s bytes from a real PDS (`@etzhayyim/sdk` /
`kotoba-lang/atproto-client`, cross-repo, real HTTPS I/O, no wiring exists in iryo today) —
the caller is expected to have already resolved the capability record (and, for the
rezept-preview wiring above, the encounter payload, and now optionally the granter public
key for signature verification) before invoking this cell.

## 全件対応 (すべての診療行為・薬剤・特定器材・病名)

The official 厚労省/支払基金 master is tens of thousands of copyrighted rows, so iryo does
NOT embed it — it **ingests** it (`methods/master_loader.cljc`), making every code resolvable:

- `load_normalized(dir)` — iryo-defined normalized CSV (shinryo/iyaku/tokutei/shobyo/
  shushokugo/comment), fully tested.
- `load_mhlw_*(path, ColMap)` — raw 厚労省 基本マスター CSV, column positions given by an
  overridable `ColMap` (defaults documented as approximate; verify against the current
  記録条件仕様 before production).
- `Masters.merge()` — seed + official master compose (official overrides seed).

**診療区分カバレッジ (全カテゴリ)**: 初診/再診/医学管理/在宅/投薬(内服・屯服・外用)/
注射(皮下・静注・点滴)/処置/手術/麻酔/検査/病理/画像診断/その他/入院。年齢区分
(乳幼児/成人/前期高齢/後期高齢)からの負担割合導出、公費負担医療(生活保護/難病/自立支援 …)の
重ね合わせ + 負担区分、高額療養費 全区分(70歳未満 ア〜オ / 70歳以上 現役並み・一般・低所得、
外来個人/世帯上限)、入院時食事療養 標準負担額。レセ電は IR/RE/TY/HO/KO/SY/SI/IY/TO/CO/SJ。

## 計算ルール (exact, tested — see methods/test_rezept.cljc)

- **1点 = 10円** (master-driven `tensu_tanka_yen`)
- **薬剤料 五捨五超四入**: 薬価 ≤15円→1点; >15円→ 薬価/10 を端数「五捨五超」(0.5以下切捨,
  0.5超切上); 内服は投与日数を乗じる
- **一部負担金 端数処理**: 総医療費 × 負担割合 を 10円未満四捨五入 (5円以上切上)
- **高額療養費 (70歳未満 月額)**: ア 252,600+(医療費-842,000)×1% / イ 167,400+(-558,000)×1%
  / ウ 80,100+(-267,000)×1% / エ 57,600 / オ 35,400

Point values are **always resolved through a loaded master** (`masters.Masters`); the
engine never hard-codes a tariff (G4). The bundled `data/seed_masters.json` is a
**representative seed for verification only** — production loads the official 厚労省 /
支払基金 master.

## PHI Discipline (CRITICAL — structural, not policy)

1. **Patient identity = rotating pseudonym DID** (`karte.rotating_pseudonym_did`, monthly
   period) — never a stable MRN; the public-meta projection is not cross-period correlatable
   (ADR-2605181200).
2. **`Karte.public_meta()` is codes-only**; `Karte.assert_no_phi()` rejects any smuggled
   plaintext PHI field (`name/dob/address/soap_*/...`). `SoapNote` refuses construction
   without an `encrypted_cid` (ADR-2605181100).
3. **レセ電 PHI (氏名/生年月日) enters only via the operator `phi` callback at submission**,
   transmitted over the closed オンライン請求 IP-VPN to the 審査支払機関 — never the public
   substrate. Default `build_receden` output is PHI-free (verified in test_receden.py).

## Gates (7) — do NOT weaken

- **G1 member-principal** — licensed 保険医療機関 is the billing PRINCIPAL; iryo originates no claim on its own key
- **G2 PHI-encrypted** — 氏名/生年月日/SOAP free-text injected only at submission, never public substrate
- **G3 no-server-key** — online 請求(送信) operator-gated; iryo computes + drafts only (default `:draft`)
- **G4 master-honest** — points resolved through a loaded master; seed is representative; no hard-coded tariff
- **G5 non-adjudicating** — validation SURFACES discrepancies; 審査支払機関 + clinic decide; iryo approves/denies nothing
- **G6 Murakumo-only inference** — narration via kotoba `llm` host binding (no external LLM)
- **G7 no-religious-corp-inflow** — iryo is a tool the member clinic self-operates; the corp takes no insurance-billing inflow (preserves iyashi G13)

## Non-goals

- NOT a state-licensed billing entity (the member clinic carries the license)
- NOT an adjudicator (does not 査定/返戻 — only observes; the 審査支払機関 decides)
- NOT a clinical record store (that is `karute`; iryo consumes a codes-only projection)
- NOT a proprietary レセコン / EHR-billing vendor integrator (ORCA-proprietary / Epic / Cerner)
- NOT a fiat/inflow path into the religious-corp

## Build & Verify

```bash
cd 20-actors/iryo
./run_tests.sh           # 12 cljc suites green (masters/master-loader/insurance/kogaku/rezept/karte/handoff/receden/coverage/e2e/datoms/kotoba)
```

The engine is fully ported to clojure-on-babashka (`methods/*.cljc`) over the kotoba
Datom log; the legacy `py/` twin was pruned once the cljc suite reached parity. The
representative master seed lives at `data/seed_masters.json` (loaded by `methods/masters.cljc`).

## Cross-actor

- **karute** (電子カルテ EMR) — hands off the codes-only billable encounter projection via the `ingest-billing` boundary (`methods/handoff.cljc`); PHI stays in karute's envelope
- **iyashi** (clinical care provider) — L4 sibling; iryo is the billing tool iyashi's G13 deliberately excludes from the corp
- **yakushi** (pharma) — 医薬品マスタ / 薬価 alignment for 投薬 算定
- **toritate** (accounting + audit) — donation/grant accounting (NOT insurance inflow); reads claim totals for audit
- **chigiri** (legal-procedure / consent) — consent capability for PHI access; member-onboarding

## Related Files

- `/20-actors/iryo/manifest.edn` · `/20-actors/iryo/README.md`
- `/90-docs/adr/2606074000-iryo-rezept-claims-engine-charter.md` — Master ADR
- `/90-docs/adr/2605231100-karute-emr-phase1.md` — karute EMR (handoff source)
- `/90-docs/adr/2605181100-mst-encrypted-records-signal-keywrap.md` — PHI envelope
- `/90-docs/adr/2605263000-iyashi-clinical-care-provider-tier-b-actor-r0.md` — L4 / G13 boundary
- `/CHARTER-RIDER.md` — license + Rider
