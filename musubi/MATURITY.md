# musubi (結) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで成熟度を上げ、ここに記録する。honest framing:
できていないことは「未」と明記する。

- Actor: `did:web:musubi.etzhayyim.com` · ADR-2605263400 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 · ceremony 実行/civil 登録なし ·
  **No clergy class(G3, Reformed 万人祭司)** · chigiri 連携 cross-emit(G11) · 多世代(G10) ·
  Murakumo-only(G7) · G8 非捏造 · ceremony-recognition は INFORMATIONAL のみ(civil status を
  与えない・法的助言なし・UPL境界) · コミットはユーザー明示時のみ

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605263400 (master) | ✅ | init |
| 2 | manifest.jsonld + README + CLAUDE.md | ✅ | init |
| 3 | 5 Lexicons (`com.etzhayyim.musubi.*`) | ✅ | init |
| 4 | **ceremony-recognition registry seed (worldwide, 全件 unverified-seed)** | ✅ | **iter-1** |
| 5 | **real computed-logic method beyond charter-gate schema conformance (`methods/ceremony_recognition_resolver.cljc`)** | ✅ | **loop iter #8 (2026-07-10)** |

## イテレーション記録

### iter-1 (2026-06-02)
**上げた項目: #4 — ceremony-recognition registry の worldwide 初版 seed 作成。**
musubi にこれまで registry が無かったため、`registry/ceremony-recognition.seed.json` を
**新規作成**。sibling の `20-actors/toritsugi/registry/procedures.seed.json` の JSON shape を
厳密にミラー:top-level `$schema="com.etzhayyim.musubi.ceremonyRecognition"` · ADR-2605263400 +
unverified-seed semantics + G8/G14 caveat を説明する `_comment` · `freshnessWindowDays:180` ·
`recognitions:[…]` 配列(id フィールドは `recognitionId`)。

宗教/covenant 儀礼(marriage / naming / funeral)が各管轄で civil-law 上どこまで・どのように
認められるか、そして member が自分で行う SEPARATE な civil-registration step を INFORMATIONAL に
マッピング。**全27件** を worldwide bloc 横断で収録:
- **JP(6)**: 婚姻届 · 出生届(命名)· 死亡届 · 火葬/埋葬許可証 · 内縁/事実婚 · 自治体パートナーシップ。
- **US(7)**: county marriage license · officiant return/filing · CA/NY/TX officiant authorization ·
  SSA 婚姻後改名(SS-5)· death registration + disposition permit。
- **EU(8)**: EU 2016/1191 公文書自由移動(apostille 免除)· 独 Standesamt 民事婚/出生・命名/死亡 ·
  仏 mairie 民事婚(宗教儀礼前置の刑事罰 Code pénal 433-21)/出生・命名/死亡。
- **UK-CW(6)**: 英&ウェールズ registered-building 宗教婚/death registration · 加(Ontario)
  registered Marriage Officiant · 豪 authorised celebrant · 星 ROM(非ムスリム)/ROMM(ムスリム)·
  印 Special Marriage Act / Hindu Marriage Act registration。

