# 20-actors/meisai — CLAUDE.md

## Identity

- **Name**: meisai (明細 — the statement itself; the row-level truth of what was spent)
- **DID**: `did:web:etzhayyim.com:actor:meisai`
- **ADR**: ADR-2606122400 (R0, 2026-06-12)
- **Parent ADRs**: ADR-2605262130 + 2605312345 (kotoba Datom log), ADR-2605215000
  (Murakumo-only inference), ADR-2606039200 (karakuri — own-account automation posture)
- **Cross-actor siblings**: karakuri (fetch posture), kaiyaku (recurring-charge consumer),
  organizer (mail-pattern detector), toritate / warifu (boundaries — see README)
- **Status**: R0 — methods + tests landed; first source sumitclub.jp

## Constitutional Discipline (CRITICAL)

meisai is a **member-own personal-data ingestion actor**. The hard rules, in order of how easy
they are to violate by accident:

1. **G3 local-only** — `data/` is gitignored and MUST stay so. Never commit, pin, publish, or
   post a statement, a row, an intake file, or the persisted log. This repo is public; a card
   statement in a commit is unrecoverable. If you add a new data path, add it to `.gitignore`
   in the same change.
2. **G2 credential/PAN unrepresentable** — `ingest.guard` raises on credential-shaped keys and
   PAN-shaped values. Do not weaken the guard; extend the test when you extend the shape.
3. **G1 member-own only** — the only input is a local file the member produced about their own
   account. Do not add a path that takes another person's statement.
4. **G4 read-only at source** — the fetch leg (computer-use-clj `sumitclub_meisai`) carries a
   system prompt that forbids every state-changing control on the card site. If you touch the
   fetch leg, keep that prompt's HARD RULES intact.
5. **G6 Murakumo-only inference** — within etzhayyim, the fetch leg runs on local Ollama
   (gemma 4 QAT default via `jvm_host.clj` `LLM=ollama`). Do NOT wire the Anthropic/Gemini
   adapter paths into anything under this actor (ADR-2605215000).
6. **G7 live leg operator-gated** — meisai's own loop does no network I/O (test-enforced).
   The browser fetch is a step the member runs explicitly, never a cron.

## Architecture

```
member's machine                                 20-actors/meisai/
┌──────────────────────────────┐                 ┌──────────────────────────────┐
│ computer-use-clj             │   statement EDN │ methods/ingest.py  (G2 guard)│
│  sumitclub_meisai.clj        │ ──────────────▶ │ methods/autorun.py (sweep)   │
│  · IComputer macOS host      │  data/intake/   │ methods/kotoba.py  (commit-  │
│  · IVault 1Password/Bitwarden│                 │   DAG, append-only, local)   │
│  · Ollama gemma 4 QAT        │                 │ data/persisted/*.kotoba.edn  │
└──────────────────────────────┘                 └──────────────────────────────┘
       member-principal, read-only                      gitignored, local-only
```

Datom shape: `meisai-stmt:<source>:<YYYY-MM>` entities with `:meisai.stmt/{source,month,
total-jpy,row-count,intake-cid,source-url}` (+ worldwide `:meisai.stmt/{total,currency}`);
`meisai-row:<hash16>` entities with `:meisai.row/{stmt,index,date,merchant,amount-jpy,note}`
(+ worldwide `:meisai.row/{amount,currency}` for non-JPY cards). All `:db/add`, no retract.

## Worldwide coverage registry (R1)

`sources/world-card-issuers.edn` is the PUBLIC coverage map of the world's card companies &
payment services (networks / issuers / PSP / BNPL / wallets) so meisai can ingest ANY issuer, not
just sumitclub. It is **public metadata** (no statement/row/credential/PAN) → it lives OUTSIDE the
gitignored `data/` and **IS committed** (do NOT add it to `.gitignore`; that is the whole point —
it is the one file in this actor that is meant to be public). `methods/sources.cljc` emits
`:meisai.source/*` datoms (`sources/world-card-issuers.kotoba.edn`, a committed public Datom log),
an honest coverage report + worklist, `resolve`, and `normalize` (raw issuer intake → canonical;
JPY → `:amount_jpy`, others → generic `:amount` + `:currency` in integer minor units). G2/G3 are
unaffected: the registry parses no statement, and normalize feeds ingest where the G2 guard runs.

## Clojure port (datomic + clojure substrate parity)

`methods/{kotoba,ingest,autorun}.cljc` + `methods/test_autorun.cljc` are 1:1 Clojure ports of the
heartbeat. The canonical-JSON tx-CID encoder reproduces Python's
`json.dumps(…, sort_keys=True, separators=(",",":"))` **byte for byte**, so the Clojure and Python
heartbeats build the **identical commit-DAG** (the 2-intake head CID
`b0f03ac8fd4ddac1f0715278c13d847498f80e09b6102d4164f7a3a834251b62c` is asserted equal in
`test-autorun cid-byte-parity-with-python`). The **G2 guard** (credential-shaped key / PAN-shaped
value → raise) is ported and test-enforced. `kotoba.cljc` embeds its own EDN reader (no cross-actor
dep), matching `parse_edn`. Run: `bb -cp 20-actors -e "(require 'meisai.methods.test-autorun
'clojure.test)(clojure.test/run-tests 'meisai.methods.test-autorun)"`.

## Build & Test

```bash
./run_tests.sh                      # 6 bb suites, 28 tests / 103 assertions, hermetic
bb -cp 20-actors -e "(require 'meisai.methods.autorun)(meisai.methods.autorun/-main \"--cycles\" \"1\")"
```

## R1 triggers (deferred)

**per-issuer fetch-leg adapters** that flip the 100 `:registry-only` sources in
`sources/world-card-issuers.edn` to `:supported` (member-side, computer-use-clj, G4 read-only
posture — out-of-repo, the seam is the registry `:shape` + `sources/normalize`).

LANDED in R1: lexicons `com.etzhayyim.meisai.{statement,source,recurringHandoff}` (`cells/lex/`);
member-local launchd heartbeat (`50-infra/launchd/com.etzhayyim.meisai.heartbeat.plist` — NOT a
shared fleet cell, by G1/G3/G7); report-time FX enrichment (`methods/fx.cljc`,
`:handoff/jpy-equivalent`, advisory — never a `:meisai.row/*` Datom).

The kaiyaku SIDE of the recurring-charge handoff **landed** (ADR-2606122400): kaiyaku
`methods/meisai_ingest.cljc` ingests `data/kaiyaku-handoff.edn` into its 縁-ledger as a
`:recurring-charge` tie over a `:svc/kind :card-merchant` node — the meisai → kaiyaku round-trip
is now closed (kaiyaku decides keep/review/sever; meisai never does).

The recurring-charge handoff (`methods/recurring.cljc`) is meisai's first DERIVED view: it folds
`:meisai.row/*` into subscription candidates and emits an ADVISORY `:review` handoff
(`data/kaiyaku-handoff.edn`, PERSONAL → gitignored). meisai SURFACES; kaiyaku decides keep/sever
(G2 + member-sig). `:sever` is unrepresentable here and a merchant is a SERVICE, never a person
(kaiyaku N1) — both test-enforced.
