# chigiri (契) — Maturity Ledger

`/loop` 進捗台帳。各イテレーションで成熟度を上げ、ここに記録する。honest framing:
できていないことは「未」と明記する。

- Actor: `did:web:chigiri.etzhayyim.com` · ADR-2605262700 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 · 提出/代理なし ·
  **UPL strictly prohibited — chigiri renders NO legal advice (G8/G14)** ·
  本 registry は REFERRAL routing to licensed human counsel / legal-aid orgs のみ ·
  Zero compensation · Public-Fund-routed · Murakumo-only(G11) ·
  G8 非捏造(honest coverage over inflated counts) · コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605262700 (master) + 2605262800 (legal corpus) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | 9 Lexicon skeletons (`com.etzhayyim.chigiri.*`) | ✅ | init |
| 4 | 12 cell paths reserved (`kotodama.cells.chigiri_*`, import時 RuntimeError) | ✅ | init |
| 5 | **legal-aid REFERRAL registry seed (worldwide, unverified-seed)** | ✅ | **iter-1** |
| 6 | registry 更新 (root CLAUDE.md / adr README / deps.toml) | 未 | — |
| 7 | cell ↔ manifest 整合 invariants test | 未 | — |
| 8 | 憲法ゲート機械検証 node guard (UPL/G8/G14 fail-closed) | 未 | — |
| 9 | hanrei / bunken / toritsugi cross-actor boundary 整合 | 未 | — |
| 10 | referral seed の authority・legalBasis・provenance 精査 + verification ワークフロー | 未 | — |
| 11 | kotoba KG seed への chigiri エンティティ追加 | 未(node-local 見込) | — |
| 12 | lexicon validator green の固定化 | 未 | — |
| 13 | 各 cell dir の README parity | 未 | — |
| 14 | murakumo fleet.toml への chigiri cell placement | R1延期見込 | — |
| 15 | **worldwide legal-aid coverage (JP/US/EU/UK-CW)** | ✅ | **iter-1** |

## イテレーション記録

### iter-1 (2026-06-02)
**上げた項目: #5 + #15 — worldwide legal-aid REFERRAL registry seed。**
`20-actors/chigiri/registry/legal-aid.seed.json` を新設(actor 初の registry file)。
sibling の `toritsugi/registry/procedures.seed.json` と **完全に同形**の JSON shape:
top-level `$schema=com.etzhayyim.chigiri.legalAidReferral` + R0 SEED の `_comment`
(unverified-seed semantics + G8/G14 caveat + UPL/zero-compensation/Public-Fund 境界) +
`freshnessWindowDays=180` + `referrals[]`(id フィールドは `referralId`)。

**28 entries**(全 unverified-seed・lastVerified=2026-06-02T00:00:00Z・provenance URL・
language code・UPL 境界 caveat を notes に含む)を **4 worldwide bloc** で投入:
- **JP (8)**: 法テラス / 日弁連 法律相談センター / 司法書士総合相談センター /
  総合労働相談コーナー / 法務局 人権相談(みんなの人権110番) / 消費生活センター 188 /
  自治体 無料法律相談 — national + 自治体(municipality)variation を明示カバー。
- **US (7)**: LSC grantee locator / LawHelp.org / ABA Free Legal Answers /
  CA Self-Help Centers / NY CourtHelp A2J / TX Access to Justice / 連邦地裁 Pro Se clinics
  — federal + 代表 state variation(CA/NY/TX)。
- **EU (8)**: e-Justice Portal / DE Beratungshilfe / DE Prozesskostenhilfe /
  FR aide juridictionnelle / FR point-justice / GDPR Art.77 DPA complaint /
  ECC-Net consumer — EU-level instruments + Germany & France。
- **UK-CW (7)**: LAA Civil Legal Aid (E&W) / Citizens Advice / Legal Aid Ontario /
  Legal Aid NSW + LawAccess / NALSA (India) / Legal Aid Bureau (Singapore) /
  Ontario FLIC court self-help — UK + Canada/Australia/India/Singapore。

