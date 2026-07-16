# meisai 明細

**Member card-statement (利用明細) ingestion into the local kotoba Datom log.** Tier-B actor ·
R0 · ADR-2606122400 · `did:web:etzhayyim.com:actor:meisai`.

A member fetches their OWN card statement with a read-only computer-use agent run on their OWN
machine — [`com-junkawasaki/computer-use-clj`](https://github.com/com-junkawasaki/computer-use-clj)
`examples/sumitclub_meisai.clj` (karakuri 絡繰 T2 posture: ToS-permitted automation of the
member's own account; credentials vault-injected via `type_secret`; inference on **local Ollama
gemma 4 QAT**, Murakumo-conformant per ADR-2605215000). The agent writes a statement EDN file
locally; **meisai ingests that file** into append-only `:meisai.stmt/*` + `:meisai.row/*` EAVT
datoms on a content-addressed local kotoba Datom log.

First source: **sumitclub.jp** (SuMi TRUST CLUB). The intake shape is source-agnostic — any card
portal the fetch leg learns to read lands through the same ingest.

## What is structural, not advisory

- **G2 — credentials and card numbers are unrepresentable.** `ingest.guard` RAISES on
  credential-shaped keys (`password`/`secret`/`otp`/`cvv`/`pin`/`token`) and PAN-shaped values
  (13–19-digit runs) anywhere in an intake. Test-enforced.
- **G3 — personal data never leaves the machine.** `data/` (intake + persisted log) is
  gitignored; the loop persists locally and publishes/pins/posts nothing.
- **G5 — provenance + dedup.** Every statement tx carries the intake file's content CID;
  re-running the loop over the same intakes appends nothing; `verify_chain` detects tamper.

## Run

```bash
# 1. fetch (member-run, on the member's machine — see computer-use-clj README):
#    SUMITCLUB_OUT=20-actors/meisai/data/intake/2026-05.edn \
#      clojure -M:dev:examples -e "(require 'sumitclub-meisai) (sumitclub-meisai/-main)"

# 2. ingest (no network, no credentials):
python3 methods/autorun.py --cycles 1

# tests (standalone, stdlib only):
./run_tests.sh
```

## Worldwide coverage (R1) — `sources/world-card-issuers.edn`

meisai is **source-agnostic**: any card portal the fetch leg learns to read lands through the same
ingest. `sources/world-card-issuers.edn` is the **public coverage registry** of the world's card
companies & payment services — global networks (Visa/Mastercard/Amex/JCB/UnionPay/Discover/RuPay/
Mir/Elo/…), issuers across every region (JP/US/EU/UK/CN/KR/IN/BR/SEA/MEA), PSPs, wallets, and BNPL
— **101 sources today** (18 networks). It holds **public metadata ONLY** (company name, public
portal root, accepted networks, and — for sources the fetch leg has learned — a statement-shape
adapter); it carries **no statement, row, credential, or card number**, so unlike the gitignored
statement log it lives OUTSIDE `data/` and IS committed.

`methods/sources.cljc` (clj-native):

```bash
# honest coverage report + the ingest worklist (which registered issuers still need an adapter):
bb -e '(require (quote meisai.methods.sources))(meisai.methods.sources/-main)'
#   101 sources (1 fetch-supported, 100 registry-only / worklist); 18 networks; kinds {…}

# regenerate the public Datom log (the registry "data itself in datomic/edn", 922 datoms, committed):
bb -e '(require (quote meisai.methods.sources))(meisai.methods.sources/-main "--emit")'
#   → sources/world-card-issuers.kotoba.edn  (one append-only tx, deterministic CID)
```

- **registry-datoms** → `:meisai.source/{id,name,kind,country,region,currency,status,portal,
  network}` EAVT (public, committable).
- **coverage** → honest `:supported` (an adapter exists — only `sumitclub` today) vs
  `:registry-only` worklist; never pretends the world's tens of thousands of issuers are all here.
- **normalize** → maps a raw issuer statement (any field names, any currency) into the canonical
  meisai intake. **JPY** keeps the legacy `:amount_jpy` attribute (parity with sumitclub); other
  currencies land as generic `:amount` + `:currency` in **integer minor units** (no floats in the
  Datom log). The G2 credential/PAN guard still runs on every normalized intake (test-enforced).

Adding an issuer is one EDN entry: public metadata costs nothing constitutionally. A fetch-leg
adapter (G4 read-only posture, member-principal) is what flips a source `:registry-only → :supported`.

## kaiyaku handoff (R1) — recurring-charge detection

`methods/recurring.cljc` (clj-native) folds the member's own `:meisai.row/*` rows into
recurring-charge **candidates** (a merchant billed across ≥N distinct months at a stable amount
looks like a subscription) and emits a kaiyaku-consumable handoff — the `meisai → kaiyaku` wiring
that mirrors `tate → kaiyaku`.

```bash
bb -e '(require (quote meisai.methods.recurring))(meisai.methods.recurring/-main)'
#   → data/kaiyaku-handoff.edn  (recurring candidates; advisory :review)
```

- **recurring** → `{:merchant :currency :months :occurrences :typical-amount :amount-stable?
  :recurring?}` per candidate (median amount; stability flag; multi-currency aware).
- **handoff** → records carrying `:handoff/action :review` + `:handoff/advisory true`. **meisai
  SURFACES, it never DECIDES**: keep/review/sever is kaiyaku's call (its G2 edge-primary burden +
  member-sig + dry-run gates). `:sever` is **not representable** here (test-enforced), and a
  merchant is a SERVICE candidate, never a person (kaiyaku N1).
