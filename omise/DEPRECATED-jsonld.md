# DEPRECATED: `actor-manifest.jsonld`

`actor-manifest.jsonld` (+ legacy `CLAUDE.md`, `MIGRATION-TODO.md`) is the **pre-kotoba**
JSON-LD scaffold. It describes a generic multi-tenant Shopify clone with an implied
RisingWave/SQL read path, which **violates the substrate boundary** (kotoba EAVT only; no
RisingWave/SQL/Kysely/Lance as canonical) and lacks a charter-clean inversion stance.

**Canonical manifest is now `manifest.edn`** (kotoba-native), per **ADR-2606071400**, which
promotes omise to its sharp niche: the **seller-side storefront commons** (Shopify-layer for
internal SBT-gated sellers) whose listings feed `okaimono` Ring 1. Zero commission, zero
subscription, USDC+TitheRouter via warifu, member-signed.

The JSON-LD files are retained for **one R-cycle** for reference, then removed. Do not extend
them. New work lands in `manifest.edn`, `lex/*.edn`, `cells/*.edn` (mirroring `okaimono`).
