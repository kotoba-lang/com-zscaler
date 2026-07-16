# karute (カルテ) — Maturity Ledger

`/loop` 進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに記録する。
honest framing: できていないことは「未」と明記する。

- Actor: `did:web:karute.etzhayyim.com` · ADR-2605231100 (EMR Phase 1) · DID-worker LIVE
- **二層構造**: (1) この `20-actors/karute/` = kotoba-native **charter surface** — 11 FHIR
  Lexicons + 憲法ゲートテスト; (2) EMR の実装 (Svelte SuperApp + lg-karute pod + did-worker) は
  `60-apps/etzhayyim-project-karute/` + `50-infra/karute-did-web/` 側(`actor.edn` の deploy stages)。
  この台帳は **(1) の charter surface** の成熟度のみを追う(実 EMR は別レイヤ)。
- 不変条件(厳守): 全 PHI は `com.etzhayyim.encrypted.record` envelope のみ(平文 PHI を MST に
  書かない) · consent = `com.etzhayyim.consent.capability`(Ed25519 member-signed, no-server-key
  ADR-2605231525) · 3軸 split clean(payoff/custody/settlement = etzhayyim) · 患者識別子 =
  DID(`patientDid`)、平文氏名/MRN を連結キーにしない。

## 成熟度チェックリスト

| # | 項目 | 状態 | 完了イテレーション |
|---|---|---|---|
| 1 | ADR-2605231100 (EMR Phase 1) | ✅ | init |
| 2 | actor-manifest.jsonld + actor.edn(deploy pipeline)+ CLAUDE.md + NOTICE | ✅ | init |
| 3 | 11 FHIR Lexicons (`com.etzhayyim.karute.*` — patient/encounter/condition/observation/medicationRequest/serviceRequest/carePlan/dispenseRecord/soapNote/homecareEpisode/homeVisit) | ✅ | init |
| 4 | did:web:karute.etzhayyim.com worker LIVE(`50-infra/karute-did-web`) | ✅ | init |
| 5 | **charter-gate テスト** (`methods/test_charter_gates.cljc` — 4 tests / 35 assertions) | ✅ | **iter (this)** |
| 6 | run_tests.sh が charter-gate suite を実行(actor reflex に wired) | ✅ | **iter (this)** |
| 7 | encrypted-envelope 規律をスキーマ層で機械強制(`additionalProperties:false` + 平文 PHI フィールド拒否、R1) | 未 | — |
| 8 | consent.capability の Ed25519 検証テスト(member-signed / server-refused) | 🟡 部分(iryo 受理境界に検証ゲート実装 + 実鍵ペア sign→verify roundtrip テスト green。鍵の DID 解決は未) | **iter 2026-07-08(3)** |
| 9 | 患者 DID = 30日 rotating pseudonym(ADR-2605181200)の構造検証 | 未 | — |
| 10 | kotoba EAVT への FHIR inner-type 投影(public graph = meta only)の検証 | 未 | — |
| 11 | iryo(レセプト)への hand-off boundary テスト(karute → iryo consent-capability) | 🟡 部分(受理境界+rezeptプレビュー自動配線+署名検証ゲート[鍵解決済みの場合]+consentCapabilityUri構造的自己整合性検証。鍵のDID解決・実PDS fetchは未) | iter 2026-07-08 → iter 2026-07-08(2) → iter 2026-07-08(3) → **iter 2026-07-08(4)** |

## イテレーション記録

### iter 2026-07-08(4)
**上げた項目: #11 続き — (b) PDS/AT-URI解決の**構造的な半分**だけを実装:
`consentCapabilityUri` が (a) 正しく `at://<did>/<collection>/<rkey>` に分解できる、
(b) collection が正規 NSID `com.etzhayyim.consent.capability` である、(c) did セグメントが
解決済み `capability["granterDid"]` と一致する、の3点を `handoff.cljc` の `capability-gate`
に追加した。ネットワークI/O・新規依存ゼロ(純粋な文字列 parse)。実際のバイト取得(実PDSへの
HTTPS fetch)は依然スコープ外のまま — honest framing: (b) は「構造」と「取得」の2層に分解でき、
今回解消したのは前者のみ。**

