# hinagata 雛形

**Legal-document-template commons (法律文書雛形).** Fair, neutral, openly-licensed legal
templates (contracts / agreements / covenants) — decomposed into reusable clauses, each bound
to the **actual public statute** it rests on — published content-addressed for anyone to copy,
adapt, and **execute electronically**.

- **ADR**: 2606111954 · **Status**: 🟢 R1 (template commons publishable + esign bridge live)
- **Schema**: `00-contracts/schemas/legal-template-ontology.kotoba.edn`
- **Lexicons**: `com.etzhayyim.hinagata.{template,clause,statuteCitation}` +
  `com.etzhayyim.esign.{envelope,signature,completedEvent}`

## What it does (the four questions it answers)

1. **A fair worldwide template commons + a publish mechanism** — `methods/publish.py`
   content-addresses every template body to a kotoba IPFS CIDv1 (byte-identical to `ipfs add
   --cid-version=1 --raw-leaves`) and snapshots the commons; anyone can fetch, verify the CID,
   and reuse. 11 seed templates (international sale, mutual NDA, GDPR DPA, JP lease (+EN),
   ILO-aligned employment, consulting, Apache contribution, zero-interest loan, donation,
   JP consumer sale).
2. **EDN linking templates to actual statutes** — the `legal-template-ontology` binds each
   clause to real public law via `:cites-statute` / `:mandated-by` 縁 (CISG, GDPR, ILO, eIDAS,
   ESIGN/UETA, 借地借家法, 民法, 利息制限法, 特定商取引法, 電子署名法, Apache-2.0), each with its
   official source URL.
3. **Published so anyone can use it** — Apache-2.0 + etzhayyim Charter Rider, CID-addressed,
   no paywall.
4. **Electronic contracts** — `methods/esign.py` renders a template to a signable document and
   wires the existing `com.etzhayyim.esign.*` substrate; the member signs client-side with a
   WebAuthn passkey bound to their DID (no-server-key).

## Constitutional boundary

A **commons, never the practice of law** (G1): no advice, no opinion on a matter, no
enforceability certification. A statute citation is a DISCLOSED structural fact (N3), not a
verdict. Edge-primary (N1): a template's groundedness is the integral of its clauses' statute
citations, computed on read. See `CLAUDE.md` for the full gate set.

## Run

```bash
cd 20-actors/hinagata
python3 methods/analyze.py && python3 methods/coverage_report.py && python3 methods/publish.py
python3 methods/esign.py --template tmpl.dpa-gdpr --signer did:plc:alice --signer did:plc:bob
python3 tests/test_analyze.py && python3 tests/test_coverage.py && python3 tests/test_esign.py && python3 tests/test_wasm.py
```
