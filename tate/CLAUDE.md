# tate 盾 — citizen legal-defense concierge (worldwide)

**DID**: `did:web:etzhayyim.com:actor:tate` · **Tier**: B · **Status**: 🟢 R2 (registry maturity) ·
**ADR**: 2606112301 (R0 JP) + **2606112400 (R1 worldwide)** · **depends**: 2606112201
(kaiyaku) · 2605312500 (kurashimori) · 2605262700 (chigiri UPL prior art) · 2606060900
(tasuke) · 2605231525 (no-server-key) · 2605215000 (Murakumo-only) · 2605312345
(Datom = canonical state)

**Jurisdictions (R1)**: `:jp :us :eu :uk :de :kr :fr :au :ca :it :es :nl :br :tw :sg :in :cn :pl :se :at :pt :ie :ch :dk :fi :no :mx :be :ar :nz` — representative 30 of ~193, plus `us-states.edn` (**50/50 州完遂** — :us 通知に州が分かれば州規則を開示追記、州不明は honest degrade)
(`coverage_report.cljc` measures + names the gap, G10; the worklist drops entries off
automatically once covered). Uncovered jurisdictions degrade to
`:unknown-jurisdiction` — tate **never guesses foreign law**.

## What this is

The **defensive paper layer** for a member as a private individual (盾 = shield — it only
ever defends). Two legs over the member's OWN documents:

1. **不利条項スキャン** (`methods/terms_scan.cljc`) — the member's consumer ToS /
   credit-card member agreements / B2B 法人契約 matched against a coded clause-pattern
   registry (`data/clause-patterns.edn`): 自動更新トラップ, 過大違約金, 全部免責,
   年14.6%超の遅延損害金, 専属管轄, リボ自動設定, 支払停止の抗弁の放棄, 無限賠償,
   競業避止, 長期支払サイト, 知財包括譲渡, 最低期間+自動更新ロック. Each flag =
   **pattern + DISCLOSED statutory anchor** (消費者契約法8–10条 / 民法548条の2 /
   割賦販売法 / 下請法 / 独禁法) + **route**: `:kurashimori` (rights) / `:kaiyaku`
   (sever the tie) / `:referral` (professional) / `:info`. **Never a validity verdict.**
   Verified entries also carry **`:clause/source-url`** — the primary legal source
   (official legislation portal) the anchor was checked against, recorded **in the EDN
   itself** so the kotoba Datom log is self-provenancing. G10: a URL is recorded only when
   actually verified (never guessed/remembered); `coverage_report` names the provenance gap
   (worklist, drops off as entries are back-filled).
2. **法的手続き応答支援** (`methods/respond_plan.cljc`) — notices an individual RECEIVES
   (支払督促 / 少額訴訟呼出 / 訴状 / 行政処分 / 内容証明) classified against a coded
   procedure registry (`data/procedure-registry.edn`) → DISCLOSED deadline **rules**
   (民訴391・393条 督促異議 2週間, 373条 通常移行, 378条 異議, 159条 擬制自白;
   行審法18条1項 3月; 行訴法14条1項 6箇月) + response options (督促異議 / 答弁書 /
   通常移行申述 / 審査請求 / 書面回答) + self-submit checklist + referral triggers.

## Public face (atproto actor + mesh-distributable coverage)

tate is a **resolvable atproto actor** so people can find/identify it — handle
`tate.etzhayyim.com`, DID `did:web:etzhayyim.com:actor:tate`, registered in the canonical
`00-contracts/schemas/actor-profile-seed.kotoba.edn` SSoT (backs did.json + getProfile per
ADR-2606013800) with static fallbacks `public/actor/tate/{did.json,profile.json}`
(verificationMethod `[]` — **no-server-key**, did:web TLS trust root, ADR-2605231525).

Its **only** public surface is **(a)** an anonymized **AGGREGATE coverage digest** and
**(b)** the crawlable static site — **a member's OWN private documents are never published
(G1)**. `methods/coverage_publish.cljc` builds the digest from `coverage_report` (registries
ONLY — never `seed-member-docs`), selects an explicit aggregate allowlist, and
content-addresses it (`methods/cid.cljc`, CIDv1/raw/sha2-256, ipfs-parity) → mesh-distributable
+ verifiable (`public/actor/tate/coverage.json`). `member-leak?` is a **structural,
test-enforced G1 guard**: a member-document marker can never appear in the published bytes.
(The full mesh-RUNTIME component is a staged follow-up — the per-actor `mesh.cljc` pattern is
not yet merged to main.)