正規化: 各 researched entry を chigiri schema に map(id→referralId、
authority/channel/legalBasis/provenance/notes を保持、bloc を正規化、language code 付与)。
全 notes に必須境界文 "UPL strictly prohibited — chigiri renders NO legal advice. This
registry is REFERRAL routing to licensed human counsel / legal-aid orgs only. Zero
compensation. Public-Fund-routed." を挿入。verificationStatus は全件 unverified-seed。
referralId で dedup(28 unique、衝突なし)。捏造 entry はゼロ(G8: honest coverage)。

**検証**: python で JSON valid・28 件 unique referralId・必須12フィールド全件存在・
全件 unverified-seed + lastVerified 固定 + provenance=http(s) + notes に UPL/Zero
compensation/Public-Fund-routed 含有を assert(全 pass)。

**注(honest / 未)**:
- 本 entry は **全件 unverified-seed**。G14 により live 使用前に human/Council verification +
  freshness 再確認が必須。authority/legalBasis/channel/手数料/閾値/URL は drift 前提の
  wayfinding scaffold で、authoritative ではない。
- confidence=medium のものあり(US-NY admin-order basis / US-TX Gov't Code 節番号 /
  US-federal per-district clinic 偏在 / JP 自治体無料相談[national URL なし] /
  FR point-justice locator path drift / EU ECC-Net charter 根拠の間接性 /
  Ontario FLIC example-only)— notes に理由を明記。
- `com.etzhayyim.chigiri.legalAidReferral` の **Lexicon schema は未作成**($schema は
  論理名のみ。実 Lexicon JSON は #3 の 9 skeleton には未含)。
- #6(root CLAUDE.md / adr README / deps.toml への registry 登録)・#10(referral 精査 +
  VERIFICATION ワークフロー)・#7-#9・#12-#14 は未着手。
- コミット/add は未実施(working-tree edits only)。

### iter-2 (2026-06-02)
**上げた項目: #8(部分) — fail-closed registry invariants test 追加。** `70-tools/scripts/audit/test_chigiri_registry_seed.py` を新設(R0-safe: test-only・network-free・cell 非実行)。7 invariants を pin: (1) JSON valid + `referrals` 非空, (2) `referralId` unique(重複 fail-closed), (3) 全件 `verificationStatus=unverified-seed`(G14), (4) 全件 provenance https URL + lastVerified, (5) jurisdiction 存在 + >=5 distinct(worldwide coverage, JP-only 退行 guard), (6) per-entry notes 非空 + registry-wide UPL/referral-only/no-advice/zero-compensation 境界 regime 参照, (7) top-level `freshnessWindowDays` integer。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest ... -q` → **7 passed**(green)。注: 既存環境の langsmith/pydantic plugin-autoload 非互換は本 test と無関係(test に外部依存なし)。

### iter-3 (2026-06-02)
**Lexicon reconciliation — CREATED `00-contracts/lexicons/com/etzhayyim/chigiri/legalAidReferral.json`** (`com.etzhayyim.chigiri.legalAidReferral`, record/key=tid) typing the legal-aid REFERRAL registry seed; all 13 seed fields (referralId/title/jurisdiction/bloc/authority/channel/legalBasis/language/provenance/confidence/lastVerified/verificationStatus/notes) typed, referralId as record-key concept; UPL(G14)/zero-compensation(G15)/Public-Fund-routed(G8)/informational-mirror boundaries encoded in description, NO consideration property representable. Validators green: `lexicon-primary-types` OK · `nsid-lexicon-exists` OK · `no-legal-aid-consideration` OK (exit 0, CRITICAL invariant intact). `lexicon-const-name-collision-check` fails on a PRE-EXISTING unrelated collision (`com.etzhayyim.apps.ipaddress.analyzeIp`) — reproduces identically with this file absent; NOT caused by this change. (working-tree edits only, no git add/commit.)

