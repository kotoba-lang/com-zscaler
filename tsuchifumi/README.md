# tsuchifumi 土踏み — earthing-EMF Wellbecoming observatory + おせっかい actor

> アーシング (earthing/grounding) が十分に制度化されていないこと × 環境電磁界
> (ambient EMF) 曝露を **Wellbecoming 観測 + system-dynamics 可視化 + risk 分析 +
> 透明な おせっかい (ossekai) nudge**（social-proto / atproto）として扱う
> clj-native Tier-B actor。**ADR-2606212000** (R0)。

**土踏み** = 「土を踏む」— 素足で土に触れる earthing の核（土踏まず＝足の弓）。
power-mirror 系譜（inochi 命 = 生態圏 / shiori 栞 = 人の detractor）の、
**環境電磁界 × 接地アクセスの空白**に向けた sibling。これまで誰も担っていなかった
領域を埋める。

## いちばん大事な不変条件 — 認識論的誠実さ (G2, 反疑似科学)

この領域は **科学的に争いがある**。主流科学が確立しているのは EMF の **熱的** 限度
(ICNIRP/WHO) と曝露の **事実** であって、**非熱的** EMF 健康影響や **アーシング療法**
の臨床効果は **未確立 (contested)**。tsuchifumi はこれをデータモデル自体で分離する：

| 量 | 性質 | tier |
|---|---|---|
| exposure-load（ambient-EMF + device-hours + indoor-fraction） | 測定事実 | :established / :emerging |
| earthing-deficit（接地・緑地アクセス／制度の不足） | 測定事実（**制度の空白**） | :established |
| **health-burden（生体電気的負荷）** | **仮説** | :contested / :anecdotal |

- health-burden は **必ず resting evidence tier 付き**で報告される。:contested/:anecdotal
  な負荷は **確立した害として断定しない**（→ `:await-evidence`）。
- 制度的 earthing-deficit は **確立した緑地・屋外時間の wellbeing 証拠**に立つので、
  健康論争に関係なく **no-regret な relief 目標**として常に有効。
- **OBSERVATORY + MODEL + NUDGE のみ** — 非診断・非治療・**何も売らない**（アーシング
  マット／機器／アフィリエイトは構造的に表現不能）。

## 4つの成果物（ユーザー依頼の4点）

1. **分析・可視化** — `analyze.cljc`：region 別に exposure-load × earthing-deficit を
   on-read で採点し、relief verdict（5種）+ population-weighted **relief-gap**（actionable
   worklist）+ evidence honesty を出力。
2. **system dynamics 可視化** — `sysdyn.cljc`：Forrester/Meadows stock-and-flow
   モデル（stocks: E ambient-EMF / A earthing-access / I grounding-infrastructure /
   B 仮説 burden）を Euler 積分。**distribution-only**（p10/p50/p90 の ensemble、G6 点推定
   不可）。`:neglect`/`:baseline`/`:relief` の3 scenario を比較し、**制度化が burden 曲線を
   下げる relief dividend** を示す。`viz.cljc` が実データから自己完結 HTML（canvas）を生成。
3. **risk 分析** — `risk.cljc`：driver 別 risk register（likelihood × impact ×
   **evidence-confidence**）+ Meadows **leverage points**。疑似科学 driver は
   evidence-discount で register 最下位に落ちる（G2 を register が体現）。
4. **social-proto / atproto で ossekai する actor** — `social.cljc`：
   app.bsky.feed.post 形の **dry-run おせっかい post** を起草。fear/sales/diagnosis/
   EMF-harm token を scan して **拒否**、dry-run のみ・**no-server-key**、各 post は
   **ossekai (御節介) に渡す proposal**（御節介 が consent-bound + on-chain-logged で carry）。

## co-scientist — 特定 (identify) + 分析 (analyze)

`coscientist.cljc` — ibuki co-scientist (ADR-2606201200) の **Generate → Reflect →
Rank → Evolve → Meta-review** トーナメントを、**charter-clean な intervention CATALOG**
（LLM free-write 不可。fear/sales/diagnosis mechanism は構造的に表現不能）に対して実行し、
何を **やるべきか／研究すべきか** を **特定**・**分析**する。

**この actor の核心的な設計手 (G2 honesty をリサーチ・プログラムに適用)**：候補は
2トラックに分かれ、**異なる目的関数**でランクされる：

- **`:action`** — ≥emerging な証拠に立つ **no-regret** 介入。`utility = relief ×
  wellbeing × evidence-weight / cost` でランク → **特定**された最上位を **ossekai 御節介**
  へ（dry-run, consent-bound）。