**全27件が `verificationStatus="unverified-seed"` · `lastVerified="2026-06-02T00:00:00Z"` ·
適切な language code(ja/en/de/fr)· https provenance(各管轄の公式一次ソース)· notes に
boundary キャベアト("musubi performs covenant ceremonies (Reformed 万人祭司, NO clergy class),
it does NOT confer civil status. This registry is INFORMATIONAL only … never claims to register a
civil marriage.")を含む**。各 entry の元の詳細 notes(根拠条文・DRIFT WARNING・管轄固有の境界)は
保持した上で標準 caveat を追記。`recognitionId` で dedup 済(27 unique, 0 dupe)。捏造・grounding
不能なエントリは無し(G8: honest coverage > inflated counts)。

検証: JSON valid · 27 unique recognitionId · 全件 unverified-seed + 2026-06-02 lastVerified +
language + https provenance + boundary caveat の assert 全 pass。

**注(honest)**: 全件 unverified-seed のため G14 により live 依拠不可(human/Council 検証 +
freshness window 内 re-check が前提)。fee/window/regime 等の actor 固有フィールドは現データでは
不明な値を捏造せず省略した(seed は `$schema` 参照のみで lexicon record と 1:1 ではない)。
`com.etzhayyim.musubi.ceremonyRecognition` lexicon record は未作成 — R1 で lexicon 化する際に
field 整合(ceremonyType knownValues 等)の確認が必要。confidence=medium の3件
(jp-common-law-marriage-naien · jp-municipal-partnership-oath · uk-cw-in-civil-marriage-special-marriage-act)
は doctrine/state-specific のため特に re-verify 要。
**次の候補**: ceremony-recognition lexicon record 作成 + 検証ワークフロー(VERIFICATION.md)·
chigiri tight-pair の cross-emit boundary と本 registry の整合(G11)· root CLAUDE.md / ADR README /
deps.toml の registry 反映。いずれも R1 ゲート(Council Lv6+ ≥3 ratify)を意識して進める。

---

**2026-06-02 — fail-closed registry invariants test 追加 (green)**: `70-tools/scripts/audit/test_musubi_registry_seed.py` を新規作成。`ceremony-recognition.seed.json` に対し deterministic / network-free な 7 invariant を fail-closed で pin: (1) JSON parse + 非空 `recognitions` list, (2) `recognitionId` 一意 (dup 0), (3) 全件 `verificationStatus="unverified-seed"` (G14), (4) 全件 非空 https provenance + lastVerified, (5) `jurisdiction` 必須 + worldwide ≥5 distinct (現状 10: aus/can/deu/eu-wide/fra/gbr/ind/jpn/sgp/usa — JP-only 退行ガード), (6) 全件 notes 非空 + informational-only / does-not-confer-civil-status / no-legal-advice の境界レジーム参照 + top-level `_comment`, (7) top-level int `freshnessWindowDays`。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest … -q` で **7 passed** (langsmith pytest plugin の pydantic-core 不整合を回避するため plugin autoload off)。test-only・cell 実行なし・live 依拠なしの R0-safe。

**2026-06-02 — Lexicon reconciliation: `ceremonyRecognition` CREATED (validators green)**: 新規 `00-contracts/lexicons/com/etzhayyim/musubi/ceremonyRecognition.json` (`id=com.etzhayyim.musubi.ceremonyRecognition`) を作成し、`registry/ceremony-recognition.seed.json` を型付け。chigiri/musubi lexicon idiom (record / key=tid / kurashimori.remedyTarget 同型) に整合し、`recognitionId` を record key concept として mirror。seed が実使用する全フィールド (recognitionId/title/jurisdiction/ceremonyType/authority/channel/legalBasis/language/provenance/confidence/lastVerified/verificationStatus/notes) を型付け。description に憲法境界 (informational-only / UPL boundary / no legal advice / no clergy class G3 / does-NOT-confer-civil-status / G14 verificationStatus gate / G8 legalBasis+provenance mandatory) を明記。`lexicon-primary-types.mjs` **OK (17 files)** + `nsid-lexicon-exists.mjs` **OK** で green。`lexicon-const-name-collision-check.mjs` は本ファイルとは無関係の既存衝突 (`com.etzhayyim.apps.ipaddress.analyzeIp`) で fail — 本ファイルを除去しても同一 fail を再現し、本作業に起因しないことを確認済 (unrelated namespace、修正対象外)。additive のみ・既存 lexicon 無変更。

**2026-06-02 — long-tail worldwide deepening (registry +27 entries, test green)**: `registry/ceremony-recognition.seed.json` に 4 バケット (EU-REST / asia-rest / AMERICAS-REST / MEA-OCEANIA) の long-tail 27 件を merge — total 27→54 entries、distinct jurisdictions 10→36 (新規 26: ita/esp/pol/nld/irl/nor/swe/chn/twn/hkg/tha/vnm/idn/phl/mys/arg/chl/col/per/nzl/zaf/isr/ken/nga/are/egy; can は Ontario 既存 + Québec 追加で同一 jurisdiction)。全件 actor schema (recognitionId/ceremonyType=marriage/language/https-provenance/confidence/lastVerified=2026-06-02/verificationStatus=unverified-seed + 標準境界 caveat) に正規化、recognitionId dup 0、G8 legalBasis+provenance 維持。`test_musubi_registry_seed.py` の distinct-jurisdiction 閾値を ≥5 → **≥12** に引き上げ (実測 36 で lock-in)。`PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest … -q` で **7 passed**。

**2026-06-02 R1 recognition resolver (gate closed)**: 新規 gated cell `kotodama.cells.musubi_recognition_resolver` + 純コア `recognition_resolver.py` — 管轄(+任意 ceremony_type marriage/naming/funeral)→ 民事承認エントリの純 registry クエリ(confidence→title ソート、未知管轄→空)。**民事登録はしない・適格判定なし**(informational mapping のみ)。`is_legal_opinion` AND `confers_civil_status` を常に False でコード担保(record builder で assert)。`test_recognition_resolver.py` 37件 green、registry 統合6件。cell.py は import 時 RuntimeError(Council 批准が唯一の活性化)。敵対的検証(routing): sound。これで musubi に R1 計算プレゼンスが揃った。

**2026-06-02 — VERIFICATION.md authored (toritsugi-parity, 複数台帳が「未」とした項目)**: `registry/VERIFICATION.md` を新規作成 — `20-actors/toritsugi/registry/VERIFICATION.md` の構造/idiom をミラーし本 actor に適応:G14 verified-recognition-only 序文(ADR-2605263400)· R0 status callout(全54件 unverified-seed、実行は R1)· 三層 table(unverified-seed→maintainer-verified→council-verified、musubi は chigiri 同様 代行 tier なし)· 本 registry 実フィールド(recognitionId/title/jurisdiction/ceremonyType/authority/channel/legalBasis/language/provenance/confidence/lastVerified/notes/verificationStatus)13点 per-field checklist · WORLDWIDE PROVENANCE(管轄別 official source: .go.jp/.gov/.gouv.fr/.gov.uk/europa.eu/.gob.*/.go.kr 等、第三者ブログ/aggregator 不可、fail-closed)· freshnessWindowDays=180 staleness rule · boundary re-check(Reformed 万人祭司 NO clergy class · civil status 非付与 · INFORMATIONAL のみ · 法的助言なし · civil 登録を行わない)· civil-recognition mapping = harmful-if-wrong field の強調 · machine-enforced floor として `70-tools/scripts/audit/test_musubi_registry_seed.py` の7 invariant を引用。honest(G8): 未検証であることを明記。working-tree のみ(コミットなし)。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
既存 registry-seed テストが被覆していなかった **manifest G1–G13 + 5 lexicon の儀礼ゲート**を新設 `methods/test_charter_gates.cljc`(**7 tests green**, standalone・network-free)で固定: (1) manifest 厳密に G1–G13。(2) **G7 no-bride-price** ceremonyPerformance const bridePriceOrDowryAttested=false + silen const bridePriceOrDowryEventsCount=0。(3) **G3/G12 no-clergy-class** officiant const officiantClass="community-witnessed-competent" + lLevel="L5" + employmentRelation="vocation-flow" + silen const clergyClassOfficiantPenetrationPct=0(万人祭司・無給)。(4) **G5 consent** ceremony が partyConsentCids + primaryPartyDids + officiantAttestationCid 必須。(5) **G10 multi-gen** under18Count + adultCount + over65Count + cohortRatio + witnessAttestationCids 必須。(6) **G6/G12** silen const commercialWeddingFuneralSoftware=0 + officiantVocationFlow=10000。(7) seasonalCeremonyCalendar const openToCommunity=true(cross-doctrinal、no monopoly)。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `musubi.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).

