# open-apqc — kotoba-native pilot (Datom + clj→WASM)

APQC PCF Coordinator の **kotoba-datomic / kotoba-clj→WASM** 実装。`isco/kotoba/`
と同じパターン。従来の `vertex_open_apqc_l1` RW テーブル / kotoba TS + AT-PDS
経路に対し、kqe-over-Datom-log substrate (ADR-2605262130 + ADR-2605312345) 側を
ここに置く。

実装は **OpenRouter 経由の `moonshotai/kimi-k2.7-code`** を実装エンジンとして生成し、
Claude (Opus 4.8) がオーケストレーション・検証 (actor ランタイム LLM は不変 →
ADR-2605215000 §1 に抵触しない)。

## 成果物

| ファイル | 役割 |
|---|---|
| `apqc-pcf.kotoba.edn` | PCF v7.4 の EAVT Datom スキーマ + `:representative` seed。**全 13 L1 + 全 13 L1 + 全 72 L2 + 代表 L3 (352)、計 438 行**（9.0→9.1→9.1.1→9.1.1.1 / 7.0→7.1→7.1.1 含む）。`:apqc.process/code` が `:db.unique/identity`。 |
| `apqc-coordinator.clj` | kotoba-clj `defgraph` cell。`run(ctx)` が CBOR `{code, mode}` を復号し `:route → (if-edge summarize? :summarize :lookup)`。`:lookup` は `kqe-get-objects` で `apqc.process/name` Datom を読み、`:summarize` は `llm-infer`。 |
| `apqc-coordinator.wasm` | コンパイル済み WASM Component (kotoba:kais world)。 |

## ctx 契約

- `code` : text — 対象 PCF コード (例 `"9.1.1"`)
- `mode` : uint — `0` = 名称ルックアップ / `1` = llm 要約 / `2` = coverageSnapshot 件数 (CBOR uint) / `3` = 親コード / `4` = 子プロセス数 / `5` = 子コード配列 (CBOR array) / `6` = coverage比 (CBOR map)

## ビルド & ローカル deploy 検証

ビルド/検証はこの actor 内のスクリプトで完結する。Clojure→WASM コンパイラと
WasmExecutor は **kotoba substrate エンジン(sibling repo)の汎用 `kotoba-clj` CLI**
が担い、actor はそれを呼ぶだけ(kotoba には APQC 固有コードは無い)。

```sh
./build.sh        # apqc-coordinator.clj → apqc-coordinator.wasm (kotoba-clj CLI)
./run_tests.sh    # seed 整合(bb) + gaps + ビルド + WasmExecutor スモーク(lookup / ratio)
```

`run_tests.sh` の検証:

- `validate.clj` — seed 不変条件 5/5 PASS
- `query.clj … gaps` — カバレッジ・ワークリスト
- `build.sh` — `apqc-coordinator.clj` → `apqc-coordinator.wasm` (kotoba:kais Component)
- `kotoba-clj run` — `mode=0` lookup 9.0 → CBOR text、`mode=6` ratio → CBOR map

mode: 0 lookup / 1 summarize(llm-infer) / 2 coverage / 3 parent / 4 children / 5 materialize / 6 ratio。

## TODO (pilot の次)

- L1 13件 + 代表チェーンから PCF v7.4 全 L2–L5 (約 1,000+ プロセス) へ seed 拡張。
- `coverageSnapshot` lexicon 相当の集計ノード (registeredL1/totalL1 …) を kqe-query で実装。
- `materializeSubprocesses` / `getProcess` 手続きを cell のコマンドとして接続し、
  `did:web:apqc.etzhayyim.com` 配下へ登録。

<!-- coverage-worklist:auto -->
## Coverage worklist (auto — `bb query.clj <seed> gaps`)

Non-leaf nodes with zero children = where deeper `:representative` detail is still missing. `gaps: 283`. Sample:

```
GAP 1.2.1 3 Design the organizational structure
GAP 1.2.2 3 Define roles, responsibilities, and accountabilities
GAP 1.2.3 3 Define span of control and reporting relationships
GAP 1.2.4 3 Assess and refine organizational structure
GAP 1.3.1 3 Assess the organization's process and business capability maturity
GAP 1.3.2 3 Define process and business capability strategy
GAP 1.3.3 3 Develop process and business capability roadmap
GAP 1.3.4 3 Manage process and business capabilities
GAP 1.4.1 3 Develop strategic initiatives
GAP 1.4.2 3 Evaluate strategic initiatives
```
<!-- /coverage-worklist -->
