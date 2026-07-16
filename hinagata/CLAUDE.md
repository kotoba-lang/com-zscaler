# hinagata 雛形 — legal-document-template commons (法律文書雛形)

**ADR**: 2606111954 · **depends**: 2605262700 (chigiri 契 / legal-procedure substrate) +
2605262800 (global legal-corpus) · 2605231230 (esign envelope) + 2605231525 (no-server-key) +
2605181100 (Signal key-wrap) · 2605312345 (Datom = canonical state) · 2605215000 (Murakumo-only)
· 2606101000 (rasen — same KG-mirror architecture). **Status**: 🟢 R1 — template commons
publishable (content-addressed) + electronic-contract bridge live.

hinagata ("雛形" = template / mould) is the **fairness/access sibling** of the legal lineage.
Where **chigiri 契** runs the legal-**procedure** substrate and the **global legal-corpus**
(ADR-2605262800) ingests the **statutes** themselves, hinagata holds the published, fair,
reusable **document templates** — contracts, agreements, covenants, notices — decomposed into
reusable **clauses**, and **binds every clause to the actual public statute it cites or is
mandated by** (`:cites-statute` / `:mandated-by` 縁). A template is never free-floating
boilerplate: it is a structure traceable to real law. It runs an **edge-primary statutory
groundedness** pass (integrated statutory anchoring over a template = the disclosed binding
load accumulated on its clauses' citation 縁) **routed to PUBLIC RELEASE** (anyone may use it
freely), and **executes contracts electronically** through the existing esign substrate.

It answers the four questions that motivated it: (1) a fair worldwide legal-template commons +
a publish mechanism — yes (`publish.py` content-addresses every body, anyone can fetch + verify
+ reuse); (2) EDN linking templates to actual statutes — yes (`legal-template-ontology` +
`:cites-statute`/`:mandated-by`); (3) published so anyone can use it for contracts — yes
(Apache-2.0 + Charter Rider, CID-addressed); (4) electronic contracts — yes (`esign.py` wires
`com.etzhayyim.esign.*`, member signs client-side, no-server-key).

## Hard gates (constitutional — read before any change)

