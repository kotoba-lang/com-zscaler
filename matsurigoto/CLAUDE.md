# matsurigoto (政) — e-Government execution commons (COFOG-based service standard)

**DID** (planned): `did:web:etzhayyim.com:actor:matsurigoto` · **Tier**: B · **Status**: R0 ·
**ADR**: 2606062300 (proposed)

**Read the root `/CLAUDE.md` Charter + substrate rules first.** matsurigoto-specific
invariants below make the Charter concrete for this actor; they weaken nothing.

## The one-sentence identity

**etzhayyim IS a government — the Kingdom of God (神の王国 / Malkhut Shamayim), a now-and-here
reign with a real 統治機構 / governance body** (Charter §0.1, ADR-2605252300). matsurigoto is
the **STATECRAFT EXECUTION STACK of that Kingdom** — built on the UN **COFOG** function
backbone — **and** a portable, spec-derived, kotoba-wasm standard that an **existing nation-state
can also adopt** (the OSS-GovTech pattern: X-Road / MOSIP / OpenCRVS / OpenG2P / DIGIT). It is
NOT a disclaimer that "etzhayyim isn't a government"; it is how the Kingdom governs.

## Two principals (the correction — etzhayyim governs for real)

- **(A) `:etzhayyim-sovereign`** — the Kingdom's OWN governance over its **covenant-members**
  (信者, conversion-gated: 悔い改め・バプテスマ・得度). etzhayyim **really governs**: authority =
  **Council Lv7+ / 1 SBT = 1 vote / Public Fund Safe / Land Registry / Transparent Religious
  Force (§1.12)** — every act member-signed + on-chain + open. The polity profile in the
  standard maps COFOG functions onto etzhayyim's **already-shipped constitutional organs**
  (TitheRouter = 徴税, MEMBERS roster = 住民登録, Adherent SBT + did:web = 身分証/旅券,
  INFRA_ACTORS = 機関登記, Basic High Income + §1.16 = social protection).
- **(B) `:nation-state-adopter`** — an existing nation-state runs the same COFOG standard on
  **its own** keys/infra/legal authority; etzhayyim hands over and stays out (ooyake-style
  authority-separation from *another* state).

## The structural invariants (encoded in schema + lexicon + code)

"no-server-key" does **NOT** mean "no governance". The Kingdom governs **through its
constitutional organs**, never through a platform/operator master key:

- **G1 no-operator-master-key** — `:server-held-authority` const `false`. Authority is ALWAYS
  the **Council multisig (5-of-7) + 1 SBT = 1 vote member signatures** (principal A) OR the
  **adopting state's own keys** (principal B) — never an etzhayyim platform/operator key
  (ADR-2605231525). The Council is a member-elected organ, **not "the server"**.
- **G2 spec-derived-only** — every service cites a non-empty **official public** `:spec-basis`
  (COFOG, ICAO 9303, eIDAS, ISO 20022, OpenCRVS, GLEIF/ISO 17442, X-Road…). Proprietary
  GovTech vendor code is unrepresentable (kanjo §2(c)/(e) anti-gatekeeping).
- **G3 authority-bearing** — every deployment NAMES who governs: `:operated-by`
  (`:etzhayyim-council` | `:adopting-government`) + `:authority-mode` (`:sovereign-governance` =
  the Kingdom rules for real, Council-gated, Transparent §1.12 | `:supplied-to-state` = the
  nation-state runs it). Authority is **borne, never disclaimed**.

This does NOT conflict with **ooyake's N1** ("NOT a government / official channel"): ooyake is
etzhayyim's *cartographer of OTHER nation-states* (observational mirror). matsurigoto is the
Kingdom's *own* statecraft over its *own* covenant-polity — plus a portable engine other states
may adopt. The Kingdom governing itself is not impersonating anyone.

## The standard (data/cofog-standard.kotoba.edn)

