# 20-actors/ugachi — CLAUDE.md

## What this is

**ugachi 穿ち** — the **§2(l) extraction RISK-GATE** actor. 採掘・採油は一律禁止ではない
(ADR-2606161700): a proposed extraction project is authorized ONLY by passing the
multi-generational (子・孫) × wellbecoming risk gate. ugachi is that gate, made
**executable** — it proves the charter change is not a slogan (it genuinely refuses
catastrophic/monopolistic projects AND genuinely permits-design the stewarded ones).

**ASSESSMENT + R0 DESIGN ONLY — ugachi never digs (採掘しない).** No actuation method
exists; live extraction is Council Lv7+ gated, never performed here.

`did:web:etzhayyim.com:ugachi` · `com.etzhayyim.ugachi.*` · ADR-2606161800 · clj-native R0.

## The gate (verdict algebra, `methods/gate.cljc`)

`verdict` → `{:refuse :route-to-recovery :propose-r0 :insufficient-evidence}`, in order:

1. no consent → `:refuse :no-consent` (G5, land sovereignty + community)
2. carbon `:net-positive` → `:refuse :carbon-positive` (G6 / §2(d))
3. monopoly-effect `:entrench` → `:refuse :monopoly-entrenchment` (G3 / §1.12; *diversify* is favorable)
4. `net-irreversibility = irreversibility·(1−remediation) ≥ 0.5` → `:refuse :irreversible-multigen-harm` (G3 / §2(d))
5. recovery-alternative `:viable` → `:route-to-recovery` (kanayama — recovery-first)
6. transparent (on-chain+open-source+1SBT=1vote) AND descendant-benefit ≥ 0.5 → `:propose-r0` (design-only)
7. else → `:insufficient-evidence`

**Hard refusals precede recovery routing** — a refused project is never "fixed" by routing
(meta-invariant: no failing project anywhere returns a permit; test-enforced).

## Hard invariants (proven by tests)

- **G1 採掘しない** — no `:ugachi/actuate` / `:ugachi/extract` attribute; assessment + R0 design only.
- **G2 not by-name** — `:ugachi.gate/by-industry-name` unrepresentable; the rule is harm-to-子孫.
- **G2/G5 stewardship ledger, NEVER a target-list** — the report says so, in words.
- refuse-precedes-recovery; every no-consent/carbon-positive/entrenching/irreversible project → `:refuse`.

## Files

```
methods/ugachi_edn.cljc   loader + classify
methods/gate.cljc         verdict → assess → render-datoms → render-report (+ bb CLI)
methods/bridge.cljc       Wave 2: ground monopoly-effect in busshi concentration → ground-and-assess (+ bb CLI)
methods/kotoba.cljc       Wave 2: content-addressed append-only STEWARDSHIP LEDGER (tamper-evident commit-DAG)
methods/autorun.cljc      Wave 2: deterministic, idempotent-by-content heartbeat — assess → append ONLY on change (+ bb CLI)
methods/test_*.cljc       gate verdicts + refusal/structural + grounding + ledger/heartbeat invariants
kotoba/ontology.ugachi.edn  EAVT schema + enums + refuse-reasons + negative space
kotoba/seed.edn           synthetic proposed projects spanning all verdicts
data/ (gitignored)        generated stewardship ledger — never committed/hand-edited
manifest.edn              gates G1–G9 + non-goals N1–N5
```

## Wave 2 — busshi grounding bridge (`methods/bridge.cljc`, ADR-2606161830)

The gate's `:monopoly-effect` input was a free project field; the bridge GROUNDS it in
**busshi 物資's actual concentration observation** (ADR-2606161730): map `:resource` → a busshi
commodity, pull its observed chokepoint-risk + top-producer-share, and corroborate-or-correct
the declared effect — `:diversify` on a NON-concentrated commodity is downgraded to `:neutral`
+ flagged `:overclaimed-diversification`; `:diversify`/`:entrench` on a concentrated commodity
is corroborated. Conservative: grounding **never fabricates** an `:entrench` (no false refusals).
E.g. copper "diversify" → neutral (busshi cu chokepoint=low 24%); tungsten "diversify" stands
(busshi w=critical 80%); rare-earth "entrench" → still refused. This composes the OBSERVATION
layer (busshi) into the EXECUTION layer (ugachi).

## Run

```bash
./20-actors/ugachi/run_tests.sh                                    # 5 suites (32 tests / 94 assert)
bb --classpath 20-actors 20-actors/ugachi/methods/gate.cljc        # print the stewardship gate
bb --classpath 20-actors 20-actors/ugachi/methods/bridge.cljc      # print the busshi-grounded gate
bb --classpath 20-actors 20-actors/ugachi/methods/autorun.cljc \
   20-actors/ugachi/kotoba/seed.edn 20-actors/busshi/kotoba/seed.edn   # heartbeat → append to ledger
```

R0 synthetic seed → 3 `:propose-r0`, 1 `:route-to-recovery`, 5 `:refuse`, 2 `:insufficient`
(grounding adjusts the copper diversification overclaim without flipping its permit).

## Pairs with

- **kanayama** (recovery — the route-to-recovery target) · **busshi** / **rare-earth-coverage** (monopoly/concentration input)
- **kamado** (carbon transition) · **abaki** (de-monopolization) · **inochi** (restoration)
- Authorized by **ADR-2606161700** (§2(l) v3.2). Live actuation = Council Lv7+ (never ugachi).

## R0 → later

- **Wave 2 (landed, ADR-2606161830)**: busshi concentration grounding bridge (above).
- **Wave 2 (landed, ADR-2606170900)**: content-addressed stewardship-ledger persistence
  (`kotoba.cljc`) + deterministic, **idempotent-by-content** heartbeat (`autorun.cljc`) —
  verdicts appended to a tamper-evident commit-DAG (verify-chain) ONLY when they change
  (identical beat = no-op, `:appended false :reason :no-change`); resume-safe, no-server-key.
- Wave 2+: rare-earth-coverage detail + kamado carbon-balance as real gate inputs (behind G7/G9);
  Murakumo-narrated gate digest; fleet registration; live kotoba-engine bridge. Live actuation stays Council Lv7+.