**架空請求 guard (G6)**: genuine 支払督促/訴状 arrive by **特別送達 only**. Court
vocabulary on SMS / email / 普通郵便 → `:suspected-fake`: the plan's first step is
`do-not-contact-sender`, evidence is preserved, and the member routes to **tasuke 助 /
警察相談 #9110 / 消費者ホットライン 188**. No deadlines or options are offered on a fake.

## Hard gates (constitutional — read before any change)

- **G1 member-principal, own documents only.** R0 seeds are fully `:synthetic`; live
  member docs are consent-bound + encrypted (`com.etzhayyim.encrypted.*`).
- **G2 non-adjudicating.** A flag is a pointer to a disclosed statute
  (`:verify-current-law true` everywhere — statutes amend), never "this clause is
  invalid". Report language stays 可能性/専門家確認 (test-enforced).
- **G3 UPL (弁護士法72条).** No representation — `_make_option` **raises** on
  `:representation`; every option is `:self-submit` / `:self-decide`,
  `submitted_by: member`. No individualized legal judgment; options come verbatim
  from the coded registry.
- **G4 deadline honesty.** tate **never computes a calendar date** — it emits the
  disclosed rule text + anchor + `verify_service_date: true`; the member confirms
  when they were actually served.
- **G5 context honesty.** Consumer anchors never fire on `:b2b` docs (disjoint by
  construction in `scan_doc`); B2B routes referral-forward instead.
- **G7 referral-forward.** 本訴 / claim > the jurisdiction's refer-over line
  (¥600,000 · $10,000 · £10,000 · €5,000 — representative) / 執行段階 / 重大処分
  always carry the jurisdiction's directory: 法テラス · state bar + legal aid ·
  Citizens Advice · Verbraucherzentrale · ECC-Net.
- **G10 jurisdiction-honesty (R1).** Anchors and procedures **never cross
  jurisdictions** (structural filters in `scan_doc` / `classify` — a 消費者契約法
  anchor cannot fire on a US doc; JP 支払督促 vocabulary under a `:us` notice does not
  match the JP procedure). Per-jurisdiction UPL anchors (弁護士法72条 / state UPL /
  Legal Services Act 2007 / RDG) live in `data/jurisdictions.edn`; the G3 gate itself
  is global and structural. The G6 guard generalizes via `:proc/genuine-channels`
  (特別送達 · personal service / certified mail · förmliche Zustellung · court post ·
  formal service) with multilingual court-vocab trip-wires.

## Universal invariants (parametric tests — 新規手続きに自動適用)

| scope | invariant |
|---|---|
| 全手続き | options + anchored deadline rules + genuine-channels + refer-when 必在 (lint); verify-service-date 必在 (G4); UPL: representation unrepresentable, member self-submit (G3) |
| 非 civil 全トラック | ≥1 `:opt/protective` (member を守る一手) 必在 |
| :housing | `no-self-help-protection` 必在 — 退去は裁判所命令のみ |
| :enforcement | 法定の差押え保護範囲 (3/4・25%・P-Konto・SBI・1/5 …) を必ず開示 |
| :insolvency | kaiyaku 縁-ledger 突合 (前払金=債権) を必ず案内 |
| :family | kokoro 心 (Wellbecoming サポート) routing 必在 |
| :dl/critical | census (report) + 先頭ソート (plan) + ⚠ バナー (表示) の三層 |
| fake-guard | 全 trigger 語彙が自動 trip-wire; SMS/email は宣言なき限り偽疑い |

## Non-goals

N1 not a law firm / no advice · **N2 defensive only** — never drafts claims/suits
AGAINST others, no 取立 · N3 no evasion of lawful obligations (genuine debt/deadline
surfaced honestly) · N4 never scores the counterparty (clauses are flagged, companies/
persons are not — no blacklist, 反個人主義) · N5 kurashimori owns クーリングオフ/返金,
toritsugi owns proactive 行政手続 — tate owns the defensive response surface ·
N6 刑事 out of scope → immediate 弁護士 referral. **N2 補足 (wave 8)**: 自分自身の雇用・居住関係を守る応答 (解雇への Kündigungsschutzklage / ACAS EC / 労働審判の検討) は『防御』であり N2 の攻撃的訴訟支援には当たらない — 第三者への請求の組成は引き続き非目標.