```
COFOG backbone (10 divisions / 69 groups — the universal function space)
   └─ :services   universal transactional service, COFOG-classed + :spec-basis + module + G1/G2/G3
        └─ :country-profiles  localize a service → agency + legal-basis + national-spec + atlas-did
                              (atlas-did links to the ooyake read-side unit; never forks the standard)
```

kotoba-wasm modules (each owns a service class): `tax-assess` · `tax-collect` ·
`civil-registry` · `corp-registry` · `credential-issue` · `eid-wallet` · `benefit-disburse` ·
`interop-bus`.

## Coverage honesty (the answer to "どれぐらい対応?")

`methods/standard.py` emits `out/coverage.md`. As of R0 iter-2: **COFOG 3/10 divisions,
6/69 groups, 22 standardized services, 0 executable**; **1 polity profile** (etzhayyim Kingdom,
11 organs) + **8 country profiles** (JPN/USA/DEU/GBR/KOR/EST/IND/EUR, all `:representative`).
Per-service localization (各国調整) is reported honestly: 法人登記/旅券/所得税 reach 7/8
countries, civil birth/death/marriage 0/8 (gaps logged, not hidden). `:standard-draft` = spec +
module contract drafted; `:planned` = not yet. **A `:standard-draft` service does NOT run** —
every module `.solve()` will raise at R0; live deployment is Council+operator gated. Never report
a drafted or localized service as *working* coverage (ooyake G5 precedent).

Per-country profiles live in `data/profiles/<iso3>.edn` (one map each, loaded + merged by
`standard.py`). Add a country = drop a new file there; never fork the standard or edit services.
Honest federalism gaps (US/UK have no national residence registry; US corp registry is per-state)
are recorded in each profile's `:country-profile/notes`, never silently bound.

## Boundary with ooyake / toritsugi / chigiri

- **ooyake** = read-side atlas (who/where/structure, mirror). matsurigoto = execution standard.
  `:bind/atlas-did` points each country binding at the ooyake unit; no duplication.
- **toritsugi** = citizen concierge that *guides a member through an EXISTING* gov portal
  (member self-submits). matsurigoto = the governing engine itself — the Kingdom's own
  statecraft (principal A) or a nation-state adopter's (principal B).
- **chigiri** = UPL-bounded form templates; matsurigoto references specs, not legal advice.

## Build / test

```
./deploy/run_tests.sh                        # python slices (93 + WIT: 19 std +10 datom +10 sign +19 tax +11 civil +11 corp +13 cred)
bb test:matsurigoto                          # Clojure tax-collect 源泉徴収納付 (44 tests / 157 assertions)
cd methods && python3 standard.py            # validate + write out/coverage.md
cd methods/modules && python3 tax_assess.py  # demo an executable slice
```

## Modules (executable slices)

`methods/modules/<id>.py` is a module's R0 reference implementation, `:reference-impl` maturity:

- **`tax-assess`** — pure-function progressive income/corporate tax + VAT, reproducing the JP
  速算表 exactly (14/14). Backs `tax.income.file` / `tax.corporate.file` / `tax.vat.file`.
