# manabi (学び) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに記録する。
honest framing (G8): できていないことは「未」と明記する。

- Actor: `did:web:etzhayyim.com:manabi` · ADR-2605261045 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行(import時 RuntimeError) ·
  ANTI-CREDENTIALISM(degrees/transcripts/GPA 発行禁止, skillAttestation のみ — G7 + §2(e)) ·
  anti-addiction UX(streaks/leaderboards/badges 禁止 — G3 + §2(d)) · minor privacy
  aggregate-only(G6) · comparative-religion(G14) · Murakumo-only · G8 非捏造 ·
  open-education resource は ROUTE のみ(accredit/grade/rank しない) · コミットはユーザー明示時のみ

## イテレーション記録

### 2026-06-02 — open-education resource registry hardening
**`registry/resources.seed.json` の fail-closed 機械床 + G14 検証ワークフローを追加。**
新規 `70-tools/scripts/audit/test_manabi_registry_seed.py`(8 invariants: parse +
unique resourceId + 全件 unverified-seed(G14) + accessUrl/https-provenance/lastVerified +
12+ jurisdictions + 許可 resourceKind 語彙 + notes の anti-credentialism/open-resource
境界 + top-level integer freshnessWindowDays)を作成、green(8 passed)。
`registry/VERIFICATION.md`(G14 三層チェックリスト: per-field license + free-access 検証 +
worldwide per-jurisdiction official/recognized-source provenance fail-closed +
ANTI-CREDENTIALISM 境界 re-check; 機械床として test を引用)を追加。honest: **0 verified**。

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
既存テスト(registry-seed)は被覆していなかった **manifest G1–G14 + 7 lexicon の教育ゲート**を新設 `methods/test_charter_gates.cljc`(**6 tests green**, standalone・network-free)で固定: (1) manifest が厳密に G1–G14。(2) **G7 anti-credentialism** — domainMasteryAttestation const `credentialClaimedAttested=false`(学位/単位を主張しない、再現可能な技能実演のみ)。(3) **G6 minor privacy** — learningSession/certPrep が learnerAgeBucket{minor, adult} discriminator(未成年は集計のみ)。(4) **G4 curriculum review** — curriculumAttestation が charterAlignmentAttestations + charterRiderScanResult{pass/fail/warn} + licenseDeclaration{Apache-2.0, CC-BY-SA-4.0}(open content)必須。(5) **G3 anti-addiction** — noAddictionUxAttestationCid + inquiryFractionPct 必須。(6) **海賊版禁止** — personalMaterialImport const `internalOnly=true` + encryptedPayloadCid + licenseAcknowledgment{owner-purchased/authored/public-domain}。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `manabi.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
