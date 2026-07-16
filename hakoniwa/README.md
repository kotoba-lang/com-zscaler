# hakoniwa 箱庭

**Forward-simulation observatory** — the charter-clean inversion of a swarm-intelligence
prediction engine (the `666ghj/MiroFish` shape). hakoniwa runs a **contained miniature world**
(箱庭) of **FICTIONAL latent personas** forward in discrete steps and reads out a
**DISTRIBUTION over possible futures**, routed to **resilience & preparedness**.

- **Tier-B actor** · **ADR-2606111500** · status **🟡 R0** (design + deterministic engine +
  13 tests; live swarm gated).
- **Feeds mitooshi 見通し** (ADR-2606051800): mitooshi had the leak-free proper-scoring loop
  but no generative engine; hakoniwa produces the distributions it scores.
- **Pure stdlib** (no numpy) → kotoba pywasm-runnable; deterministic (seeded, no `Math.random`).

## The inversion (why it is charter-clean)

| MiroFish-class engine | hakoniwa 箱庭 |
|---|---|
| models **real people** to predict opinion | models **fictional latent personas** only (G1; no PII) |
| asserts a **prediction** | asserts a **distribution**, never a point (G2; 非終末論) |
| implicitly **steers** (persuasion) | routed to **resilience**; steering unrepresentable (G3) |
| proprietary / opaque world | **transparent** box, plaintext-public on kotoba (G4; 相互監視) |
| OpenAI-compatible API + cloud memory | **Murakumo-only** + kotoba Datom log (G5) |

Simulating fictional agents is categorically distinct from surveilling real people
(ADR-2606111400). The whole box is public; there are no real people in it to watch unwatched.

## Run

```bash
python3 methods/distribution.py   # → out/distribution-report.md + out/forecast-record.kotoba.edn
python3 methods/datom_emit.py     # → out/scenario-datoms.kotoba.edn (canonical EAVT state)
python3 tests/test_simulate.py && python3 tests/test_distribution.py   # 13 green
```

See `CLAUDE.md` for the constitutional gates (G1–G8), the Friedkin-Johnsen kernel, and the
ontology. The seed scenario (`data/seed-scenario.kotoba.edn`) is a fictional town weighing a
sonae 備え-aligned flood-preparedness drill — a resilience lens, never a prediction asserted
as fact.