- **G3**: the handoff reveals subscriptions → it is PERSONAL data, written under the gitignored
  `data/`, never committed/pinned/posted. The pure fns operate on datoms, so they are tested on
  synthetic datoms with no file.

The round-trip is closed on the kaiyaku side: kaiyaku `methods/meisai_ingest.cljc` ingests this
handoff into its 縁-ledger as a `:recurring-charge` tie over a `:svc/kind :card-merchant` node.

## Report-time FX (R1) — `methods/fx.cljc`

A worldwide statement bills in any currency; the log stores the native amount in integer minor
units. `fx.cljc` adds a **report-time** JPY-equivalent for the member report / kaiyaku handoff —
**never a Datom** (a rate snapshot goes stale; baking it into the append-only log would assert a
false as-of truth):

```clojure
(fx/to-jpy 999 ":usd" {":usd" 150.0})       ;=> 1499   (999¢ @150)
(rec/handoff datoms {:rates {":usd" 150.0}}) ;=> non-JPY records gain :handoff/jpy-equivalent
```

`enrich-handoff` annotates only non-JPY records (`:handoff/jpy-equivalent` + `:handoff/fx-rate` +
`:handoff/fx-advisory true`), leaving the native amount/currency untouched. With a JPY-equivalent
present, kaiyaku prices the foreign charge; without a rate it stays cost-0 → kaiyaku routes it to
`:review`, never auto-`:sever` (G8 honesty). Rates are an INPUT (the member's own snapshot or a
future G7-gated live leg), never a committed table that would rot.

## Per-issuer fetch adapters (R1, member-side)

`:supported` requires a **fetch-leg adapter** — a member-run, read-only computer-use script in
[`com-junkawasaki/computer-use-clj`](https://github.com/com-junkawasaki/computer-use-clj) (karakuri
T2 posture, vault-injected creds, Murakumo-conformant local inference). The in-repo seam is the
registry `:shape`: the adapter writes a statement EDN with whatever field names the portal yields,
the member declares those names once in the source's `:shape`, and `sources/normalize` maps them to
the canonical intake — **no per-issuer code in meisai**. The contract per issuer:

1. write a read-only fetch script for the member's own account (no state-changing controls, G4);
2. declare its `:shape {:rows … :month … :date … :merchant … :amount … :amount-scale :minor|:major}`
   on the registry entry (sumitclub is the worked example);
3. verify it end-to-end on the member's machine → flip `:status :registry-only → :supported`.

Adapters live out-of-repo (member-side, G7); the 100 `:registry-only` sources are the worklist.

## Lexicons & residence (R1)

- **Lexicons** (`cells/lex/`): `com.etzhayyim.meisai.{statement,source,recurringHandoff}` — document
  the on-log / handoff shapes. `source` is PUBLIC metadata; `statement` + `recurringHandoff` are
  PERSONAL (local-only, no PAN/credential — the schemas carry no such field by construction).
- **Residence**: `50-infra/launchd/com.etzhayyim.meisai.heartbeat.plist` — a per-member launchd
  LaunchAgent that runs the local intake sweep hourly on the member's OWN machine. **Not a shared
  murakumo fleet cell**: meisai is member-local personal data with no network I/O (G1/G3/G7), so it
  must not run on shared fleet nodes (the constitutionally-correct reading of "fleet registration").

## Boundaries

| Sibling | Relation |
|---|---|
| karakuri 絡繰 | the fetch leg is karakuri-shaped (T2 own-account automation); meisai is the ingestion side |
| kaiyaku 解約 | `methods/recurring.cljc` detects recurring charges over `:meisai.row/*` → advisory handoff (`data/kaiyaku-handoff.edn`); kaiyaku ingests it into its 縁-ledger and owns the keep/sever decision |
| organizer | detects ご利用明細 *mail* patterns; meisai holds the statement *table* |
| toritate 執帳 | corp's OWN on-chain books — a MEMBER's personal card is not that; never conflate |
| warifu 割符 | the corp's own card rails; meisai only reads external bank-issued cards |

## R1 honesty

Methods + 28 green bb tests (103 assertions); live fetch verified end-to-end against local gemma 4
QAT (mock-host loop) on 2026-06-12. Landed: the worldwide coverage registry (101 sources) +
multi-currency normalize + the kaiyaku recurring-charge handoff (round-trip closed) + report-time
FX + 3 lexicons (`cells/lex/`) + the member-local launchd heartbeat. The per-issuer **fetch-leg
adapters** (which flip the 100 `:registry-only` sources to `:supported`) live member-side
(computer-use-clj, G7) and are the remaining wave. No Pregel/shared-fleet registration — and there
won't be: meisai is member-local (G1/G3/G7), so it resides as a per-member LaunchAgent, not a fleet
cell. Aggregate/derived views beyond recurring-charge detection are future waves.