- **`:research`** — **contested** な仮説（**行動も断定もしない**）。`VoI = relevance ×
  (1−confidence) / cost`（value-of-information）でランク → **suimin / mitooshi** へ
  evidence-synthesis を依頼。

**:action トラックに contested 候補が来たら veto**（contested な主張は **研究はしても
行動しない**）— これが research programme を誠実に保つ安全性質。`autorun` heartbeat は
毎 beat この identified hypothesis を ledger に記録する。

```bash
# 5. co-scientist: 特定 + 分析（action は ossekai へ、research は suimin/mitooshi へ）
bb --classpath 20-actors 20-actors/tsuchifumi/methods/coscientist.cljc
```

## Gates

| ID | name | rule |
|---|---|---|
| G1 | non-diagnostic-non-therapeutic | 診断/治療/治癒しない。`:diagnose/:treat/:cure` 表現不能。care → mitate/iyashi/kokoro |
| G2 | evidence-honesty-anti-pseudoscience | health-burden は tier 付き；contested を害と断定しない；practice nudge は ≥emerging に立脚；risk-score は tier で discount |
| G3 | no-person-data | aggregate cohort/region のみ；`:person/health|:biometric` 表現不能 |
| G4 | ossekai-transparent-nudge-no-fear | PROPOSE のみ、御節介 が carry；fear/manipulation 拒否 |
| G5 | no-commerce | 何も売らない；`:product/:buy` 表現不能；sales token 拒否 |
| G6 | model-is-hypothesis-distribution-only | sysdyn は p10/p50/p90 のみ；点推定不可；forecast → mitooshi |
| G7 | synthetic-seed | R0 seed は :synthetic；実データは operator/Council step |
| G8 | no-server-key-kotoba-eavt-native | state = kotoba EAVT；heartbeat は鍵なし・network I/O なし；live carry は member-signed + 御節介 |

## 使い方 (babashka)

```bash
# 1. 分析 + relief map
bb --classpath 20-actors 20-actors/tsuchifumi/methods/analyze.cljc

# 2. system dynamics の scenario 比較
bb --classpath 20-actors 20-actors/tsuchifumi/methods/sysdyn.cljc

# 3. risk register + leverage points
bb --classpath 20-actors 20-actors/tsuchifumi/methods/risk.cljc

# 4. dry-run おせっかい (ossekai) post batch
bb --classpath 20-actors 20-actors/tsuchifumi/methods/social.cljc

# 可視化 HTML を生成（自己完結 canvas, 実データ）
bb --classpath 20-actors 20-actors/tsuchifumi/methods/viz.cljc
#   → 20-actors/tsuchifumi/viz/sysdyn-risk.html

# 持続永続化: heartbeat（analyze+risk → append-only content-addressed ledger）
bb --classpath 20-actors 20-actors/tsuchifumi/methods/autorun.cljc

# tests
./20-actors/tsuchifumi/run_tests.sh   # 56 tests / 197 assertions green
```

## ファイル

```
20-actors/tsuchifumi/
├── manifest.edn
├── README.md / CLAUDE.md / run_tests.sh / .gitignore
├── kotoba/
│   ├── ontology.tsuchifumi.edn   # EAVT schema + enums + tier-weights + 負の空間
│   └── seed.edn                  # :synthetic regions + evidence catalog + risk drivers
├── methods/
│   ├── tsuchifumi_edn.cljc       # loader
│   ├── analyze.cljc              # relief gate + evidence honesty
│   ├── sysdyn.cljc               # system dynamics (distribution-only)
│   ├── risk.cljc                 # risk register + Meadows leverage
│   ├── coscientist.cljc          # 特定+分析 tournament (action vs research track)
│   ├── social.cljc               # atproto dry-run ossekai posts
│   ├── viz.cljc                  # self-contained HTML generator
│   ├── kotoba.cljc / autorun.cljc# 持続永続化 (ledger + heartbeat)
│   └── test_*.cljc               # 8 suites
└── viz/sysdyn-risk.html          # generated visualization
```

## Routes

| route | to |
|---|---|
| おせっかい nudge を carry | **ossekai 御節介**（consent-bound + on-chain-logged） |
| evening-light / circadian | **suimin 睡眠** |
| forecasting | **mitooshi 見通し** |
| care（診断・治療） | **mitate / iyashi / kokoro** |
| 取-holder driver concentration | **danjo / tsumugi / keizu** |
| biosphere restoration | **inochi 命** |

_All R0 data is :synthetic. Real environmental-EMF / public-health / greenspace data,
live atproto carry, and IPFS/IPNS publication are operator/Council steps (G7/G8)._