## Boundaries (who owns what)

| Concern | Owner |
|---|---|
| 不利条項の検出 + 法的手続きへの応答 (防御) | **tate** (this actor) |
| クーリングオフ / 返金 / 消費者庁 escalation (rights) | **kurashimori** |
| 解約 / 退会の実行 (縁切り executor) | **kaiyaku** (tate routes `:kaiyaku` hits there) |
| 架空請求 / 詐欺被害の回復 | **tasuke** (G6 fake-guard routes there) |
| proactive 政府手続き concierge | **toritsugi** |
| legal-procedure substrate (registry 基盤) | **chigiri** |
| debtor-initiated formal insolvency self-petition (自己破産・個人再生 own-filing) | **saisei** (tate's `:insolvency` track stays creditor-side only, N2) |

## Layout

```
20-actors/tate/
├── CLAUDE.md                      # this file
├── manifest.edn                   # actor manifest (5 cells, 9 gates, 6 non-goals)
├── data/
│   ├── jurisdictions.edn          # jurisdiction registry: UPL anchor + directories (R1)
│   ├── clause-patterns.edn        # jurisdiction-keyed clause registry (128 shapes, 30 juris — 実 R1 全 29 法域が ≥4 patterns、:eu のみ 2(越境 instruments メタ); :clause/source-url 一次ソース URL を verified entry に記録, coverage が provenance gap を可視化)
│   ├── procedure-registry.edn     # jurisdiction-keyed procedure registry (181 procs; :civil 36 + :labor 29 + :housing 29 + :enforcement 29 + :insolvency 29 + :family 29 — track×juris matrix; fake-guard 語彙 registry 自動導出; :dl/critical 期限先頭表示; **非civil全手続きに protective 選択肢必在**; :dl/source-url 一次ソース URL を verified deadline-rule に記録 — proc-level provenance 149/181 (rule-level 176、82%)、:jp + :de + :uk + :fr + :kr + :it + :es + :nl + :au + :ca + :pl + :at + :ch + :nz + :sg + :tw + :se + :fi + :pt 完備 (19 法域) + :us(連邦完備: 15 USC 1673/42 USC 2000e-5/FRCP/FRBP — 残は州 generic 「各州…」anchor のみ=単一ソース化不可で構造的に対象外)/:ar(LCT 20.744/Ley 24.522 裏取り済、残 ley23789/CPCCN/CCyC)/:ie(5/6)/:cn(2/5)/:no(5/6)/:in/:be 進行、:ar は InfoLEG (LCT verNorma id=25552 / Ley 24.522 id=25379) で裏取り、:us 連邦は uscode.house.gov + uscourts.gov で裏取り、:ie は Irish Statute Book (1926/act/18 / 2018/si/427) で裏取り、:us 連邦は uscode.house.gov + uscourts.gov で裏取り、:ie は Irish Statute Book (1926/act/18 / 2018/si/427) で裏取り、:pt は Diário da República (CPC lei/2013-34580575 / Código do Trabalho lei/2009-34546475 / CIRE decreto-lei/2004-34529075 / NRAU lei/2006-34578375 / DL 269/98 detalhe/269-1998-566629) で完備、:cn は 国家法律法规数据库 flk.npc.gov.cn (劳动合同法 detail?id=…74d7106b3 / 民事诉讼法 detail?id=…56910a05) で裏取り、:fi は Finlex (työsopimuslaki 55/2001 / Konkurssilaki 120/2004 / AHVL 481/1995 / ulosottokaari 705/2007 / oikeudenkäymiskaari 4/1734) で完備、:se は Sveriges riksdag SFS (LAS 1982:80 / Lag 1990:746 / äktenskapsbalk 1987:230 / utsökningsbalk 1981:774 / konkurslag 1987:672 / jordabalk 1970:994) で完備、:tw は 全國法規資料庫 (勞基法 N0030001 / 民訴 B0010001 / 家事 B0010048 / 強制執行 B0010004 / 破產 B0010006 / 租賃住宅條例 D0060125) で完備、:sg は Singapore Statutes Online (ECA2016/WC1961/SCTA1984/IRDA2018/CLPA1886/SCJA1969) で完備、:nz は NZ Legislation (Employment Relations Act 2000/0024 / Residential Tenancies Act 1986/0120 / Disputes Tribunal Act 1988/0110 / Companies Act 1993/0105 / Care of Children Act 2004/0090 / District Court Act 2016/0049) で完備、法令単位で back-fill 継続)
│   ├── us-states.edn              # :us 州サブ管轄 (small-claims 上限 + answer 期限 + ARL)
│   └── seed-member-docs.edn       # SYNTHETIC member contracts + notices, intl (G1)
├── methods/                       # clj/bb (.cljc) — kotoba-native; py→clj port complete (ADR-2606160842)
│   ├── terms_scan.cljc            # 不利条項 scanner (non-adjudicating flags, G10 filter)
│   ├── respond_plan.cljc          # response planner + fake-notice guard (G6/G10)
│   ├── coverage_report.cljc       # honest jurisdiction coverage + named gaps (G10)
│   ├── coverage_publish.cljc      # PUBLIC anonymized AGGREGATE coverage digest (mesh-distributable, content-addressed; G1 member-leak? guard)
│   ├── cid.cljc                   # kotoba IPFS content-address (CIDv1/raw/sha2-256, ipfs-parity)
│   ├── site_gen.cljc              # crawlable static site (FAQPage JSON-LD + sitemap — Google 可視化)
│   ├── case_actors_gen.cljc       # 1 case = 1 keyless actor (profile + case.json/checklist DL + 相談先)
│   ├── datom_emit.cljc            # kotoba Datom-log (EAVT) emitter
│   └── edn.cljc                   # EDN load/serialize helpers (registry + seed readers)
├── tests/                         # clj/bb (.cljc) — bb run_tests.sh (129 tests / 7,865 assertions)
│   ├── test_terms.cljc
│   ├── test_respond.cljc
│   ├── test_site.cljc
│   ├── test_coverage.cljc
│   ├── test_coverage_publish.cljc
│   ├── test_case_actors.cljc
│   └── test_kotoba.cljc
└── out/                           # GENERATED — do not hand-edit
    ├── clause-readout.md
    ├── kaiyaku-handoff.edn          # :kaiyaku ルートの機械可読ハンドオフ (actors compose)
    ├── response-plans.md
    ├── coverage-report.md
    └── tate-datoms.kotoba.edn
```

## Run

```bash
# clj/bb (babashka), run from the repo root (classpath = 20-actors). NOT python.
bash 20-actors/tate/run_tests.sh   # full suite: 129 tests / 7,865 assertions green

# ad-hoc, from repo root:
bb --classpath 20-actors -e '(require (quote [tate.methods.coverage-report :as c])) (print (c/report (c/coverage)))'        # honest coverage report
bb --classpath 20-actors -e '(require (quote [tate.methods.coverage-publish :as p])) (print (p/coverage-json))'             # PUBLIC anonymized coverage digest (mesh)
bb --classpath 20-actors -e '(require (quote [tate.methods.datom-emit :as d])) (println (count (d/emit)))'                  # kotoba Datom-log (EAVT) count
```

## Do not

- Do not emit a validity verdict, drop a statutory anchor, or apply a consumer-law
  anchor to a `:b2b` document — G2 / G5 (tests enforce).
- Do not add a `:representation` option kind or any claim-drafting / offensive leg —
  G3 / N2 (`_make_option` raises; tests enforce).
- Do not compute a calendar deadline or offer options on a `:suspected-fake` notice —
  G4 / G6 (tests enforce: fake plans have no deadlines/options and open with
  do-not-contact-sender).
- Do not score or blacklist counterparties — N4.
- Do not ingest real member documents into `data/` — seeds stay `:synthetic`; live
  docs are consent-gated + encrypted (ADR-2605181100).
- Statutory rules in the registries carry `:verify-current-law true` — when amending
  them, cite the current statute text, never memory.
- Do not answer for an uncovered jurisdiction (no LLM-guessed foreign law — the most
  dangerous failure mode this actor can have), and never let an anchor or procedure
  cross jurisdictions — G10 (tests enforce: adversarial JP-keywords-on-US-doc fires
  nothing; `:br` notice gets no deadlines/options). Adding a jurisdiction = one
  `jurisdictions.edn` entry + patterns + procedures + tests; no code change.
