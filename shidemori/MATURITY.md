# shidemori (死出守) — Maturity Ledger

`/loop` 進捗台帳。各イテレーションで成熟度を上げ、ここに記録する。honest framing:
できていないことは「未」と明記する。

- Actor: `did:web:shidemori.etzhayyim.com` · ADR-2605263800 · **R0 scaffold**
- 不変条件(全イテレーション厳守): R0 では cell 非実行 · dispatch なし ·
  NON-mortuary / non-commercial / non-legal-advice 境界(G14, ADR-2605263800) ·
  PII平文禁止 · Murakumo-only · G8 非捏造 · コミットはユーザー明示時のみ

## イテレーション記録

- 2026-06-02 registry hardening: WROTE fail-closed seed invariants test `70-tools/scripts/audit/test_shidemori_registry_seed.py` (8 tests, `PYTEST_DISABLE_PLUGIN_AUTOLOAD=1 python3 -m pytest … -q` → green) pinning the death-registration seed (`registry/registries.seed.json`, 130 entries / 31 jurisdictions): JSON parse + non-empty `registries`, unique `registryId`, all `unverified-seed` (G14), non-empty `accessUrl`+`provenance`+`lastVerified`, ≥12 distinct jurisdictions, `recordKind` ∈ {death-registration-authority, death-certificate-issuer, burial-cremation-permit, civil-registry-office, intl-guidance}, every `notes` non-empty + references the NON-mortuary/non-commercial boundary, top-level integer `freshnessWindowDays`. WROTE `registry/VERIFICATION.md` — G14 three-tier human checklist foregrounding re-verification of the statutory registration DEADLINE against the cited law (a wrong deadline is harmful), per-jurisdiction official-source provenance (fail-closed), non-mortuary/non-commercial boundary re-check; honest (G8): 0 entries verified. Provenance check requires http(s) (not https-only) because two genuine official sources (koreanlii.or.kr, home-affairs.gov.za) are http — real data not masked.

### 2026-06-17 (loop) — manifest+lexicon charter-gate test (構造ゲート pin)
既存 registry-seed テストが被覆していなかった **manifest G1–G13 + 5 lexicon の dignity ゲート(ロスター最密 const 台帳)**を新設 `methods/test_charter_gates.cljc`(**7 tests green**, standalone・network-free)で固定: (1) manifest 厳密に G1–G13。(2) **G3 非終末論** memorialNft const `afterlifeDoctrineImposed=false`。(3) **G7 no-embalming** externalMortuaryEngagement const `embalmingChemicalsUsed=false`。(4) **G10 waqf+green-burial** cemeteryLand const waqfInalienabilityAttested + biodegradableShroudPineCasketOnlyAttested=true。(5) **dignity 台帳** silenShidemoriReview const: eschatological=0 / commercialMemorialSoftware=0 / embalmingChemical=0 / mortuarySurveillance=0 / stateLicensedMortuaryFirstParty=0 / mandatoryBurial=0 / singleDoctrinalAfterlifeMonopoly=0 / commercialMemorialAi=0 / waqf-compliant=10000 / guardian-vocation-flow=10000。(6) **G9 free conscience** memorialNft が memberDirectiveCid 必須 + no-doctrine-imposed 選択肢。(7) chinkonRemembrance const openToCommunityAttested=true(cross-doctrinal)。`run_tests.sh` 新設。working-tree edits only。

> **2026-06-17 substrate-native migration (ADR-2606160842):** the charter-gate test above was ported Python→Clojure (`methods/test_charter_gates.py` → `methods/test_charter_gates.cljc`, ns `shidemori.methods.test-charter-gates`, reads the lexicons via cheshire/edn) and the Python was pruned. Run via `./run_tests.sh` (now `exec bb`) or `bb run test:charter` (all 34 charter suites; 244 tests / 924 assertions green). Assertions unchanged (1:1 port).