前回セッションの申し送り「(b) 実PDS解決の実現可能性を再調査する」を受けて着手前に事前調査:

- `@etzhayyim/sdk/pds`(TS)は実装 stub ではなく、`@etzhayyim/atproto-client`
  (= 別リポジトリ `kotoba-lang/atproto-client`)への re-export shim だった(README の
  「Status: scaffold v0.0.0 / 全メソッド未実装」という記載は package.json
  `0.1.0-alpha` の実態に対して古い)。`kotoba-lang/atproto-client` 側には `.cljc` 実装
  (`src/kotoba/lang/atproto_client/pds.cljc`)があり、`resolve-pds`/`get-record` は
  transport を `IHttp` として host-injected にしているため、テストは fake transport で
  実ネットワークなしに検証可能 — 「テスト内で安全に扱えない」という理由での対象外化は
  成り立たないと判明。
- しかし実際に iryo へ配線するには (1) iryo に現状 deps.edn/git-dep 機構が一切無く、
  `kotoba-lang` という別 org の package への**新規 cross-repo 依存**を今回新設する必要が
  あること、(2) 本番コードが**実際に HTTPS did:web fetch を行う**ことになり、これまでの
  3イテレーションが一貫して守ってきた「既に解決済みの入力を検証する(fetchはしない)」
  パターン(`signature-gate` と同型)を逸脱する、大きめでリスクのある変更になること — の
  2点から、今回はネットワーク fetch そのものの配線は見送り、fetch なしで前進できる
  **構造的自己整合性チェックだけ**を実装した(honest framing: 「今回も cross-repo だから
  何もしない」ではなく、実際に調べた上で「構造の半分は前進できる」と判断)。

実装(`20-actors/iryo/methods/handoff.cljc`):

- `parse-at-uri` — 純粋関数。`at://<did>/<collection>/<rkey>` を `{:did :collection :rkey}`
  に分解。did セグメント自体がコロンを含む(`did:web:foo.example:bar`)ため `:` ではなく `/`
  で split(AT-URI 構文上安全)。不正な形(セグメント数不一致・空セグメント・`at://`
  非prefix)では例外を投げず nil を返す。
- `consent-capability-collection` — 正規 NSID 定数(`com.etzhayyim.consent.capability`)。
  lexicon の記述「granter の PDS に `com.etzhayyim.consent.capability` として保存される」
  (ADR-2605231401)を根拠にした固定値。
- `capability-gate` に3つの cond 節を追加(既存の purpose/granteeDid/granterDid/失効/期限/
  scope/resourceUris チェックのあと、最後尾に配置 — 既存テストの意味論を変えないため):
  consentCapabilityUri の欠落 → 不正な AT-URI 形式 → collection 不一致 → did セグメントが
  `capability["granterDid"]` と不一致(なりすまし/差し替え capability の検出)。
- これらは「解決済み capability の**自己整合性**」を検証するだけで、`consentCapabilityUri`
  の実バイトを実際にネットワークから取得するわけではない — 依然 out of scope。
- テスト(`methods/test_handoff.cljc`、23→29 tests / 53→71 assertions、green): `parse-at-uri`
  の直接テスト(整形 URI / 不正 URI 各種)+ capability-gate レベルで4本(uri欠落・不正形式・
  collection不一致・did不一致=なりすまし検出)。既存テスト(granterDid/patientDid不一致等)は
  無変更で green のまま(新チェックは既存チェック群の**後**に配置したため意味論が競合しない)。
- `20-actors/iryo/run_tests.sh` 12 suites 全 green。karute 側 `run_tests.sh`
  (charter-gate suite、4 tests / 35 assertions)は無変更のまま green を確認。
- **honest framing — 依然未達のまま残るもの**: granterDid の DID 文書から実際に公開鍵を
  取得する経路、`consentCapabilityUri` の実バイトを実PDSから fetch する経路 — どちらも
  cross-repo の実 HTTPS I/O で、iryo には現状その配線(deps.edn含む)が一切無い。したがって
  「karute → iryo」の hand-off は依然「構造検証は完備、実データ取得は無し」の状態。