- **G1 — a COMMONS, NEVER the practice of law.** This is the defining inversion (mirrors
  chigiri's UPL boundary). hinagata issues **no advice, no opinion on a specific matter, no
  representation, no enforceability certification**. The unit is always a **template / clause /
  statute-link** — never a party, a matter, or a recommendation. A statute citation is a
  DISCLOSED structural fact (this clause cites this article), never a hinagata verdict that the
  clause is valid or sufficient. UPL is structurally excluded.
- **G2 — edge-primary (N1).** Statutory grounding lives ONLY on edges (`:en/binding-load`
  weighted by disclosed `:clause/optionality`). A template's groundedness = the **integral of
  its incident clause `:cites-statute` / `:mandated-by` + direct `:cites-statute` 縁**, computed
  **on read** — never a stored per-template score. There is no `:lt/score-of-template`.
- **G3 — non-adjudicating (N3).** Statute citations, instrument names, optionality categories
  (`:mandatory` / `:recommended` / `:optional`) are **DISCLOSED facts** sourced from the public
  instrument or its official guidance, never hinagata verdicts. Never certifies validity,
  sufficiency, or enforceability.
- **G4 — public venue + open license.** Apache-2.0 + Charter Rider. Every template body is
  **content-addressed** (CIDv1 raw/sha2-256, byte-identical to `ipfs add --cid-version=1
  --raw-leaves`) and **free to copy + adapt**. Never a paywalled forms vendor.
- **G5 — sourcing honesty.** Every record carries `:lt/sourcing :authoritative |
  :representative`. Statutes carry a public official source URL. Coverage of all families / all
  jurisdictions is ~0 by design; **unbound clauses** (not yet citing a statute) are surfaced as
  the next-wave worklist, not hidden (`coverage_report.py`).
- **G6 — Murakumo-only narration.** Any LLM narration routes through Murakumo (ADR-2605215000).
- **G7 — outward-gated.** Live **legal-corpus binding** (ADR-2605262800 statutes), scope
  expansion, and IPFS pin / IPNS publish require Council + operator DID. R0/R1 = analyzer +
  schema + seed + content-addressed publish snapshot.
- **G8 — no-server-key (ADR-2605231525).** hinagata holds **NO signing key**. `esign.py` builds
  **UNSIGNED** envelopes and verifies signature **structure** only; the member signs
  client-side with their own WebAuthn passkey / wallet. The server never signs.

## Layout

```
20-actors/hinagata/
├── CLAUDE.md                                  # this file
├── README.md                                  # short orientation
├── manifest.jsonld                            # actor manifest (5 cells, 8 gates)
├── data/
│   ├── seed-legal-template-graph.kotoba.edn   # hand-curated PUBLIC template↔clause↔statute seed
│   └── ingest-sources.edn                     # bounded public statute-source allowlist (G7)
├── methods/                                   # pure-stdlib (no numpy) → kotoba pywasm-runnable
│   ├── analyze.py                             # edge-primary statutory-groundedness analyzer
│   ├── datom_emit.py                          # kotoba Datom-log (EAVT) emitter — canonical state
│   ├── coverage_report.py                     # honest coverage + statute-binding integrity (G5)
│   ├── esign.py                               # electronic-contract bridge → com.etzhayyim.esign.*
│   ├── publish.py                             # OUTWARD (G7): content-address every body → 80-data
│   └── cid.py                                 # kotoba IPFS CIDv1 (raw/sha2-256) — ipfs-parity, no daemon
├── tests/                                     # 23 tests, pure stdlib (network-free)
│   └── test_analyze.py · test_coverage.py · test_esign.py · test_wasm.py
├── wasm/                                      # kotoba pywasm component (componentize-py)
│   ├── wit/world.wit                          # WIT world (analyze/datoms/coverage/envelope exports)
│   ├── app.py                                 # export bodies (runnable in dev; embeds seed for WASM)
│   └── build.sh                               # embed seed → componentize → CID → DID service descriptor
└── out/                                       # GENERATED — do not hand-edit
```

## Run

```bash
cd 20-actors/hinagata
python3 methods/analyze.py           # → out/groundedness-report.md
python3 methods/datom_emit.py        # → out/legal-template-datoms.kotoba.edn (EAVT)
python3 methods/coverage_report.py   # → out/coverage-report.md

# electronic contract: render a template + build an UNSIGNED esign envelope (member signs client-side)
python3 methods/esign.py --template tmpl.dpa-gdpr --signer did:plc:alice --signer did:plc:bob
python3 methods/cid.py out/contract-tmpl.dpa-gdpr.md   # re-derive the document CID (ipfs-parity)

# OUTWARD (G7) — content-address every template body + snapshot the public commons
python3 methods/publish.py           # → 80-data/legal-templates/{bodies,PUBLISH.md,publish-manifest.json}

python3 tests/test_analyze.py && python3 tests/test_coverage.py && python3 tests/test_esign.py && python3 tests/test_wasm.py  # 23 green
```

## Ontology (legal-template-ontology, `00-contracts/schemas/`)

- **nodes** `:lt/kind` ∈ `{:template, :clause, :statute, :jurisdiction, :concept, :license}`
  with template (`:template/title :template/lang :template/license :template/stance
  :template/body-cid`), clause (`:clause/role :clause/optionality`), statute
  (`:statute/citation :statute/instrument :statute/jurisdiction :statute/url`), jurisdiction
  (`:jurisdiction/code :jurisdiction/system`), concept (`:concept/code`) facets.
- **edges** `:en/kind` ∈ `{:has-clause, :cites-statute, :mandated-by, :instantiates,
  :governed-by, :applies-in, :translates, :supersedes, :conflicts-with, :derived-from}`
  carrying `:en/binding-load` ∈ [0,1] (where statutory anchoring lives) and, on citation
  edges, a DISCLOSED `:en/force` (`:mandated` / `:cited` / `:referenced`).
- **derived** `:bond/groundedness` · `:bond/reusability` · `:bond/statute-pull` ·
  `:bond/jurisdiction-reach` — transient, computed on read, never persisted (N1/G2).
- **optionality/weight** disclosed scale: `:mandatory 1.0 :recommended 0.6 :optional 0.3`.

## Electronic contract (esign bridge)

`methods/esign.py` is the contract-signing flow that wires the existing
`com.etzhayyim.esign.*` lexicons (ADR-2605231230). It (1) **renders** a template into a
deterministic contract body that carries its statutory provenance (each clause lists the public
law it rests on), (2) **content-addresses** the body (CIDv1 raw + SHA-256), (3) builds the
**UNSIGNED** `com.etzhayyim.esign.envelope` rostering DID signers, and (4) **verifies** a
signature's structural binding (roster membership + document-hash anti-tamper + accepted
WebAuthn algorithm) and fires a `completedEvent` only when every roster signer has a valid
signature. The cryptographic WebAuthn / DID-key verification is kotoba-auth's job; hinagata is
the structural gate that precedes it. **no-server-key**: the member signs client-side with their
own passkey — hinagata never holds a signing key (G8 / ADR-2605231525).

## Cross-links

`:lt/links` can name a node in a sibling graph — e.g. a **chigiri** procedure, a **musubi**
covenant ceremony, or (on `cl.signature-esign`) the `com.etzhayyim.esign.envelope` shape.
Identity assurance of signers routes through **shomei** (ADR-2606072100); on-chain settlement
of a contract's payment leg routes through **warifu** (ADR-2605302000). hinagata supplies fair
public templates and a faithful signing envelope; it does not advise, represent, or certify.
