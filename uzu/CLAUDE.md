# uzu 渦 — CLAUDE (actor instructions)

**uzu 渦** is an artificial organism built on the **information-energy coupled** view
of existence (the design brief, ADR-2606211500): *being = energy flow forming an
information structure that bends the next energy flow*. The organism is the **vortex**
in the flow — not the water, the self-maintaining pattern in it.

## What it is

A **Markov-blanketed active-inference agent** living on the append-only kotoba Datom log:

- **perceive → infer → plan → act → metabolize → live-or-die** (one `metabolism/beat`).
- **μ (belief) = a fold over the perception log** (`model/fold-beliefs`) — the information
  structure literally is a fold, not a stored cell.
- **infer** minimizes **variational free energy** (`model/update-belief`, in nats).
- **plan** minimizes **expected free energy** (`model/choose`) = pragmatic value (match the
  preference `C`) + epistemic value; **affordability (energy) vetoes the choice**.
- **act** charges the **metabolic energy ledger** (`ledger/metabolize`); intake is drawn
  from the **true** regime (the world bends back).
- **death** when the conserved energy balance hits zero — self-maintenance is **earned**.

It also **measures real-world energy flows** (`measure/*`) — physical (W), economic (USD/yr),
informational (bit/s), experiential/meaning (index) — as one open dissipative system, and
**visualizes** them (`viz/*` → `out/energy-field.html`).

## The load-bearing invariants (do not break — they ARE the design)

- **Two ledgers, never conflated.** ENERGY is conserved + depletes (`:uzu.beat/energy`);
  INFORMATION is copyable + append-only (`:uzu.beat/free-energy` + the commit-DAG). No code
  sums them in one unit. (G1, test-enforced.)
- **Never equate units across classes.** The four measured flow classes are incommensurable;
  `measure/totals-by-class` sums only within a class — there is **no** grand total. (G2.)
- **No joules-per-meaning.** Experiential flows have **no** physical conversion; the factor is
  explicitly `nil`. Converting meaning → joules is the "philosophy soup" the design rejects.
  Meaning is **subject-dependent** (the preference `C` is the only subject-specific term). (G3.)
- **Cross-class visual magnitudes are `:reference-only`** — log-layout placement, not a unit
  identity claim. (G4.)
- **Mortality is real.** No immortality; a misfitted organism dies. (G5.)
- **Deterministic, no-server-key.** The tape IS the world (no `Math/random`, no wall clock);
  the loop holds no key and does no network I/O. Live ingest from observatory siblings
  (kasa/kanjō/shionome/hikari/busshi/spirit-in-physics) is a Council/operator step. (G6/G7.)

## Engineering conventions

- **clj/bb over the kotoba Datom log** (repo-wide rule). All methods are pure `.cljc`,
  babashka-runnable; file I/O is `#?(:clj …)`-gated. No Python, no shell logic.
- State = content-addressed EAVT commit-DAG (`kotoba.cljc`), verify-chain tamper-evident,
  idempotent-by-content heartbeat (`autorun.cljc`).
- Run tests: `./20-actors/uzu/run_tests.sh` (7 suites; **42 tests / 111 assertions green**).
- Generate the visualization: `bb --classpath 20-actors 20-actors/uzu/methods/viz.cljc`.

## Roster context

Sibling of **ibuki 息吹** (organism autonomy on the Datom log — uzu adds the missing
*thermodynamic* half: an energy budget, metabolism, and death) and of the observatory
lineage that supplies the real-world flows. See the root `CLAUDE.md` Status table + ADR-2606211500.
