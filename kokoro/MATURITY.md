# kokoro (心) — Maturity Ledger

`/loop` の進捗台帳。各イテレーションで **1項目** だけ成熟度を上げ、ここに記録する。
honest framing (G8): できていないことは「未」と明記する。

- Actor: `did:web:kokoro.etzhayyim.com` · ADR-2605263700 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行(import時 RuntimeError) ·
  非臨床(NOT clinical / NOT diagnosis/treatment, G3) · 暗号化エンベロープ必須(G4) ·
  人間-in-loop(G6) · 商用メンタルヘルスSW/AIセラピー禁止(G7/G8) ·
  G14 unverified-seed-only(crisis line を未検証で routing しない) · コミットはユーザー明示時のみ

## イテレーション記録

### iter-1 (2026-06-02)
**worldwide crisis-support directory + machine floor + verification workflow。**
`20-actors/kokoro/registry/support-lines.seed.json` の世界規模 crisis-support
ディレクトリ(127件 / 31管轄 + 国際ディレクトリ; 全件 `unverified-seed`, G14)に対し、
fail-closed pytest 不変条件 `70-tools/scripts/audit/test_kokoro_registry_seed.py`
(8 tests green: parse/非空 lines · lineId 一意 · 全件 unverified-seed · contact+https
provenance+ISO lastVerified · ≥12管轄 · supportKind 許可集合 · notes 非空+非臨床
support-routing 境界参照 · 整数 freshnessWindowDays) を追加し、G14 三層人手チェックリスト
`20-actors/kokoro/registry/VERIFICATION.md`(SAFETY-CRITICAL: 各 contact 番号の公式
ソース再検証を前景化 / 管轄ごと公式ソース provenance チェック fail-closed / 非臨床
support-routing 境界再確認 / honest: 0件 verified, 90日 tighter freshness)を作成。