### iter-4 (2026-06-02)
**Long-tail worldwide deepening of #5/#15 — merged 26 new legal-aid REFERRAL entries** into `registry/legal-aid.seed.json` (all 26 net-new, 0 dedup drops), across 4 new buckets: **EU-REST (8** — swe/nld/irl/ita/esp/fin/nor/bel), **asia-rest (7** — phl/hkg/twn/idn/mys/tha/vnm), **AMERICAS-REST (5** — arg/chl/col/per/can-Quebec), **MEA-OCEANIA (8** — zaf/ken/nga/nzl/isr/are/egy). Normalized to the actor's exact schema (id→referralId, bucket→bloc, channel/authority/legalBasis/provenance/notes preserved, ISO language code added, channel_note folded into notes); all 26 ship verificationStatus=unverified-seed + lastVerified=2026-06-02T00:00:00Z + https provenance + mandatory UPL boundary caveat in notes. Registry now **55 entries / 36 distinct jurisdictions** (was 29 / 10). Invariants test threshold raised from `>= 5` to `>= 12` distinct jurisdictions (actual 36 ≫ 12) — `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest 70-tools/scripts/audit/test_chigiri_registry_seed.py -q` → **7 passed** (green). One source provenance (Colombia secretariasenado.gov.co) was http upstream → normalized to https same-host/path (noted in entry). G8 honest coverage: zero fabricated entries. (working-tree edits only, no git add/commit.)

**2026-06-02 R1 referral resolver core**: `kotodama.cells.chigiri_legal_aid_clinic/referral_match.py` — 管轄→法的扶助 referral の純 registry クエリ(confidence→title ソート、未知管轄は空配列)。**適格/means-test 判定はしない**(income 閾値は管轄固有データ=捏造回避)、routing のみ。UPL/無償性を担保。`test_referral_match.py` green。注: 本 cell は唯一の稼働中実 cell(import gate ではなく `_assert_no_advice`/zero-comp ガードで G14/G15 強制); resolver はその上の純関数。

**2026-06-02 #10(部分) — VERIFICATION ワークフロー doc authored**: `20-actors/chigiri/registry/VERIFICATION.md` を新設(toritsugi `registry/VERIFICATION.md` parity)。3-tier(unverified-seed→maintainer-verified→council-verified、**代行 tier なし**=top tier は council-verified referral)・13フィールド per-field checklist(実 referralId..notes)・per-jurisdiction WORLDWIDE PROVENANCE 公式ドメイン規則(.go.jp/.gov/.gouv.fr/.gov.uk/europa.eu/.gob.*/.go.kr…、blog/aggregator 不可、fail-closed)・freshnessWindowDays=180 staleness・UPL no-advice/referral-only/zero-consideration 境界 re-check・bona-fide legal-aid body(非 for-profit solicitation)+ no-legal-aid-consideration invariant 強調・machine-enforced floor(`test_chigiri_registry_seed.py` 7 invariants)を記載。honest(G8): 全件 unverified-seed のまま・maintainer DID 未登録・本 doc は process spec のみ。working-tree edits only(git add/commit なし)。