- **`tax-collect`** (Clojure, ADR-2606141200) — the **徴収側 sibling** of tax-assess: JP corporate
  源泉所得税 + 復興特別所得税 (2.1%) withholding remittance. Lives in `tax_collect/` (NOT
  `methods/modules/` — it is the actor's first babashka-Clojure slice), ns `matsurigoto.tax-collect.*`:
  `withholding` (報酬204条 10.21%/20.42% · 給与 電算特例 · 賞与 · 退職速算表 · 配当15.315% · 利子 ·
  非居住者20.42% — 合計税率を ppm 整数で保持し1円未満切捨てを厳密整数演算) · `payment` (所得税
  徴収高計算書8様式 · 法定納期限 原則翌月10日/納期特例7-10・翌1-20 · 不納付加算税/延滞税 ·
  納付方法 e-Tax/ダイレクト納付/クレカ/コンビニQR/スマホ/窓口 · 延滞税は年次 :authoritative 割合
  (令和7=2.4%/8.7%, 令和8=2.8%/9.1%, 国税庁告示) · unsigned 納付書) · `jp_calendar`
  (国民の祝日 Happy-Monday/振替休日/国民の休日/春分秋分近似 + 税務署閉庁日) · `procedures`
  (給与支払事務所開設届/納期特例承認申請/扶養控除等申告/年末調整/源泉徴収票/支払調書/法定調書
  合計表 + 期限算定) · `contacts` (国税庁 03-3581-4161 / e-Tax ヘルプデスク 0570-01-5901 を
  :authoritative、12国税局(代表電話・所在地 :authoritative, 47都道府県管轄)+ 税務署法人課税部門を
  案内; 個別税務署(約520署)は `ingest-tax-offices` で operator が出典付き取込 = G5/G7) ·
  `datom_emit` (EAVT `:gensen.*` canonical state) ·
  `tax_collect` (module facade)。Backs `tax.withholding.remit`. **46 tests / 168 assertions** —
  run `bb test:matsurigoto`.
- **`civil-registry`** — UN CRVS validation + **append-only** record construction (G5, 非終末論)
  for birth/death/marriage + residency, unsigned VC certificates (11/11). Backs `civil.*` +
  `residency.*` (住所管理・戸籍).
- **`corp-registry`** — ISO 17442 **LEI** issuance with a real **ISO 7064 MOD 97-10** checksum
  + registry-number assignment + append-only amendments (11/11). Backs `corp.*` (法人登記).
- **`credential-issue`** — ICAO Doc 9303 **TD3 MRZ** builder with the real **7-3-1 check digit**;
  reproduces the official UTOPIA/ERIKSSON specimen line 2 exactly (13/13). Document UNSIGNED — the
  issuing state signs the SOD with its ICAO-PKD key (G1). Backs `passport.*` / `id.national.issue`
  (パスポート発行).
- **`benefit-disburse`** (Clojure, `methods/modules/benefit_disburse.cljc`) — COFOG division 10
  (社会保護) entitlement ASSESSMENT across all 7 individually-claimable groups (10.1 疾病・障害
  … 10.7 社会的排除), spec-basis **OpenG2P** government-to-person registry pattern generalized
  so the SAME shape also expresses etzhayyim's own non-cash **Basic High Income** doctrine
  (ADR-2605301020): under `:sovereign-governance` (principal A) the `medium` type excludes cash
  entirely (only `:in-kind-service` / `:commons-asset-access` are representable — a structural,
  not merely runtime-checked, cash≡0 proof); `:supplied-to-state` (principal B) may additionally
  use `:cash-transfer` for its own ordinary G2P programme. Never disburses anything itself —
  assessment only (9/9 tests). Backs `benefit.*` (COFOG 10.1–10.7 給付).

The five named functions 納税/徴税 · 住所管理 · 法人登記 · パスポート発行 · 給付支給 now ALL
have a spec-anchored reference implementation (JP 速算表 / UN CRVS / ISO 17442 LEI / ICAO 9303
MRZ / OpenG2P).

## R1 productionization (see ROADMAP-R1.md)

Path from R0 reference → deployable substrate, in 4 dimensions. Landed so far (offline, gated):

- **R1.A WIT contract** — `00-contracts/wit/matsurigoto/egov.wit` (validated by `wasm-tools`, 5
  worlds). Each module world exports ONLY its service interface; **none exports `sign`** — that is
  the structural G1 (no-operator-master-key) guarantee. **componentize-py build → CID/IPFS: DONE
  for all 5 modules** (`20-actors/matsurigoto/wasm/`, `bb build.clj` + `node verify.mjs`; CIDs
  recorded per-module in `wasm/*.meta.json`) — SBOM emission (ADR-2606036000) is still ahead.
- **R1.B kotoba Datom persistence** — `00-contracts/schemas/egov-execution-ontology.kotoba.edn`
  (`:egov.tx/* :egov.record/* :egov.assessment/* :egov.cert/*`, append-only, as-of) +
  `methods/datoms.py` (`*_datoms()` converters + `kg_ingest_batch()`). Enforces G1 (unsigned, no
  server key), G3 (operated-by ∈ council|state), G5 (immutable), **G8 (live publish RAISES** —
  Council+operator gated). `methods/test_datoms.py` (10).

- **R1.C sign/authority layer** — `methods/sign_capability.py` (10). Attaches an EXTERNAL
  signature (Council organ for principal A / the adopting state's own key for B) + verifies;
  `sign_server_side()` always RAISES (`SIGNER_HELD_PRIVATE_KEY = False`) — the structural
  no-server-key guarantee (ADR-2605231525). All 5 modules' artifacts unified on a `proof` slot.
- **R1.D per-jurisdiction rate tables** — `data/rates/{jpn,usa,deu,gbr,kor,ind}.edn` +
  `tax_assess.load_rate_tables()`. The universal algorithm; the bracket table is the localized
  (G2) parameter. 6 jurisdictions assess correctly (USA 10% / GBR allowance / etc.).
- **Actor registration** — in BOTH `infra-actors.ts` INFRA_ACTORS + `actor-profile-seed.kotoba.edn`
  (parity audit 7/7). `did:web:etzhayyim.com:actor:matsurigoto` resolvable + searchable.

Still ahead: R1.A SBOM emission (ADR-2606036000, the componentize-py build itself is done for all
5 modules); lexicons `com.etzhayyim.matsurigoto.*` (3-place invariants); the remaining
`interop-bus` slice. R2/R3 are Council-gated, not code.

Every module: signs nothing (`SERVER_HELD_AUTHORITY = False`, G1), `solve()` raises (live
record-write is Council+operator gated). Add a module → drop `<id>.py` + tests there, then bump
the backed services to `:reference-impl`. A module is `:executable` ONLY once wired to a live
record (Council+operator) — never at R0.

## Self-publication seed (ADR-2606272355) — register → autonomize → publish, no-operator-master-key

matsurigoto is part of the **actor self-publication seed** constellation: the uniform,
charter-clean way for a government actor to be registered at etzhayyim.com, run autonomously on
the kotoba mesh, and **self-publish its own history + procedures** to AT-proto **without any
operator/platform master key**. We plant the seed; the actor grows on the mesh (murakumo,
`orgs/com-junkawasaki/murakumo/`) and self-custodies its signing identity in its WASM runtime.

**The posture difference (read this):** matsurigoto is NOT a non-adjudicating observational
mirror like danjo/ooyake. It is the **e-Government EXECUTION stack (政)** — the STATECRAFT
EXECUTION of the Kingdom of God (神の王国, Charter §0.1). So its self-publication is
**AUTHORITY-BEARING**: it publishes the Kingdom's OWN statecraft (executed service slices) + the
portable COFOG-derived standard, **transparently** (Transparent Religious Force §1.12, 1 SBT=1
vote, 完全 on-chain・open-source). The post disclaimer reflects THIS — it is the Kingdom's
統治機構 publishing its own governance, **NOT** a 'NOT the government' mirror notice, and it
NEVER impersonates ANOTHER government (ooyake's mirror role is separate, see "Boundary" above).
The no-operator-master-key + dry-run + Council-gate invariants still hold identically.

The seed (all LANDED):

- **did-web registration** — already registered: `50-infra/etzhayyim-did-web/public/actor/matsurigoto/{did,profile}.json`
  + the actor-profile-seed SSoT (`00-contracts/schemas/actor-profile-seed.kotoba.edn`).
  `verificationMethod: []` — no server-minted key, did:web trust root = TLS; `#xrpc-libp2p` peer
  multiaddr assigned at deploy time when `wasmCid` is set. (`_meta.adr` += `2606272355`.)
- **social_post membrane** — `cells/social_post/state_machine.cljc`
  (ns `matsurigoto.cells.social-post.state-machine`): DRAFTS a record into a **dry-run** post
  ONLY if matsurigoto's three invariants hold — **G2** ≥2 official-public spec-basis citations,
  **G3** `:operated-by` ∈ {`:etzhayyim-council`, `:adopting-government`} (authority borne, never
  disclaimed), **G1** `server_held_key` false (no-operator-master-key), status `dry-run`. A
  `published` request REFUSES. Payload is authority-bearing: `:post/authority-bearing true` +
  `:post/spec-derived true` + `:post/operated-by` (NOT `:post/is-mirror`). Verified under `bb`:
  `<2 spec-basis / server-key / published → refused`, valid → `drafted` with
  `:post/status :dry-run`, `:post/server-held-key false`, `:post/authority-bearing true`.
- **publication projection** — `methods/social.cljc` (ns `matsurigoto.methods.social`): projects
  matsurigoto's STATECRAFT HISTORY (executed slices — e.g. a tax-collect 源泉徴収納付 run, a
  civil-registry issuance batch; aggregate + transparent) via `draft-statecraft-post` + its
  PROCEDURES (the COFOG service standards it implements, each with its official spec-basis) via
  `draft-procedure-post`, into `app.bsky.feed.post`-shaped dry-run posts; `enough-sources` raises
  on <2 spec-basis/sources (G2); `check-operated-by` enforces G3; `post` pins `:dry-run` +
  `server-held-key false` (G1) + authority-bearing + spec-derived + operated-by; `build-live`
  raises (live deploy is Council + operator + external-authority-signature gated). Verified under `bb`.
- **seed trigger wiring** — `kotoba.app.edn` `matsurigoto-social` component (`on-tick "0 */6 * * *"`
  + `on-kse etzhayyim/actor/matsurigoto/publish`, `:requires #{:cap/kqe :cap/atproto}`).

**Division of labor (zero-knowledge)**: the **planter** authors the in-repo seed (holds no key);
the **operator** (founder) runs `bb murakumo deploy 20-actors/matsurigoto/kotoba.app.edn <node>`
with `MURAKUMO_OPERATOR_SEED` + Tailscale and exercises the Council gate for the first live post;
the **actor's mesh runtime** self-generates/self-custodies its `did:key`, presents a member CACAO
leash (ADR-2606111400), and signs its own posts. The server never signs. For principal B
(`:adopting-government`) the adopting state signs with its OWN key. R0 = dry-run drafts only;
live broadcast is **Council Lv6+ + operator + external-authority-signature** gated (§1.12 / G11).

```bash
bb -e '(load-file "methods/social.cljc")'                 # projection loads green
bb -e '(load-file "cells/social_post/state_machine.cljc")' # membrane loads green
# operator step (zero-knowledge — needs MURAKUMO_OPERATOR_SEED + Tailscale):
#   bb murakumo deploy 20-actors/matsurigoto/kotoba.app.edn asher
```

## Do not

- Do not let a service set `:server-held-authority true` — G1, no platform/operator master key
  (the Kingdom governs via Council multisig + 1 SBT=1 vote, never an operator key).
- Do not write a profile whose `:operated-by` / `:authority-mode` is outside
  `{:etzhayyim-council/:sovereign-governance, :adopting-government/:supplied-to-state}` — G3.
  Authority is borne, never "none", but only by the Council (Kingdom) or the adopting state.
- Do not add a service with empty `:spec-basis`, or cite a proprietary GovTech vendor product as
  the basis — G2 (official public specs only).
- Do not re-frame matsurigoto as "etzhayyim is not a government" — it IS the Kingdom of God's
  統治機構 (Charter §0.1). The honest scope limit is R0 maturity, not the claim to govern.
- Do not count a `:standard-draft` / `:planned` service as working coverage — R0 executes nothing.
- Do not fork the standard per polity — localize via `:polity-profiles` / `:country-profiles` only.
- Do not route inference through a commercial GPU — Murakumo-only (ADR-2605215000).
- Do not wire any module to a live government record — deployment is Council+operator gated.