### iter 2026-07-08(3)
**上げた項目: #8 — consent.capability の Ed25519 署名検証を iryo 受理境界(`handoff.cljc`)に実装。
(a) を部分的に解消:検証ロジック自体は完成 + 実鍵での sign→verify テスト green。ただし
granterDid(rotating pseudonym did:web)の DID 文書から鍵そのものを取得する経路(network
resolution)は依然 cross-repo で未 — honest framing: 部分達成。**

前々イテレーション (iter 2026-07-08) の honest framing で残した (a) Ed25519 署名検証・(b) PDS
解決のうち、(a) の「検証ロジック」部分だけを実装(鍵の**取得**である (b) 相当の DID 解決は
今回もスコープ外のまま、無理に着手しない)。事前調査で判明した事実:

- `patientDid` はこの bridge では **rotating pseudonym did:web**
  (`iryo.methods.karte/rotating-pseudonym-did` → `did:web:patient.iryo.etzhayyim.com:<hash>`)
  であり、did:key のような自己記述型 DID ではない。したがって granterDid の公開鍵を得るには
  実際の HTTPS did:web 文書解決が必要で、これは PDS 解決 (b) と同種の cross-repo network I/O
  — iryo 内で完結できない。
- 一方、**鍵が既に解決済みで手元にある場合の Ed25519 署名検証そのもの**は iryo 内で完全に
  完結でき、かつ **追加の依存ライブラリが一切不要**(JDK 標準 `java.security`
  KeyFactory/Signature の Ed25519 サポート、JDK 15+)。同じ手法は既に
  `20-actors/kaiyaku/tools/issue_capability.cljc` で実装・green 確認済み(`bb` = babashka
  v1.12.218 環境で実測検証: sign→verify roundtrip が正しく動作)。

実装(`20-actors/iryo/methods/handoff.cljc`):

- `canonicalize-capability-payload` — capability record から `signature` を除いた全フィールド
  を sorted-keys の決定的な canonical 文字列にする(署名対象バイト列)。**honest framing**:
  これは本 namespace 独自の canonicalization であり、実際の granter 側 signer
  (`@etzhayyim/sdk.signConsentCapability`, ADR-2605231401 Phase 2)は依然 stub のため、
  そちらとの byte-parity は未検証(`kaiyaku/tools/issue_capability.cljc` の G6 と同種の
  オープンな注記)。
- `verify-ed25519-signature` — 生 32byte Ed25519 公開鍵を X.509 SubjectPublicKeyInfo DER
  (RFC 8410 §4、固定12バイトprefix)でラップして `KeyFactory`/`Signature` で検証。追加
  依存ゼロ。babashka 上での SPKI ラップ往復を事前にスクリプトで実測検証してから組み込んだ。
- `signature-gate` — 新しいゲート関数。呼び出し元が **`granterPublicKey`**(base64 の生
  32byte 公開鍵、`capability` 自体と同じ「既に解決済みの入力」契約)を供給した場合のみ
  暗号学的検証を実行;供給しない場合は no-op で **既存呼び出し元の挙動は byte-for-byte 不変**
  (後方互換、新しい必須ゲートではない)。鍵が供給された場合、署名欠落・alg不一致・検証失敗
  (誤った鍵 or 署名後の改ざん)はすべて fail-closed で `iryoStatus:"needs-info"` を返す。
- `handoff/handle-ingest` に配線: 構造ゲート(capability-gate)通過後にのみ signature-gate
  を実行する順序(構造ゲート失敗時は署名検証をスキップ)。`granterPublicKey` は
  `cell-only-keys` に追加し PHI allow-list から除外。