### iter-5 (2026-06-05, loop)
**カバレッジ向上 — 主要欠落 9 法域の実在公的法律扶助機関を追加。** `registry/legal-aid.seed.json` 55 → 64 referrals、distinct jurisdictions **36 → 45**(toritsugi の 41 法域を超過しつつ整合)。追加: **mex**(Instituto Federal de Defensoría Pública / Ley Federal de Defensoría Pública)・**bra**(Defensoria Pública da União / CF Art.134 + LC 80/1994)・**kor**(대한법률구조공단 KLAC / 법률구조법)・**pol**(Nieodpłatna pomoc prawna / Ustawa 2015)・**chn**(法律援助 MoJ / 法律援助法 2021)・**dnk**(offentlig retshjælp + fri proces / Retsplejeloven Part 31)・**prt**(apoio judiciário / Lei 34/2004)・**grc**(νομική βοήθεια / Ν.3226/2004)・**aut**(Verfahrenshilfe / ZPO §§63-73)。全件 **実在の公的法律扶助 body + 確信できる legalBasis**(G8 捏造ゼロ; 不確実な閾値・地方経路は notes で guide-time resolve)、verificationStatus=unverified-seed(G14)、https provenance、各 notes に canonical UPL/no-legal-advice/zero-compensation 境界文 + DRIFT warning。confidence: mex/bra/kor/pol/prt/aut=high、chn/dnk/grc=medium(national portal / 地方経路 indirection を notes 明記)。`test_chigiri_registry_seed.py` **7/7 green**(distinct≥12 invariant: 45≫12)。working-tree edits only。

### iter-6 (2026-06-05, loop, cross-actor parity)
toritsugi(行政手続き)がカバーするが chigiri に欠けていた 2 法域 **che/sau** を実在ルートで追加。**che**: unentgeltliche Rechtspflege / assistance judiciaire(ZPO Art.117-123 + BV Art.29 Abs.3; cantonal court-granted, 自由 advice は canton 別)。**sau**: 刑事の court-appointed defence(نظام الإجراءات الجزائية)+ Saudi Bar Association pro-bono — **G8 honest 明記: 国家による民事 legal aid は限定的**で、信頼できる経路は刑事国選 + 弁護士会 pro-bono/charitable clinic。全件 unverified-seed + https + canonical UPL/no-legal-advice 境界 + confidence=medium(地方/制度差を notes 明記)。referrals 64 → 66、distinct jurisdictions **45 → 47**。`test_chigiri_registry_seed.py` **7/7 green**。結果: chigiri と toritsugi が**完全に同一の 47 法域**をカバー(parity 達成)。

### iter-7 (2026-06-05, loop, parity test)
**2026-06-05 cross-actor parity を fail-closed テストで固定 (loop iter, 成熟度)**: 新設 `70-tools/scripts/audit/test_gov_legal_coverage_parity.py`(R0-safe: test-only/network-free/cell 非実行)が、単一アクター suite では見えない**横断不変条件**を 3 つ pin: (1) 両 registry の jurisdiction コードは ISO-3166-1 alpha-3 lowercase または文書化済み擬似法域 `eu-wide` のみ(uk/USA/usa2 等のタイポ → coverage 断片化を fail-closed 検出; 負例で検証済)。(2) coverage floor — 各 registry ≥47 distinct 法域(2026-06-05 到達; 回帰=shrink で fail)。(3) parity floor — 両アクターの共有法域 ≥45(intersection は除去時のみ縮むため将来の片側 growth に対し非 brittle)。現値 toritsugi=47/chigiri=47/shared=47。関連 4 suite **27/27 green**。

### iter-8 (2026-06-05, loop, parity 保持拡大)
**2026-06-05 主要欠落経済圏を両アクター同時追加 (loop iter, parity 保持拡大)**: 旅券当局＋法律扶助機関の双方を確信できる 5 か国を toritsugi/chigiri 双方へ追加し parity を保ったまま **47 → 52 法域**へ拡大: 🇹🇷tur(NVI/e-Devlet ‖ Türkiye Barolar Birliği adli yardım, Avukatlık Kanunu 176-181 + CMK)・🇷🇺rus(МВД/Gosuslugi ‖ FZ-324/2011 бесплатная юридическая помощь)・🇵🇰pak(DGIP ‖ Legal Aid and Justice Authority Act 2020)・🇧🇩bgd(e-Passport ‖ NLASO, Legal Aid Services Act 2000)・🇺🇦ukr(ДМС/Diia ‖ legalaid.gov.ua, Law on Free Legal Aid 2011)。全件実在機関 + 確信できる legalBasis(G8 捏造ゼロ; 不確実な閾値・地方経路は notes で guide-time resolve・confidence で明示)、unverified-seed(G14) + https provenance + 各境界注記。toritsugi 98→103 手続き / chigiri 66→71 referral / shared=52(片側ズレ 0)。parity + 既存 4 suite **27/27 green**。

