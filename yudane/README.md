# 委 yudane — Human Intention Energy Engine

> 委 (yudane) = *to entrust*. The 意味変換 (meaning-translation) leg of the **Energy
> Order Protocol**, and the most charter-sensitive actor. Consented, aggregate member
> intention ("today WFH", "this load can wait", "heatwave tomorrow") becomes a
> controllable energy variable → 撓 tawami / 澪 mio.

The richest source of flexibility is human intention — but it is also the most dangerous
to touch. yudane's design puts the danger off the table **structurally**, not by promise:

- **consent-bound** — intention enters only via a member-signed, scoped, expiring,
  **revocable** capability (the ibuki revocable-leash). No capability ⇒ nothing is read.
- **content-free** — only an intention **class** + an **aggregate cohort** signal cross
  the membrane. There is no per-person field, by construction.
- **k-anonymous** — a cohort below the floor (5) is refused: no individual may be identifiable.
- **the degeneration series is unrepresentable** — 五人組 → 隣組 → Stasi → social-credit:
  no denunciation, no per-person score, symmetric/reciprocal visibility (Rider §2(c)).

```
verdict:  consented            (capability present + unexpired + reciprocal + cohort ≥ 5)
          refused-no-capability / refused-expired / refused-not-reciprocal / refused-below-k-anon
consented ⇒ aggregate flex offer crosses to tawami/mio;  else nothing.
```

## Gates

- **G1 consent-bound** — member-signed capability required; expired ⇒ refused (revocable leash).
- **G2 content-free / k-anonymous** — aggregate-only; the degeneration series is unrepresentable.
- **G3 reciprocal-transparent** — reciprocal + logged (moyai); consent revocable.
- **G4 translation-only** — yudane never controls; hikari actuates under Council gate.
- **G7 no-server-key** — the capability is member-signed in the member's own runtime;
  yudane is the bearer, never a held key.

## Run

```bash
./20-actors/yudane/run_tests.sh                                   # 21 tests / 96 assertions
bb --classpath 20-actors 20-actors/yudane/methods/analyze.cljc    # render the content-free intention ledger
bb --classpath 20-actors 20-actors/yudane/methods/autorun.cljc    # one heartbeat → append (idempotent-by-content)
```

OBSERVATION + TRANSLATION ONLY. Content-free; never a who-intends-what registry.
ADR-2606211200 · Energy Order Protocol (intention leg).
