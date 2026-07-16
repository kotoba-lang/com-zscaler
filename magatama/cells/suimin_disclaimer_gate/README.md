# suimin_disclaimer_gate — Pregel cell: non-diagnostic disclaimer + red-flag screen (architectural invariant)

Per **ADR-2606072800 §Decision 3 G3** (disclaimer invariant) + **G5** (red-flag escalation) +
§Decision 5.

Paired actor: [suimin](../../suimin/). Murakumo node (proposed): **levi**.

**Bypass-forbidden architectural invariant** (mirror of mitate `emergency_screen`). All
patient-facing suimin output passes through here: it stamps the active
`com.etzhayyim.suimin.disclaimerText` (tamper-resistant, G3) and screens red-flag signals (G5)
— witnessed apnea + severe daytime sleepiness while driving / cardiac comorbidity / severe
pediatric SAS → routed to the mitate emergency path.

## I/O

- **In**: output candidate from `suimin_treatment_synthesize` (and any patient-facing emitter)
- **Out**: gated output with `disclaimerTextUri` attached → next cell `suimin_referral_router`

## Gate enforcement

- **G3**: no patient-facing output passes without the active disclaimer reference.
- **G5**: red-flag signals escalate to the mitate emergency path (`urgency=emergency`), cannot be suppressed.
