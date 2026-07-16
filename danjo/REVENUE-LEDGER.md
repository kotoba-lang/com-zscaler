# danjo 弾正 — revenue ledger (源泉所得税・復興特別所得税 の使途追跡)

```
data/gov-revenue-corpus.jp.edn   ── ingest.clj ──▶  model  ── revenue_ledger.clj ──▶  LOCAL kotoba
(pre-published gov.dataset.*       (passive, G3)    {:accounts    (trace / EAVT datoms /        Datom log
 records, IPFS-pinnable)                             :revenue-     content-addressed             (commit-DAG)
                                                     lines …}      commit-DAG)                       │
                                                                                                     ▼
                                                                              kotoba_bridge.clj ──▶ LIVE kotoba
                                                                              (datomic.transact,     engine :8077
                                                                               G7-gated, no-server-   (IPFS/IPNS)
                                                                               key, dry-run default)
```

Files: `methods/{revenue_ledger,ingest,discrepancy,taxes,org_actor,coverage,kotoba_bridge}.clj` +
`data/{gov-revenue-seed,gov-revenue-corpus,jp-national-taxes,jp-local-taxes,jp-fiscal-orgs}.edn`
(+ ingests danjo's existing `data/gov-fiscal-seed.jp.json`) + matching `test_*.clj`
(154 checks, green under `bb` and `clojure`). Coverage: **FY2023+2024 · 17 国税 + 12 地方税 · 8 組織-actor**.

Answers, **in Clojure on the kotoba EAVT Datom log**, the question:

> 日本政府の **源泉所得税** 及び **復興特別所得税** が、どこに、どのように使われているかを
> **1円単位** で追えるか?

## The honest answer is structural, and it differs by tax-kind

Japanese public finance gives two different answers, and the code encodes the difference
**structurally** (not as a disclaimer):

| 税目 | 会計 | `:earmark?` | 1円単位の追跡 |
|---|---|---|---|
| **源泉所得税** | 一般会計 | `false` | **不可** — ノン・アフェクタシオン原則(財源は代替可能/fungible)。特定の1円が特定の歳出に充てられたという会計的事実は存在しない。 |
| **復興特別所得税** | 一般会計 → 繰入 → 東日本大震災復興特別会計 | `true` | **可** — 特定財源。閉じた特別会計境界の中で 繰入額 → 歳出 が1円単位で照合できる。 |

`trace` の判定は「税がどこで徴収されたか」ではなく **「その税を earmarked(特別会計)へ繰入する
`:gov.transfer` が存在するか」** という構造的事実で行う。だから 復興特別所得税(一般会計で徴収・
徴収後に特会へ繰入)は traceable、源泉所得税(繰入なし)は non-traceable と正しく分かれる。

## Honesty gate (danjo G4 の歳入側アナログ)

`outlay-datoms` は、**非 earmarked(一般会計)の歳出に `:gov.outlay/funded-by-tax` を付けようと
すると RAISE する**。一般会計という代替可能な境界を通した「特定税目→特定歳出」の1円単位の出所主張は
**表現不能(unrepresentable)**。danjo が「判決(verdict)」を表現不能にしているのと同じ構造で、
ここでは「偽の出所主張」を表現不能にしている。

加えて受け継ぐ danjo 規律: **G4** 判決トークン禁止 / **G5** 出典 CID ≥2 / `:non-adjudicating true`。

## Data model (kotoba EAVT, `[:db/add E A V]` append-only)

```
account:<id>                 :gov.account/{kind earmark? note}
revenue-line:jp:<fy>:<tax>   :gov.revenue/{tax-kind account fiscal-year amount-jpy source-record-cids sourcing}
transfer:<from>-><to>:<fy>   :gov.transfer/{from to tax-kind fiscal-year amount-jpy source-record-cids}
outlay:<program>:<fy>        :gov.outlay/{account program-code program-name cofog recipient-class
                                          fiscal-year amount-jpy source-record-cids sourcing [funded-by-tax]}
```

金額は exact integer(`amount-jpy`)で **1円精度**。トランザクションは sha256 の content-addressed
commit-DAG(`:tx/cid` = `"b"`+sha256)として `kotoba.py` と同形でローカル append-only ログに積まれ、
`verify-chain` で改竄検知・resume-safe。`run-cycle!` は1サイクル=1tx。

すべての数値は `:representative`(財務省 一般会計 / 復興特別会計 予算の実構造に基づくが、IPFS-pinned
`jp_yosan` / `jp_fukko` corpus の G7 ライブ検証前)。集計・プログラム単位の端点のみ(danjo G10
aggregate-first)。**権威ある予算書ではない。**

## Ingest (passive-only, G3) — `ingest.clj`

`ingest.clj` projects the **pre-published `com.etzhayyim.gov.dataset.*Record` corpus**
(`data/gov-revenue-corpus.jp.edn`) → the model. danjo NEVER fetches a live portal; **the corpus
IS the input**. The sibling of `budget_ledger.py`, for the revenue side, in Clojure:

- `:record-kind ∈ {:revenue :transfer :appropriation :outlay}`, field names mirror
  `budget_ledger.py` `normalize_record` (EDN-keyed).
- each record gets a deterministic `record-cid` (`gov.dataset.<kind>Record:<sensor>:<fy>:<rid>#<sha256[:24]>`);
  every projected entry carries **≥2 source CIDs** (its own + the dataset manifest CID) → G5 holds.
- the **account-EARMARK framework is accounting LAW** (特別会計法 / 復興財源確保法), encoded as the
  `account-law` constant — NOT a fetched record. This is what keeps the traceability verdict honest.

It also ingests danjo's **existing JSON budget corpus** directly — `ingest-budget` reads
`data/gov-fiscal-seed.jp.json` (the `budget_ledger.py` `budgetRecord` shape) via a dep-free
`parse-json` (Long-exact, 1円), projecting `appropriation`/`outlay` rows. `with-budget` merges that
into a revenue model. So both the EDN revenue corpus and the JSON budget corpus feed one model.

```bash
cd methods && bb -e '(load-file "ingest.clj") \
  ((resolve (symbol "root.danjo.methods.ingest" "-main")) "../data/gov-revenue-corpus.jp.edn")'
```

## Discrepancy → non-adjudicating observations — `discrepancy.clj` (danjo's actual eye)

`reconcile` groups appropriations vs outlays per (programCode, fiscalYear) and reports the
**FACTUAL** relation O vs A — three categories, **no verdict token representable**:

| category | when |
|---|---|
| `:appropriation-outlay-within` | O ≤ A (a fact; **emits NO observation**) |
| `:outlay-exceeds-appropriation` | O > A |
| `:outlay-without-appropriation-trace` | A = 0, O > 0 (also fires on partial corpus — a declared FP mode) |

`observations` turns the two divergence categories into danjo **`discrepancyObservation`** maps,
and `observation-datoms` emits `:danjo.obs/*` EAVT in the **same shape as `methods/kotoba.py`
`derived_datoms`** — so the revenue ledger plugs straight into danjo's existing observation model:
`:danjo.obs/{category, non-adjudicating(=true), pattern, source-record-cids, method-note-cid,
known-false-positive-modes, sourcing}`.

- **G4** — `observation-datoms` RAISES if a verdict token appears in any attr **or in a category
  value** (a legal verdict is unrepresentable); every obs carries `:danjo.obs/non-adjudicating true`.
- **G5** — every observation cites ≥2 record CIDs.
- **G6** — the detector heuristic is published as an open, versioned, content-CID'd `method-note`.
- danjo **finds, never judges**: O > A is a fact to surface (timing / partial-corpus are declared
  false-positive modes), legal characterization goes to human counsel via chigiri.

Observations persist + bridge through the same pipeline:
`(run-cycle! {:seed model :extra-datoms (observation-datoms (observations model 2024))})`.

## kotoba-datomic 永続化 — two layers

1. **LOCAL append-only commit-DAG log** (`revenue_ledger.clj` `run-cycle!`): each cycle = one tx,
   `:tx/cid = "b"+sha256(prev,datoms)`, `verify-chain` tamper-evident, resume-safe. tx-ids
   auto-increment per DATA tx (so the bridge cursor is monotonic).
2. **LIVE kotoba engine** (`kotoba_bridge.clj`, ibuki R3 pattern): pushes each local tx as one
   `com.etzhayyim.apps.kotoba.datomic.transact` to a running node (:8077) → the Datoms land on the
   REAL distributed graph (IPFS-backed, IPNS-headed).
   - **host allowlist** (loopback + EVO-X2 LAN, ADR-2605215000) — off-allowlist raises BEFORE I/O;
   - **`graph-cid`** = CIDv1 dag-cbor sha2-256 base32 (`bafyrei…`), matches kotoba-core;
   - **exactly-once**: a `:bridge/*` checkpoint on the local log is the durable cursor;
   - **optimistic concurrency**: prior remote `commit_cid` sent as `expected_parent`;
   - **`:danjo.tx/*` provenance** meta on every pushed tx (local id / CID / prev);
   - **no-server-key**: auth is the node's PUBLIC operator DID as an unsigned loopback bearer;
   - **DRY-RUN by default** (returns exact request bodies, NO I/O); live only when
     `DANJO_KOTOBA_LIVE=1` (Council/operator-gated). The loop itself does no network I/O — tests
     are hermetic (dry-run + injected transport).

```bash
# dry-run export of the pending transact bodies (no network):
cd methods && bb -e '(load-file "kotoba_bridge.clj") \
  ((resolve (symbol "root.danjo.methods.kotoba-bridge" "-main")) "<local-log-path>")'
# live push (operator-gated): DANJO_KOTOBA_LIVE=1 DANJO_KOTOBA_OPERATOR_DID=did:web:… …
```

## 税の全体像 国 + 地方 — `taxes.clj` (`jp-national-taxes.edn` + `jp-local-taxes.edn`)

国税17 + 地方税12 = **29税目 ≈111兆円**(国70兆 + 地方41兆, representative)を `tax:jp:<id>` EAVT
(`:gov.tax/*`) として射影。`load-local-taxes` + `combine` で国・地方を統合し、`summary` の `:by-level`
が国/地方を分解。使途追跡可能性は **honest 3-way**(地方税にも同一適用):

| earmark-kind | 例 | per-yen 追跡 |
|---|---|---|
| `:general` (一般会計・fungible) | 所得税/法人税/相続税/酒税/関税… (64%) | ❌ |
| `:statutory-purpose` (目的税的, 法定充当だが一般会計内) | 消費税→社会保障4経費・出国税 (34%) | ❌(法的充当方向のみ事実) |
| `:special-account` (特別会計・特定財源) | 復興/電源開発/石油石炭/たばこ特別 (2%) | ✅ |

→ **税全体の per-yen 追跡可能分は約1.3%のみ**(国税ベースでも約2%)。揮発油税・自動車重量税は2009
一般財源化済として `:general`(履歴を `:note` に明記)。地方の目的税(都市計画税/入湯税/事業所税/
地方消費税の社会保障分)も「法定充当だが地方一般会計内」で per-yen ❌ と honest に分類。

## 組織別 keyless mirror-actor — `org_actor.clj` (`data/jp-fiscal-orgs.edn`)

実在の徴収・所管組織を **1組織=1 keyless mirror-actor** (entity-as-actor, ADR-2606042330) として
`:gov.org/*` に射影。`did:web:etzhayyim.com:actor:jp-<handle>`、**no-server-key**
(`:gov.org/keyless true`、verificationMethod 空)、その組織を代理・代表しない観測ミラー。

- 国・徴収: **国税庁** (`jp-nta`, 内国税15税 ≈69兆) / **税関** (`jp-customs`, 関税・とん税)
- 国・会計所管: **復興庁**(復興特会)/ **資源エネルギー庁**(エネルギー特会)/ **財務省理財局**
  (国債整理基金特会)/ **財務省主計局**(一般会計 — fungible ゆえ per-yen 追跡可税 0、と正直に表示)
- 地方・徴収(集約): **都道府県(集約)** (`jp-prefecture-agg`, 47団体) / **市町村(集約)**
  (`jp-municipality-agg`, 1741団体) — 集約代表であることを `:aggregate true` で明示(ooyake G5)

`org-view` が各組織の担当スライス(徴収する税 / 所管会計 / per-yen 可否 / 額)を返す。税・組織 datom は
`run-cycle! :extra-datoms` で同じローカル log → kotoba bridge パイプラインに乗る。

各組織は**解決可能(resolvable)**: `generate-profiles!` が `data/actors/<handle>.profile.json`
(+ `actors.json` 索引)を生成 — `verificationMethod:[]`(no-server-key)・`keyless:true`・
`type:gov-fiscal-mirror` の観測ミラー profile(代理・代表しない)。dep-free `->json`(`parse-json` の逆)。

```bash
cd methods && bb -e '(load-file "org_actor.clj") \
  ((resolve (symbol "root.danjo.methods.org-actor" "-main")) "generate")'   # → data/actors/*.json
```

## Coverage scorecard — `coverage.clj`

`report` computes an HONEST coverage map (matsurigoto G5): fiscal-years, tax-kinds, per-(tax,fy)
traceability (non-traceable taxes counted as such, not hidden), reconciliation split, datom
totals. `coverage-md` renders a scorecard; `-main` regenerates `data/REVENUE-COVERAGE.md`. As of
this iteration: **FY2023+2024, 2/4 tax-years per-yen traceable** (both 復興 residual 0; both 源泉
honestly non-traceable), 137 datoms.

```bash
cd methods && bb -e '(load-file "coverage.clj") \
  ((resolve (symbol "root.danjo.methods.coverage" "-main")))'   # → data/REVENUE-COVERAGE.md
```

## Run

```bash
# tests (bb / clojure)  154 checks (ledger 25 + ingest 22 + discrepancy 21 + taxes 26
#                                   + org-actor 25 + coverage 15 + bridge 20)
./run_tests_clj.sh                  # or: CLJ_RUNNER=clojure ./run_tests_clj.sh

# demo trace for both taxes
cd methods && bb -e '(load-file "revenue_ledger.clj") \
  ((resolve (symbol "root.danjo.methods.revenue-ledger" "-main")) "../data/gov-revenue-seed.jp.edn")'
```

```clojure
;; programmatic
(require 'root.danjo.methods.revenue-ledger)            ; or load-file
(def seed (rl/load-seed "data/gov-revenue-seed.jp.edn"))
(rl/trace seed :reconstruction-surtax 2024)  ; => {:traceable? true :per-yen? true :residual 0 :path [...]}
(rl/trace seed :withholding-income    2024)  ; => {:traceable? false :reason :non-earmarked-general-account ...}
(rl/run-cycle! {:seed-path "data/gov-revenue-seed.jp.edn"})  ; persist 1 tx to the local Datom log
```

## Boundary

これは danjo の歳入側拡張であり、**会計検査院でも国家公認の監査機関でもない**(danjo の任意団体・
非断定規律をそのまま継承)。源泉所得税については「予算→支出の相互参照と乖離の事実指摘」までに留まり、
特定の使途を主張しない。R1 で実 corpus の G7 ライブ検証・lexicon 化・fleet 登録。
