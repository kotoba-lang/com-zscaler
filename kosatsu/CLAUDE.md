# kosatsu (高札) — crime/sanctions competing-claim observatory

**DID**: `did:web:etzhayyim.com:actor:kosatsu` · **Tier**: B · **Status**: R0 · **ADR**: 2606072000

**Read the root `/CLAUDE.md` Charter + substrate rules first.** kosatsu-specific invariants below
OVERRIDE nothing in the Charter; they make it concrete for this actor.

## The one-sentence identity

高札 (kōsatsu = the public notice-board) mirrors every crime/sanctions **designation** — OFAC /
EU / UN / UK-OFSI / JP-MOF / Interpol / counter-sanction bodies — into the kotoba Datom log as an
**append-only, attributed EVENT** ("asserter A listed subject S under program P as-of T"), and
computes a **competing-claim / divergence** view of where jurisdictions disagree. etzhayyim
**authors no designation and asserts no verdict**; a designation is **asserter-relative** by
construction. An accountability / due-process-visibility **MAP**, never a target-list and never an
enforcement instrument.

## Why this exists (the gap it closes)

The legacy stack had broad crime/sanctions/intel surfaces (`project-sanctions`,
`project-intel`, `graph-sos-intel`, `yabai`, `malak`) but they were **RisingWave/SQL**, carried
**no political-neutrality model**, and tended toward a single "is X sanctioned" boolean +
per-subject risk score. kosatsu is the **kotoba-Datom-native** answer: a designation is an
**event** with an **asserter** and an **as-of** status, and "crime/sanction" is **asserter-
relative** — which is exactly how to record it neutrally when what counts as a sanctionable act
varies by political position.

## Where kosatsu sits among its siblings (no overlap)

| actor | object | kosatsu's relation |
|---|---|---|
| **danjo** 弾正 | non-adjudicating discrepancy over the state's own corpus | kosatsu reuses the no-verdict discipline; its object is the cross-jurisdiction designation board |
| **tadori** 辿 | authorized on-chain tracing + attribution | a `:designated-wallet`/`:designated-domain` subject bridges to a tadori case (authorized only) |
| **keizu** 系図 | government power-relations weave | a `:designated-org` that is also a public power role bridges to keizu |
| **tsumugi** 紡ぎ | power-entity 縁 / influence | each currently-listed designation projects an asserter→subject "designation-power" edge |
| **kanae** 鼎 | fiscal/atlas render | kosatsu emits the divergence/by-authority aggregates kanae visualizes |
| **tasuke** 助 | cybercrime victim support (person-consented) | DISJOINT — kosatsu NEVER auto-links a designation to a victim flow |

## The pipeline

```
designation_ingest ─▶ competing_claim_weave ─▶ social_post (dry-run)
(an authority's PUBLIC   (per-subject divergence    (member-signed, mirror
 designation export)      {contested|unanimous|       disclaimer, ≥2 primary
                          single-asserter} + as-of)   sources)
```

`weave.py` is the heart: it validates every authority / subject / designation against the closed
vocab, and computes the **edge-primary, politically-neutral** views — `status_as_of` (the event
log → current state), `divergence` (where jurisdictions disagree), `agreement_index`,
`delisting_timeline`, `by_authority`, `co_designation`. Nothing is a per-subject score.

## The 10 gates — do NOT weaken

Structural invariants live in **three places each** (ontology `:db/allowed`/closed-vocab vectors
+ lexicon `:const`/`:enum` + Python `ValueError`). Touch one, touch all three or you've made a
charter violation representable. `methods/test_charter_invariants.py` guards this.

- **G1 mirror-not-adjudicator** — etzhayyim authors no designation. An `:authority/kind` or id
  resolving to self (`etzhayyim`/`self`/`our`/`kosatsu`/…) is **unrepresentable**; every
  designation carries `:designation/asserted-notice const true` (it is an ATTRIBUTED mirror, not
  our claim).
- **G2 asserter-mandatory + non-adjudicating** — `:designation/asserter` is **required**; an
  asserter-less "global truth" designation is unrepresentable. The `:designation/measure` is the
  authority's **instrument** (asset-freeze / export-control / list-inclusion / …); verdict tokens
  (`criminal`/`guilty`/`terrorist`/`enemy`/`crime`) are **not enum members** (danjo G4).
- **G3 primary-source-only** — ≥2 of the **authority's OWN** primary-publication citations per
  designation (`minLength 2`). A commercial screening terminal (WorldCheck / Refinitiv /
  Dow-Jones / ComplyAdvantage / Chainalysis / …) is a **prohibited citation** (Rider §2(e)).
- **G4 event-log / as-of** — `:designation/status ∈ {:listed :delisted}` only; a delisting is a
  **NEW** datom carrying `:designation/lifted-at`, the original `:listed` datom is never
  overwritten or deleted. `:guilty`/`:convicted`/`:permanent`/`:final` are unrepresentable
  (非終末論; kotoba-canonical ADR-2605312345).
- **G5 subject-dignity / no-doxxing** — a subject exists ONLY as the named target of a public
  official act, carrying only the **authority-published identifier**. A private-life PII field
  (`dob`/`face`/`address`/`health`/`family`/…) is unrepresentable; such data lives encrypted
  off-graph (ADR-2605181100).
- **G6 stance-explicit** — every authority declares its OWN jurisdiction/legal-regime
  `:authority/stance`, attributed; never etzhayyim's view. Counter-sanction bodies (RU/CN) are
  authorities too — their designations are recorded with equal attribution.
- **G7 no-server-key + no per-subject score** — posts are member-signed (`:post/server-held-key
  const false`); there is **no** `:subject/risk-score`/`:guilt`/`:threat-level` (the schema has no
  such attr; `validate_subject` raises). Divergence is computed **on read** (edge-primary).
