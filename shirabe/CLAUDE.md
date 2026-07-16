# shirabe 調べ — live research-concierge membrane (kotoba-clj)

**ADR**: 2606131600 · **depends**: 2605215000 (Murakumo-only inference) · 2605262130 +
2605312345 (kotoba Datom = canonical state) · 2606012300 (kotoba web search) · 2606039200
(karakuri own-account posture) · 2606061000 (Maxwell default weight) · 2606122400 (meisai
local-Ollama = Murakumo-conformant) · 2606131300 (clj-port first-class). **Status**: 🟢
R1 live-verified.

shirabe ("調べ" = to look into / investigate) is the **LLM/answer layer over web search** the
roster was missing. The observatory actors (danjo/kanjō/tsumugi/…) ingest disclosed corpora;
the kotoba web search (ADR-2606012300) indexes Common Crawl. None of them **answer a person's
natural-language question** by reading the **live** public web and grounding a **gemma4** answer
in cited sources. shirabe does exactly that — and it is the thing that lets the etzhayyim stack
answer『青山の島田は今日やっている?』the same way a person would, on kotoba-clj + gemma4 instead
of a frontier API.

It is a **LOOK-UP, never an ACT**: it reads the public web, it never authenticates, books, buys,
or submits a form. A purchasing/booking flow is a different, member-principal actor
(okaimono/shukubo/tsubasa).

## The loop (ReAct, bounded)

```
question ─▶ analyze (plan) ─▶ retrieve (read-only web) ─▶ synthesize (Murakumo gemma4) ─▶ kotoba (Datom log)
              pure, G3/G7        G1, injected fetcher        G2/G4, injected infer          G5 transparent, G6 privacy
```

`session/research` chains them; if gemma4 reports `INSUFFICIENT` and sub-queries remain, it does
ONE more round (`max-rounds` 2 — never an open-ended crawl).

## Hard gates (constitutional — read before any change)

- **G1 — read-only public web, look-up never act.** The fetcher is a read-only public-web
  search/fetch. shirabe never authenticates, books, buys, or writes anywhere outward.
- **G2 — Murakumo-only inference (ADR-2605215000).** `synthesize/allowed-infer-hosts` allowlists
  the LiteLLM gateway / EVO-X2 / per-node Ollama gemma 4 E4B QAT. `validate-host!` raises on any
  other host — every commercial LLM API is unrepresentable. Re-checked at persist time
  (`kotoba/session-datoms` rejects a non-fleet model host).
- **G3 — no personalization / no surveillance.** The loop carries the query and nothing about who
  asked: no profile, cookies, history, or behavioural ranking (kawaraban/tsubasa lineage).
- **G4 — citation-grounded, non-fabricating.** The prompt forbids unsourced claims and answers
  `INSUFFICIENT` when the sources do not suffice. The resolved **本日** date is injected as a FACT
  (the system clock), so a freshness question can be reasoned — that is not fabrication.
- **G5 — bounded + sourced + transparent.** Sub-queries ≤4, evidence ≤ `top-k`, ReAct ≤
  `max-rounds`. The whole session (question, sources, model, answer) is appended to the kotoba
  Datom log — auditable by construction (相互監視 / 神の監視, reciprocal not asymmetric).
- **G6 — privacy.** `:shirabe.session/member` is bound ONLY when a member SIGNED the session; an
  anonymous research session binds no identity. The persisted log is local by default; publication
  is a separate gated step.
- **G7 — the loop does no implicit network I/O.** The web fetcher and the gemma4 infer are both
  injected; the live legs (`live.clj`, `datomic.transact` to a live node) are explicit
  operator/member steps, never a cron. This keeps the WASM component a pure function.

## Layout

```
20-actors/shirabe/
├── CLAUDE.md                       # this file
├── manifest.jsonld                 # actor manifest (6 cells, 7 gates)
├── .gitignore                      # data/persisted, out (never committed)
├── data/fixtures/
│   └── shimada-aoyama.kotoba.edn   # EDN fixture (mirrors the real 2026-06-13 ぐるなび lookup)
├── methods/                        # pure .cljc — kotoba-clj, run under babashka + kotoba engine
│   ├── analyze.cljc                # PLAN  — question → research plan (pure)
│   ├── retrieve.cljc               # SEARCH — plan + injected fetcher → ranked, sourced evidence
│   ├── synthesize.cljc             # gemma4 — evidence → Murakumo-only cited answer
│   ├── session.cljc                # orchestrator — bounded ReAct loop
│   ├── kotoba.cljc                 # kotoba Datomic write path ([:db/add] + commit-DAG)
│   └── live.clj                    # G7-gated LIVE driver (DDG fetch + Ollama gemma4 + persist)
├── tests/                          # 21 tests / 53 assertions, run under bb
│   ├── test_analyze.cljc
│   ├── test_retrieve.cljc
│   ├── test_synthesize.cljc
│   ├── test_session.cljc
│   └── test_kotoba.cljc
└── wasm/README.md                  # WASM-actor boundary (host owns live legs = G7 structural)
```

## Run

```bash
# from repo root — tests (21/53 green)
bb --classpath 20-actors -e '(require (quote [clojure.test :as t]) \
  (quote shirabe.tests.test-analyze) (quote shirabe.tests.test-retrieve) \
  (quote shirabe.tests.test-synthesize) (quote shirabe.tests.test-session) \
  (quote shirabe.tests.test-kotoba)) \
  (t/run-tests (quote shirabe.tests.test-analyze) (quote shirabe.tests.test-retrieve) \
    (quote shirabe.tests.test-synthesize) (quote shirabe.tests.test-session) \
    (quote shirabe.tests.test-kotoba))'

# LIVE: real DuckDuckGo search → real local gemma 4 E4B (Murakumo-conformant) → cited answer → kotoba Datom log
bb --classpath 20-actors 20-actors/shirabe/methods/live.clj "青山の島田は今日やっている?" --asof 2026-06-13
```

Verified live 2026-06-13: 6 real web sources → gemma 4 E4B answered『…本日は第2土曜日であり、定休日
であるため営業していない [2,4]』→ content-addressed tx appended (verify-chain ok).

## Operator legs (gated, not in the loop)

- `--infer` defaults to the local Ollama; the LiteLLM gateway (`:4000`) is the fleet path when up.
- live `datomic.transact` to a running kotoba node (`com.etzhayyim.apps.kotoba.datomic.transact`)
  and the etzhayyim.com worker `/ask` route + the componentize-py WASM build are the remaining
  operator/Council-gated steps (no-server-key).
