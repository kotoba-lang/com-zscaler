# kaiyaku 解約 — Clojure LangGraph actor (cljc port)

The portable-Clojure (`.cljc`) port of the kaiyaku Python methods
(ADR-2606112201), turned into a **LangGraph actor** on the
[langgraph-clj](https://github.com/com-junkawasaki/langgraph-clj) /
[browser-use-clj](https://github.com/com-junkawasaki/browser-use-clj) /
[computer-use-clj](https://github.com/com-junkawasaki/computer-use-clj)
stack (all zero-third-party-dep `.cljc`, Datomic-API-first, Clojure-on-WASM
ready — the kotoba-clj premise).

```
:ingest → :analyze → :plan → ‖interrupt‖ :approve → :rehearse → END
```

- the **interrupt before `:approve` is the G5 member-sig gate in graph form** —
  the graph halts with the dry-run plans on the thread's checkpoint; the
  MEMBER reviews and resumes with `{:approved [svc-id …]}`
- approved **T2** plans are *rehearsed* through `browser-use-clj`
  (web surface) or `computer-use-clj` (desktop-app surface) over an
  **injected** `IBrowser`/`IComputer` — at R0 always the pure-data mocks
  (dry-run 稽古); **T1** emits a prepared official-API handoff (live call
  G6-gated); **T3** emits the self-submit procedure (the MEMBER submits)
- with a langgraph **datomic-checkpointer** every superstep is a checkpoint
  datom and with a `:history-conn` every sub-agent action is an action
  datom — the whole 解約 session is a queryable audit trail (G9, the kotoba
  Datom-log idiom of ADR-2605312345)

## Namespaces

| ns | port of | role |
|---|---|---|
| `kaiyaku.ledger` | `methods/analyze.py load` | EDN 縁-ledger parse; **N1 person nodes throw** |
| `kaiyaku.analyze` | `methods/analyze.py` | edge-primary burden + recommend + cascade-guard (numeric parity) |
| `kaiyaku.plan` | `methods/plan.py` | T1/T2/T3 plans; **G3 evasion verbs throw; `execute` throws (G5/G6)** |
| `kaiyaku.datoms` | `methods/datom_emit.py` | EAVT `[e a v tx op]` data + kotoba EDN render (G2 transient stratum) |
| `kaiyaku.executor` | — (new) | T2 rehearsal engines (browser-use / computer-use) + **`murakumo-model` (G4 loopback-only)** |
| `kaiyaku.agent` | — (new) | the LangGraph actor graph + interrupt/resume API |

## Gates carried into the clj layer (test-enforced)

- **G3** twice-structural: `make-step` throws on every evasion verb;
  `select-tier` never returns T2 for `:prohibited`/`:unknown`;
  `rehearse-*` refuses non-T2 plans; the sub-agent's action registry is the
  library default set — no evasion tool *exists* to call.
- **G5/G6** `plan/execute` throws at R0; `build-actor` requires a
  checkpointer (the member-sig gate is a checkpointed interrupt); empty
  approval ⇒ nothing proceeds.
- **G4** `executor/murakumo-model` refuses any non-`127.0.0.1:4000` gateway
  (ADR-2605215000).
- **G8** notice/penalty carried into every plan + rehearsal task prompt.
- **G2** derived readout datoms always `:bond/is-transient`.
- **N1** person/contact nodes throw at `ledger/parse`.

## Run

```bash
cd 20-actors/kaiyaku/clj
clojure -X:test          # 17 tests / 103 assertions (fetches git deps on first run)
clojure -X:test:dev      # against local ../com-junkawasaki/* checkouts (main checkout only)
```

The tests rehearse the **autopay-management surface shape** (list → detail →
confirm → cancelled — the `/myaccount/autopay` flow) on a fully synthetic
`mock-browser` site with a scripted `mock-model` (G1: no real account, no
live model). On a Murakumo node, swap in
`(executor/murakumo-model {:http-fn … :json-write … :json-read …})` and a
host `IBrowser` — the live leg stays G6-gated.

## Boundaries

Same as the actor root [`CLAUDE.md`](../CLAUDE.md): own-account-only,
ToS-honest, no-server-key, member-signed destructive ops, cost-of-severance
honesty. The Python `methods/` remain the kotoba pywasm (componentize-py)
deployment lane; this `clj/` lane is the langgraph-clj / kotoba-clj
(Clojure-on-WASM) lane — both implement the same ADR with the same gates.