- テスト(`methods/test_handoff.cljc`、16→23 tests / 37→53 assertions、green):
  実際に JDK で Ed25519 鍵ペアを生成し、実際に署名し、実際に検証させるフルパス
  (モック無し)。正当な署名の accept、署名後の改ざん(issuedAt 変更)の reject、誤った
  公開鍵での reject、署名欠落 + 鍵供給時の reject、alg 不一致の reject、**鍵未供給時は
  既存の非署名 capability も従来どおり通る**(後方互換)回帰テスト、canonicalization が
  signature フィールドを除外し決定的であることの直接テストを追加。
- `20-actors/iryo/run_tests.sh` 12 suites 全 green(test-handoff は 16→23 tests)。karute 側
  `run_tests.sh`(charter-gate suite、4 tests / 35 assertions)は無変更のまま green を確認。
- **honest framing — 依然未達のまま残るもの**: granterDid の DID 文書から実際に公開鍵を
  取得する経路(did:web HTTPS 解決、cross-repo network I/O)、`consentCapabilityUri`/AT-URI
  の実 PDS 解決(b)、canonicalization の実 granter-side signer との byte-parity。したがって
  「karute → iryo」の署名検証は **検証ロジック完成 + 鍵が既にあれば機能**するが、鍵取得を
  含む end-to-end ではない。

### iter 2026-07-08(2)
**上げた項目: #11 続き — honest framing の (c) を解消:受理後の実レセプト計算
(`iryo.methods.agent/handle-rezept`) への自動接続を実装。(a) Ed25519 署名検証と
(b) PDS 解決は依然未(次点候補として明記済み、cross-repo `@etzhayyim/sdk` 依存)。**

前イテレーション (iter 2026-07-08) の honest framing で残した3点のうち、iryo 内部で
完結できる (c) だけを選んで実装(cross-repo 依存のある (a)/(b) は今回もスコープ外の
まま、無理に着手しない)。`20-actors/iryo/methods/agent.cljc` の `handle-ingest-billing`
を拡張:

- **`handoff/handle-ingest` は無変更** — 受理境界(PHI-free gate + consent.capability
  構造ゲート)は前イテレーションのまま。ゲートを弱めていない。
- ゲート通過後、呼び出し元が**解決済みの `\"encounter\"`**(`handle-rezept` と同じ形——
  すでに解決済みの `capability` を渡す既存の契約と同一パターン)を**追加で**渡していた
  場合のみ、`handle-rezept` を自動呼び出しし、結果を `\"rezeptPreview\"` として応答に
  添付する。`\"encounter\"` を渡さない場合の応答は従来と完全に同一(後方互換、新しい
  ゲートではない)。
- `iryoStatus` は rezept 計算の成否に関わらず `\"pending\"` のまま(G3/G5 non-adjudicating
  規律を維持)。マスター未登録コード等で計算が失敗しても受理自体は取り消さず、
  `\"rezeptPreviewError\"` として個別に報告する(受理境界の合否とプレビュー計算の合否を
  混同しない)。
- **honest framing — 依然未達のまま残るもの**: (a) capability の Ed25519 署名検証、
  (b) `consentCapabilityUri`/AT-URI の実 PDS 解決(`@etzhayyim/sdk`、cross-repo)。
  したがって「karute → iryo」の hand-off は依然 **署名検証なし・実データ解決なし**の
  受理境界+プレビュー計算に留まり、end-to-end ではない。
- テスト: `20-actors/iryo/methods/test_e2e.cljc` に3本追加(rezeptPreview が計算される
  happy path、ゲート不合格時は計算をスキップすること、計算失敗時に受理自体は取り消さ
  れず `rezeptPreviewError` のみ返ること)。`20-actors/iryo/run_tests.sh` 12 suites 全
  green(test-e2e は6→9 tests)。karute 側 `run_tests.sh`(charter-gate suite、4 tests /
  35 assertions)は無変更のまま green を確認。