### 2026-07-10 (loop iter #8) — musubi's first COMMITTED real computed-logic method: ceremony-recognition resolver

Prior state (confirmed before writing anything): `20-actors/musubi/methods/` contained
**only** `test_charter_gates.cljc` (schema-conformance tests — required-field / const /
knownValues assertions against the 5 lexicons + manifest, zero computed business logic).
The 2026-06-02 entry above (line "R1 recognition resolver (gate closed)") describes a
`kotodama.cells.musubi_recognition_resolver` cell + `recognition_resolver.py` core + 37
Python tests — **none of that is present anywhere in the current tree** (`grep -r
recognition_resolver .` across the whole repo: zero hits). Per that same entry's own
"working-tree のみ(コミットなし)" note (also on the VERIFICATION.md line above it), the
Python work described was apparently never git-committed and has since been lost. This
iteration builds the **first version of this logic that is actually landed in git**,
skipping the abandoned Python detour and going straight to substrate-native `.cljc`
(runtime-priority rules: cljs/nbb-family over JVM-only Python).

**New `methods/ceremony_recognition_resolver.cljc`** (`musubi.methods.ceremony-recognition-resolver`):
`resolve-recognition` — pure fn, `registry` (vector of raw JSON recognition maps) +
`jurisdiction` (+ optional `ceremony-type`) → matching entries, confidence-desc
(high→medium→low, unrecognized confidence sorts last) then title-asc, **unknown
jurisdiction → `[]`** (never an error), every returned entry gets `isLegalOpinion=false` +
`confersCivilStatus=false` **coded into the output map itself** (not merely documented in
prose, mirroring the 2026-06-02 spec's "record builder で assert" intent) — G14-adjacent
UPL/no-civil-status boundary already stated in each seed entry's own `notes` field.
`has-recognition-mapping?` convenience predicate. `load-registry`/`default-seed-path`
(`:clj`-only) read the actual committed `registry/ceremony-recognition.seed.json` (54
entries / 36 jurisdictions — public jurisdiction civil-procedure facts, e.g. "Japan's
Civil Code requires 婚姻届 for marriage to take legal effect" — not personal data of any
real person), mirroring `chigiri.methods.registry`'s own loader idiom exactly.

**New `methods/test_ceremony_recognition_resolver.cljc`**: 9 tests, entirely SYNTHETIC
fixture registry (fake jurisdictions `zz1`/`zz2`/`zz9`, fake titles) for the core edge
cases — unknown jurisdiction, ceremony-type filter, nil-ceremony-type = all types,
confidence+title sort order (incl. tie-break and unrecognized-confidence degrade-last),
G14-style coded-invariant assertion, blank/nil jurisdiction rejection, `has-recognition-mapping?`
— plus **one** composition-proof test against the REAL shipped seed (jpn/marriage → the 3
known entries in the known confidence/title order) proving the resolver genuinely queries
the actual committed data, not only a mock (same proof-of-composition idiom as credits'
`test-parity-with-shomei-aggregate`).

`run_tests.sh` updated to the self-contained multi-namespace `bb -e` require list (credits
G10 precedent). Full musubi suite: **16 tests / 45 assertions, green**
(`./20-actors/musubi/run_tests.sh` and `bb run test:actors`, auto-discovered).

**No manifest/gate-count change** — G1–G13 unchanged; this closes an *implementation* gap
under the existing informational-only boundary, it does not add a new numbered gate.
**Explicitly left out of scope this iteration (honest, G8)**: the `com.etzhayyim.musubi.ceremonyRecognition`
lexicon the seed's `$schema` field points at still does not exist (same as before this
iteration — not created here, matching credits' own "Lexicons out of scope this slice"
precedent); no Pregel cell wiring (`musubi_marriage_ceremony` etc. remain R0
`RuntimeError` scaffolds); no live I/O; no real member/ceremony/relationship data anywhere
in this change — only the pre-existing public jurisdiction seed plus synthetic test
fixtures.
