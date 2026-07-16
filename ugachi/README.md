# ugachi (穿ち) — the §2(l) extraction RISK-GATE

**DID**: `did:web:etzhayyim.com:ugachi` · **Namespace**: `com.etzhayyim.ugachi.*`
**ADR**: ADR-2606161800 (clj-native R0) · **Axis**: ADR-2606161700 (§2(l) v3.2)
**Status**: R0 — clj-native, kotoba-Datom-native, tests green

## Overview

採掘・採油は一律禁止ではない。A proposed extraction project (mine / well / quarry / brine)
is authorized ONLY by passing the **multi-generational (子・孫) × wellbecoming risk gate**.
ugachi is that gate, made executable — it makes the charter change real: the *same measured
rule* refuses a deep-sea-nodule / new-coal / monopoly-entrenching / no-consent project AND
permits-design a reversible, remediated, supply-diversifying, consented one.

**ASSESSMENT + R0 DESIGN ONLY — ugachi never digs.** Live actuation is Council Lv7+ gated.

## Verdict

`{:refuse :route-to-recovery :propose-r0 :insufficient-evidence}`, decided in order:
no-consent → carbon-positive → monopoly-entrenchment → irreversible-multigen-harm →
(viable recovery → route to kanayama) → (transparent + descendant-serving → propose-r0) →
insufficient. Hard refusals precede recovery routing.

## Run

```bash
./20-actors/ugachi/run_tests.sh                               # 14 tests / 37 assertions
bb --classpath 20-actors 20-actors/ugachi/methods/gate.cljc   # print stewardship gate
```

R0 synthetic seed → 3 propose-r0 · 1 route-to-recovery · 5 refuse · 2 insufficient.

## Constitutional

Gates G1–G9 + non-goals N1–N5 in `manifest.edn`; rationale in `CLAUDE.md` + ADR-2606161800.
Apache 2.0 + Charter Compliance Rider v3.2. Pairs with kanayama (recovery) / busshi /
rare-earth-coverage (concentration) / kamado / abaki / inochi.
