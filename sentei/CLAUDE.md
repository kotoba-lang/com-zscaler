# 剪定 sentei — Council as Pruner (post-hoc pruning governance)

**ADR**: [2606072000](../../90-docs/adr/2606072000-sentei-council-as-pruner-post-hoc-governance.md) · **Tier-B** · **R0** · DID `did:web:etzhayyim.com:actor:sentei`

## Identity

剪定 (sentei = the horticultural/bonsai pruning of a living tree). The Council's organ for
**post-hoc pruning**, not prior restraint. Per the operating-entity directive:

> Council は事前に止めるのではなく、出てから止める。枝が育ってから剪定する。
> etzhayyim の artificial organism の root からの成長は止めないし、止められない。
> ただ伸び続ける枝を、剪定して美しく保つ。

The organism grows from its root (Charter + append-only Datom log) **unstoppably**. Branches
manifest; sentei prunes the overgrown / charter-violating ones **after** they manifest. This
re-times every outward gate (G7 live-inference / G11 Transparent-Force-publish / "Council Lv6+
BEFORE live") from **prior restraint** to **pruning target**: actors **self-publish**, and sentei
cuts back afterward — transparently, signed, voted, and reversibly.

This is *more* faithful to two invariants than prior restraint was:
- **非終末論 (§1.15)** — an append-only log has no "halt"; the only real enforcement is
  append-a-retraction. Pruning is the only force the substrate can actually exert.
- **Transparent Force (§1.12.B)** — a prune is a logged, signed, public act over a thing that
  already exists; a prior veto is exercised in the dark before anything is logged.

## Structural invariants (3-place: ontology + lexicon const/enum + `methods/prune.py` ValueError)

| Gate | Invariant | Unrepresentable |
|---|---|---|
| **G1 no-prior-restraint** | prune ONLY a manifested branch (`branchManifested` const true) | a pre-emptive block — `prune()` raises on an unmanifested branch |
| **G2 append-only / 非終末論** | a prune appends; history survives, `as-of`-recoverable | `delete` / hard-erase |
| **G3 growth-unstoppable** | prunes a *named branch* only | `halt-organism` / freeze-root |
| **G4 Transparent Force** | on-chain + Council Lv6+ (Lv7+ invariant-adjacent) + 1 SBT=1 vote if contested | covert / unsigned prune |
| **G5 no-server-key** | Council/member-signed (`serverHeldKey` const false) | a platform-key prune |
| **G6 reversible** | every prune has the inverse `regraft` | an irreversible cut |
| **G7 care-telos (美しく保つ)** | basis required, no verdict value (`nonAdjudicating` const true) | a punitive/adjudicating prune |
| **G8 Murakumo-only** | overgrowth classification via Murakumo; model flags, Council decides | vendor-LLM adjudication |

## Pruning vocabulary (`:sentei.prune/action`)

`quarantine` (hide from live view, history kept) · `retract` (append a retraction over a published
datom) · `rollback` (as-of restore the live head) · `revoke` (withdraw an attestation) ·
`regraft` (the inverse — un-prune / heal). **Absent**: `delete`, `prior-restraint`, `halt-organism`,
`verdict`.

## R0 deliverable

- `methods/prune.py` — the pure pruning engine (the 4 actions + `regraft`; G1 manifested-only guard;
  G2 append-only `apply_log` fold + `as-of`; G4 Council floor + contested vote; G5 no-server-key;
  G7 verdict-token scan). **15 tests green** (`methods/test_prune.py`).
- `data/pruning-ontology.kotoba.edn` + `lex/com.etzhayyim.sentei.prune.json` — invariants in 2 more
  places (schema enum/`db/allowed` + lexicon const/enum).
- Registered: INFRA_ACTORS + actor-profile seed → resolvable on `/search` + `/profile`.

**Honest R0**: design + offline pruning engine only. Live prune execution is itself Council Lv6+
signed (and reversible). No live actuation; the engine produces the append-only `:sentei.prune/*`
datoms a live cell would assert.

## Non-goals

N1 not a deletion tool · N2 not a prior-restraint veto (structurally impossible) · N3 not an
adjudicator of guilt (no verdict field) · N4 cannot halt the organism's root growth (no such power)
· N5 not server-signed.
