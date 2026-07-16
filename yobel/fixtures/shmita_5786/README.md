# Fixture: shmita 5786

Hebrew calendar year 5786 = the **9th sabbatical year of the 7th Jubilee cycle**, running from sundown 2025-09-22 (Rosh Hashanah 5786) to sundown 2026-09-11 (Erev Rosh Hashanah 5787).

A real-world shmita year is currently underway. This fixture instantiates a synthetic etzhayyim shmita rite that releases voluntary in-community monetary debts originated **before** the cycle start.

## Files

- `rite.json` — `declareRite` input (1 rite)
- `creditors.json` — 3 `enrollCreditor` inputs (3 creditors, varying debt portfolios)
- `debtors.json` — 5 `enrollDebtor` inputs (1 happy / 1 no-SBT / 1 non-community / 1 post-cycle debt / 1 prohibited instrument exposure)
- `releases.json` — 4 `recordRelease` inputs (3 happy / 1 expected one-way violation)
- `expected.json` — expected outcomes per cell for golden-file comparison in `dry_run.py`

## Doctrinal basis

- Lev 25:1-7 — agricultural sabbatical
- Deut 15:1-2 — monetary debt release ("שמטה לה'") at the end of every seven years
- Deut 15:3 — release applies only to community members (non-community claim survives)
- Hillel's *prozbul* (M. Sheviʿit 10:3-4) — historic workaround; **not** invoked here by design (etzhayyim shmita is voluntary religious release, not subject to prozbul circumvention)

## Run

```bash
PYTHONPATH=20-actors python3 20-actors/yobel/scripts/dry_run.py \
    --fixture 20-actors/yobel/fixtures/shmita_5786
```

Dry-run uses the in-memory `Fake*` port stubs from `conftest.py` adapted to a non-pytest entrypoint.

## Expected outcomes summary

| Debtor | Eligibility | Release outcome |
|---|---|---|
| `debtor-community-ok` (SBT Lv2, in-community) | R1 eligible | base_l2_transfer succeeds |
| `debtor-no-sbt` (SBT Lv0) | R12 reject | no release |
| `debtor-foreigner` (SBT Lv1, NOT community) | R3 reject | no release |
| `debtor-post-cycle` (SBT Lv2, in-community, but debt originated 2026-10-05) | R2 reject | no release |
| `debtor-instrument-exposed` (SBT Lv2, in-community, but one debt is margin_call) | R13 reject (short-circuit before R1-R11) | no release for the whole bundle |

Net: 1 successful release out of 5 debtors, demonstrating gate composition + defense-in-depth Charter Rider §2(b) enforcement.
