# kyoninka 許認可 — Maturity

**Status: R0** · ADR-2606272337 · did:web:etzhayyim.com:actor:kyoninka

| Axis | State | Notes |
|---|---|---|
| Profile registered | ✅ | `manifest.edn` → `bb gen:tier-b-actors` → INFRA_ACTORS; static `did.json`+`profile.json` published |
| Domain logic | ✅ | `methods/procedure.cljc` — jurisdiction rulebook + PermitGovernor invariants (bb-runnable, dependency-free) |
| Contract tests | ✅ | `methods/test_procedure.cljc` — 5 tests / 14 assertions green |
| Web visualization | ✅ | `methods/site_gen.cljc` → `public/kyoninka/` (index + 4 deployment pages); deploy = operator step |
| Runnable actor | ✅ | langgraph-clj StateGraph in `orgs/etzhayyim/com-etzhayyim-kyoninka` (10 contract tests green) |
| kotoba lexicons | 🟡 R1 | `com.etzhayyim.kyoninka.*` named in manifest; schema EDN to land |
| Live ingest / signoff | ⏳ | Council Lv6+ + operator gate; reg-LLM real model (Murakumo) = R1 |
| Self-key (did:key) | ⏳ R1 | present-only Ed25519, seed sealed (no-server-key, ADR-2605231525) |

## R0 → R1 checklist

- [x] Tier-B actor profile + registry registration
- [x] PermitGovernor invariants (legal hard/soft) + tests
- [x] 手続き web viz (flow + jurisdiction matrix + readiness board)
- [x] static published DID + profile (resolvable fallback)
- [ ] `00-contracts/schemas/kyoninka-*.kotoba.edn` lexicons
- [ ] reg-LLM real advisor on Murakumo fleet (G9)
- [ ] jurisdiction rulebook curation with counsel (G5; current = illustrative)
- [ ] worker deploy of `/kyoninka` + Search Console (operator step)