- **G8 outward-gated** — live list ingest + live posting = Council Lv6+ + operator + member
  signature; R0 = offline analyzer + **dry-run** posts; `:post/status` is `:dry-run` only
  (`:published` unrepresentable); every cell `.solve()` raises; `ingest.py --live` is refused.
- **G9 map-not-target / no-enforcement** — every output routes to compliance-awareness /
  due-process visibility / de-risking. Never a "who-to-freeze/attack" target-list or an
  enforcement instruction. The `bridge.py` join keys are **advisory routing only**.
- **G10 sourcing-honesty** — every datom declares `:representative | :authoritative`; the
  committed seed is `:representative` (synthetic ids/labels, not a real list capture).

## When editing

- The closed vocab in `00-contracts/schemas/crime-sanctions-ontology.kotoba.edn`
  (`:ontology/authority-kinds`, `:ontology/measure-kinds`, `:ontology/designation-status`,
  `:ontology/subject-kinds`, `:ontology/divergence-classes`, `:ontology/post-statuses`) is the
  single source the invariant test parses. Adding a verdict measure, a self/etzhayyim authority
  kind, a `:published` post status, or a per-subject score attr fails `test_charter_invariants.py`.
- `weave.py` `VERDICT_TOKENS` / `SELF_TOKENS` / `SOURCE_DENY` are the Python mirror of the
  no-verdict / no-self / no-commercial-terminal rules; keep them in sync with the ontology.
- **Silence is NOT dissent.** The `contested` class fires only on an ACTIVE list-vs-delist
  conflict; a subject listed by some jurisdictions while others never designated it is reported as
  `coverage_split`, never inferred as a contrary stance. Do not collapse the two.
- `.solve()` raises `RuntimeError` on every cell at R0 — live execution is G8-gated. Do not wire a
  cell to a live list fetch or a live firehose post.
- Tests are standalone-runnable (`python3 test_*.py`); run everything with `./run_tests.sh`
  (79 tests across 6 suites). See MATURITY.md.

## Autonomous on the Murakumo fleet (`methods/autorun.py` + `methods/kotoba.py`)

`methods/autorun.py` is the self-driving heartbeat — the constitution-permitted form of
"kotoba で自律的に稼働", the same shape the infra-intel/observatory family uses (shionome / danjo /
keizu …). Each cycle it weaves the OFFLINE seed → report → **persists a content-addressed
transaction** (graph datoms + derived `:kosatsu.div/*`) to the append-only **local** kotoba Datom
log (`methods/kotoba.py`), linking the previous tx's CID into a verifiable commit-DAG.
`autorun._canonical_order` sorts datoms by canonical JSON before hashing → CID reproducible across
processes (verified stable under `PYTHONHASHSEED=random`). **The defining gates hold by
construction**: every designation event carries its own `:designation/asserter` and the loop's test
asserts the asserter is NEVER etzhayyim (G1/G2 — etzhayyim authors no designation); no
`:subject/risk-score` / verdict attr can reach the log (G7); the per-subject `:kosatsu.div/class`
is one of {`:contested` | `:unanimous` | `:single-asserter`} (a neutral fact, never a judgement);
designations are append-only `:listed`/`:delisted` events (G4, 非終末論). Fleet cells
`kosatsu_designation_ingest` (cron 8) + `kosatsu_divergence_weave` (cron 13) + `kosatsu_claim_persist`
(cron 18) on `naphtali` — see `50-infra/murakumo/fleet.toml`. Live list ingest + posting stay
G8-gated (the loop persists to the LOCAL log only). Invariants guarded by `methods/test_autorun.py`
(commit-DAG verify, tamper-detect, canonical-order determinism, append-only, **every-designation-
attributed (non-etzhayyim)**, **no-score/no-verdict + neutral-class**, no-external-I/O).

```bash
cd methods && python3 autorun.py --cycles 3 --fresh   # AUTONOMOUS heartbeat → LOCAL kotoba Datom log
```

## Honest R0

Design + data-model + offline analyzer + dry-run posts only. The seed is bounded
`:representative` (illustrative authorities + **synthetic** subject ids/labels — it mirrors NO
real person/org) — not a live authoritative capture. Live full-universe ingest (OFAC SDN / EU
consolidated / UN SC / UK OFSI / JP-MOF / Interpol public notices) + live posting are Council
Lv6+ + operator gated (Lv7+ for live publication under 1 SBT = 1 vote).

## Do not

- Do not add an `:authority/kind` or id that resolves to etzhayyim/self — G1 (we author no designation).
- Do not add a verdict to `:designation/measure` (`:criminal`/`:guilty`/`:terrorist`/…) — G2.
- Do not make `:designation/asserter` optional, or accept an asserter-less designation — G2.
- Do not add a `:subject/risk-score`/`:guilt`/`:threat-level`, or a private-life PII field on a subject — G5/G7.
- Do not accept a designation with <2 sources, or a commercial-terminal citation — G3 (Rider §2(e)).
- Do not overwrite a `:listed` datom on delisting — append a `:delisted` datom with `:lifted-at` — G4.
- Do not infer disagreement from silence — only an active list-vs-delist conflict is `contested`.
- Do not let a post be `:published` or `serverHeldKey:true`, or call any cell's `.solve()` — G7/G8.
- Do not emit a "who-to-freeze/attack" target-list or an enforcement instruction — G9.
- Do not route narration through a commercial GPU — Murakumo-only (ADR-2605215000).
- Do not use Kotoba/Datomic/SQL — kotoba Datom log only (N7); this actor supersedes the legacy RisingWave sanctions/intel scaffolds.
