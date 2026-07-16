# DEPRECATED: `actor-manifest.jsonld`

The legacy `actor-manifest.jsonld` (single-Worker) persists item/classification/collection the
pre-kotoba way (RisingWave-via-Hyperdrive), which **violates the substrate boundary** (kotoba
EAVT only).

**Canonical manifest is now `manifest.edn`** (kotoba-native), per **ADR-2606072400** — Phase A of
the substrate remediation wave (ADR-2606071800). organizer is now a **free auto-organize file
commons**: content-addressed dedup (Blake3), per-vault isolation, encrypted blobs, member-signed,
and **no content mining**. See `py/agent.py` (+ 14 passing tests) and `kotoba/schema.edn`.

The subscription-discovery pipeline (mailer → organizer → kaiyaku) is retained as a follow-up,
not part of this core conversion. JSON-LD retained one R-cycle, then removed. Do not extend it.