### iter 2026-07-08
**上げた項目: #11 — iryo 側の受理境界(intake boundary)を実装 + テスト。honest framing: 部分達成。**
karute の `requestIryoBilling` は `agent.invoke` で iryo の `ingestKaruteEncounterForBilling`
を呼ぶが(`actor-manifest.jsonld` forwardToIryo step)、**iryo 側には受け皿が全く無かった**
(このイテレーション以前は 20-actors/iryo に該当ハンドラ0件)。`orgs/etzhayyim/root/20-actors/
iryo/methods/handoff.cljc` (+ `methods/test_handoff.cljc`, 16 tests / 37 assertions, green)
がその受理境界を実装した:
- **PHI-free intake gate (iryo G2)** — karute が転送する wire フィールド
  (patientDid/encounterDid/facilityDid/serviceRequestUris/medicationRequestUris/
  consentCapabilityUri) を allow-list 検証。DID/AT-URI prefix チェック + 全 string leaf の
  ASCII-only チェック(smuggled PHI を fail-closed で拒否)。
- **consent.capability 構造ゲート (iryo G1/G7)** — 解決済み capability record に対し
  purpose=insurance-billing / granteeDid=iryo自身 / granterDid=patientDid一致 / 未失効 /
  未期限切れ / scope・resourceUris 充足 を検証。
- **結果語彙の規律 (iryo G3/G5)** — 受理成功は `iryoStatus:"pending"` のみ(draft キュー投入、
  オンライン送信はしない);ゲート不合格は `iryoStatus:"needs-info"` のみを返す —
  `"accepted"`/`"rejected"` は審査支払機関の査定語彙であり iryo は使わない(non-adjudicating
  discipline を hand-off 境界にも一貫させた)。
- **honest framing — 未達のまま残るもの**: (a) capability の **Ed25519 署名検証**は行わない
  (karute 側の項目 #8 がそもそも未、こちらも同じく未 — このイテレーションが検証するのは
  構造/ビジネスロジックのゲートであって暗号署名ではない);(b) `consentCapabilityUri` /
  各 AT-URI の **実際の PDS 解決**は行わない(`@etzhayyim/sdk` 依存、cross-repo、karute
  アプリ側の責務);(c) 受理後の実レセプト計算(`iryo.methods.agent/handle-rezept`)への
  自動接続は無い(実データはまだ流れない、intake の受理/拒否境界のみ)。したがって
  「karute → iryo consent-capability」の hand-off boundary は **受理境界のみ完了**であり、
  end-to-end(署名検証 + PDS 解決 + 実際のレセプト計算までの自動フロー)は依然未。
- テスト: `20-actors/iryo/run_tests.sh` に `iryo.methods.test-handoff` を追加registered(12
  suites, 全 green)。`20-actors/iryo/methods/test_e2e.cljc` に agent.cljc 経由の配線確認
  テストを1本追加(`test-handle-ingest-billing-is-wired-through-agent`)。karute 側の
  `run_tests.sh`(charter-gate suite, 4 tests / 35 assertions)は無変更のまま green を確認。

### iter (this) — 2026-06-18
**上げた項目: #5 + #6 — charter surface のテスト被覆をゼロから確立。**
`methods/test_charter_gates.cljc` を新規作成(4 deftests / 35 assertions、green)。central FHIR
lexicons を cheshire で読み、charter が依存する**構造的不変条件**を pin した(誤った no-plaintext-PHI
主張はしない — これらは encrypted-envelope の inner-type であり、PHI 機密は envelope 層で強制される):
- **interop** — 全 11 resource が `fhirResourceType` const を pin(Patient/Encounter/Condition/
  Observation/MedicationRequest/ServiceRequest/CarePlan/MedicationDispense/Composition/EpisodeOfCare)。
- **DID-centric identity** — 全 clinical resource(10/11)が `patientDid` を required;患者は DID
  束縛で、平文氏名/MRN を連結キーにしない(subject-DID custody, ADR-2605172400)。
- **accountability** — `soapNote.authorDid` required;prescriber/performer/pharmacist/requester/
  recordedBy は DID フィールド(無名/自由記述の著者は表現不能)。
- **closed clinical vocabularies** — encounter/observation/medicationRequest の status・class・
  category・intent は閉じた FHIR value set。
`bb.edn test:charter` に `karute.methods.test-charter-gates` を登録。`run_tests.sh` が charter
suite を実行するよう確認(actor reflex に wired)。ゲートは一切弱めず、assert のみ。
