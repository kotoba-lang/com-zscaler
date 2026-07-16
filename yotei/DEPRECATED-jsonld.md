# DEPRECATED: `actor-manifest.jsonld`

The legacy `actor-manifest.jsonld` (T1 MCP-Compose) expresses the calendar read/write path as
**Cypher over RisingWave (yata-graph)**, which **violates the substrate boundary** (kotoba EAVT
only; no RisingWave/SQL/Cypher as canonical).

**Canonical manifest is now `manifest.edn`** (kotoba-native), per **ADR-2606072200** — Phase A of
the substrate remediation wave (ADR-2606071800). yotei is now a **free scheduling commons** with
append-only bookings, a structural **no-double-book** invariant, member-signed confirmation, and
**no booker-data harvesting**. See `py/agent.py` (+ 11 passing tests) and `kotoba/schema.edn`.

Retained one R-cycle for reference, then removed. Do not extend it.