### iter-9 (2026-06-05, loop, coverage dashboard)
**2026-06-05 自動生成カバレッジ・ダッシュボード新設 (loop iter, observability)**: 新 generator `70-tools/scripts/coverage/gen_gov_legal_coverage.py`(ooyake COVERAGE.md パターンに倣う)が registry から committed `COVERAGE.md` を両アクター分生成。toritsugi/chigiri はこれまでダッシュボードも scripts も無く、カバレッジが registry を直接読まないと不可視だった点を解消。toritsugi: 103 手続き/52法域 + **procedure kind 内訳**(passport 42 / national-id・residence 24 / civic 11 / social-security 10 / tax 7 / civil 2 / other 7) + confidence 内訳(high 72 / medium 25) + 全 unverified-seed。chigiri: 71 body/52法域 + bloc 内訳 + confidence + 全 unverified-seed。両者に cross-actor parity 行(shared 52, parity test 参照)。**G5/G8/G14 honesty を各 doc 冒頭・末尾に明示**(全 unverified-seed wayfinding scaffold = authoritative coverage ではない; chigiri は UPL/referral-only/zero-compensation, toritsugi は 行政書士法/no-advice 境界)。audit suite 全 green。working-tree edits only。

### iter-10 (2026-06-16, loop, charter-gate lexicon test)
**2026-06-16 lexicon/manifest 憲章ゲートを実行可能テストで pin (loop iter, 成熟度)**: 既存テスト(registry-seed + legal-aid-clinic cell)は被覆していなかった **manifest G1–G17 ゲートセット + 11 lexicon の構造的ゲート**を新設 `methods/test_charter_gates.cljc`(**8 tests green**, standalone・network-free・R0 ceiling 不変)で固定。pin 内容: (1) manifest が厳密に G1–G17 を宣言。(2) **G14/G15 UPL** — `legalAidMatter` の const `zeroCompensation:true` + `retainedViaPublicFund:true`、required に counselDid + supervisingCounsel + lane(advice/certified-mediation のみ)= 助言は人間 counsel 経由。(3) **G12 破門 due process** — `excommunicationProcedure` const `reversalRequiresFreshCeremony:true` + cureWindow/curePeriod/evidence 必須。(4) **正戦論 force** — `forceAuthorizationRecord` の posture が defensive/deterrent のみ(攻撃なし)+ 10点 just-war checklist + oneSbtOneVoteChainCid 必須。(5) **管轄別 UPL** — `jurisdictionPolicy` が freeAdviceLawfulAlone + regulatoryFamily(compensation/licensure/activity)+ restrictingStatute。(6) **救済ラダー** — `ipLicenseClaim` が violationTier + remedyLadderTarget(訴訟先行でない段階的救済)。(7) **退会** — `withdrawalAttestation` が memberSignature + coolingPeriodEndsAt(member-signed・冷却期間)。(8) **調停の暗号化** — `disputeMediation` claimSummaryEncryptedCid 必須。`run_tests.sh` 新設(actor-local charter-gate suite)。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `chigiri.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).

### iter-11 (2026-07-10, loop, G10 mediation-first real logic)
**chigiri's first real computed-logic method — the G10 Mediation-First Rule, closing the "Structural" schema-conformance-only gap CLAUDE.md documented since R0.** Until now, `disputeMediation`'s G10 invariant ("Cooperative mediation precedes adversarial arbitration"; CLAUDE.md's "Mediation-First Rule (G10)" bullets: `currentRound ≥ 1` before any arbitration channel may be invoked, `mediationOutcomes[]` populated before `escalateToArbitration=true`) was only machine-checked at the schema level (`test_charter_gates.cljc`'s `test-mediation-claim-encrypted` just asserts `claimSummaryEncryptedCid` is required — it never checked the actual sequencing rule). **New `methods/dispute_mediation.cljc`** (`chigiri.methods.dispute-mediation`): pure functions over a plain string-keyed map mirroring the `com.etzhayyim.chigiri.disputeMediation` lexicon's raw JSON shape — no file I/O, no network, no live dispute data, fully portable `.cljc` (no reader conditionals needed for pure logic). `mediation-first-violations` checks 3 independent rules 1:1 from chigiri's own docs (no invented policy): (1) `currentRound` must be an integer ≥ 1 before `arbitrationChannel` names an actual channel (not nil, not the lexicon's `"n-a"` sentinel); (2) `mediationOutcomes[]` must be non-empty before `escalateToArbitration=true`; (3) — a lexicon-precise refinement of (2), quoting `disputeMediation.json`'s own `escalateToArbitration` field description ("May only be true when mediationOutcomes contains at least one entry with outcomeStatus=escalate") — a non-empty outcomes log is not enough; one logged round must itself carry `outcomeStatus="escalate"`. `mediation-first-compliant?` is the boolean convenience wrapper. Chose G10 over the sibling `excommunicationProcedure` (G12) gate because G10's CLAUDE.md prose and the actual shipped lexicon field names agree exactly (`currentRound`/`mediationOutcomes`/`escalateToArbitration`/`arbitrationChannel`); G12's CLAUDE.md prose names fields (`finalizedAt`, `automaticSbtRevoke=true`, `freshAdherentCeremonyCid`) that do not all cleanly match the actual `excommunicationProcedure.json` schema (which has `automaticSbtRevokeTxCid` as a CID string, not a boolean, and no `freshAdherentCeremonyCid` field at all) — implementing G12 faithfully would have required either inventing field semantics or resolving a doc/schema drift out of scope for this iteration.

**New `methods/test_dispute_mediation.cljc`**: 10 tests, entirely SYNTHETIC fixtures (fictional `did:key:z6MkFAKE-*` party/mediator/council DIDs under fake jurisdiction tag `zz1`, fictional outcome/claim CIDs — no real dispute, no real person, appropriate given the sensitivity of legal-procedure subject matter) covering: round=0 rejected / round=1 accepted when invoking an arbitration channel, missing `currentRound` rejected the same way, empty/absent `mediationOutcomes` rejected when escalating, outcomes present but none with `outcomeStatus=escalate` rejected, an outcome with `outcomeStatus=escalate` present → accepted, ordinary non-escalating mediation always compliant regardless of round, `arbitrationChannel="n-a"` never trips rule 1 even at round 0, and multiple simultaneous violations accumulating independently. `run_tests.sh` updated to the self-contained multi-namespace `bb -e` require list (musubi/credits precedent). Full chigiri suite: **29 tests / 80 assertions, green** (`./20-actors/chigiri/run_tests.sh`; was 19/65 before this change).

**No manifest/gate-count change** — G1–G17 unchanged; this closes an *implementation* gap under the already-declared G10 gate, it does not add a new numbered gate. **Explicitly left out of scope this iteration (honest, G8)**: the sibling `excommunicationProcedure` (G12) `cureWindowStartsAt + 30 days ≤ finalizedAt` due-process date-math validator (deferred per the doc/schema-drift note above — a future iteration should first reconcile CLAUDE.md's G12 prose against the actual lexicon field names before implementing it, to avoid encoding a rule that doesn't match either); no Pregel cell wiring (`chigiri_dispute_mediation` remains an R0/R2-future scaffold — this ns is method-layer only, not invoked by any live cell); no live I/O; no real dispute/mediation/excommunication data anywhere in this change.
