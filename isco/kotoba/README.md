# open-isco — kotoba-native pilot (Datom + clj→WASM)

ISCO Workforce Coordinator の **kotoba-datomic / kotoba-clj→WASM** 移行 pilot。
従来の `kotoba/` (TypeScript + AT-PDS 書き込み) 経路に対し、`query.ts` の
`CHARTER-VIOLATION §substrate` が要求する「中央集権 DB → kqe-over-Datom-log」
substrate (ADR-2605262130 + ADR-2605312345) 側の実装をここに置く。

実装は **OpenRouter 経由の `moonshotai/kimi-k2.7-code`** を実装エンジンとして生成し、
Claude (Opus 4.8) がオーケストレーション・検証した (actor ランタイム LLM は不変 →
ADR-2605215000 §1 に抵触しない)。

## 成果物

| ファイル | 役割 |
|---|---|
| `isco-occupations.kotoba.edn` | ISCO-08 の EAVT Datom スキーマ (`:schema-tx` + `:attributes`) と `:authoritative` seed。**全 ISCO-08 標準構造 619 ノード (10 major / 43 sub-major / 130 minor / 436 unit)、`:authoritative`(ILO ISCO-08)**。`:isco.occupation/code` が `:db.unique/identity`。 |
| `coordinator.clj` | kotoba-clj `defgraph` cell。`run(ctx)` が CBOR の `{code, mode}` を復号し、`:route → (if-edge summarize? :summarize :lookup)` で分岐。`:lookup` は `kqe-get-objects` で `isco.occupation/name` Datom を読み、`:summarize` は `llm-infer` を呼ぶ。 |
| `coordinator.wasm` | `coordinator.clj` をコンパイルした WASM Component (kotoba:kais world)。`WasmExecutor` でロード可能。 |

## ctx 契約

`run(ctx)` が受け取る CBOR map:

- `code` : text — 対象 ISCO コード (例 `"2512"`)
- `mode` : uint — `0` = 名称ルックアップ / `1` = llm 要約 / `2` = coverage 件数 (CBOR uint) / `3` = 親コード / `4` = 子プロセス数 / `5` = 子コード配列 (CBOR array) / `6` = coverage比 (CBOR map)

## ビルド & ローカル deploy 検証

ビルド/検証はこの actor 内のスクリプトで完結する。Clojure→WASM コンパイラと
WasmExecutor は **kotoba substrate エンジン(sibling repo)の汎用 `kotoba-clj` CLI**
が担い、actor はそれを呼ぶだけ(kotoba には ISCO 固有コードは無い)。

```sh
./build.sh        # coordinator.clj → coordinator.wasm (kotoba-clj CLI; KOTOBA_DIR で kotoba checkout 指定可)
./run_tests.sh    # seed 整合(bb) + gaps + ビルド + WasmExecutor スモーク(lookup / ratio)
```

`run_tests.sh` の検証:

- `validate.clj` — seed 不変条件 5/5 PASS(unique / no-nil / parent 解決 / declared / sourcing)
- `query.clj … gaps` — カバレッジ・ワークリスト(権威 ISCO-08 全件 → gaps=0)
- `build.sh` — `coordinator.clj` → `coordinator.wasm` (kotoba:kais Component)
- `kotoba-clj run` — `mode=0` lookup 2512 → CBOR text、`mode=6` ratio → CBOR map

mode: 0 lookup / 1 summarize(llm-infer) / 2 coverage / 3 parent / 4 children / 5 materialize / 6 ratio。

## TODO (pilot の次)

- `:summarize` を kqe で引いた名称 (CBOR bytes) からプロンプト生成するよう拡張
  (現状は code 文字列を渡す最小実装)。
- `:summarize` を kqe で引いた名称 (CBOR bytes) からプロンプト生成するよう拡張
  (現状は code 文字列を渡す最小実装)。
- coordinator cell を `kotoba serve` の常駐 WasmExecutor に登録し、619 DID の
  `did:web:isco.etzhayyim.com:occupation:{code}` と接続。

<!-- coverage-worklist:auto -->
## Coverage worklist (`bb query.clj <seed> gaps`)

`gaps: 0` — ISCO-08 標準構造を全件網羅 (`:authoritative`)。深掘りギャップなし。
<!-- /coverage-worklist -->
