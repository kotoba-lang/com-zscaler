# abaki (暴)

**abaki (暴き)** is the Anti-Monopoly & Chokepoint Intelligence Membrane for the etzhayyim project.

It is a Tier-B actor (R0) designed to identify, record, and monitor organizations and individuals who attempt to establish or maintain monopolies across any domain—including natural resources, computational hardware (silicon), digital platforms, biological assets (seeds, genes), infrastructure, and specialist knowledge.

## Purpose

Per Charter Rider v3.0 (ADR-2606073100), the etzhayyim mission explicitly prohibits reliance on and support for entities engaged in monopolistic resource extraction or specialist gatekeeping.

`abaki` enforces this by:
1. **OSINT Tracking:** Ingesting public data (M&A, patents, SEC filings, pricing changes, antitrust lawsuits).
2. **Graphing Control:** Building a `Chokepoint_Entity_Graph` that tracks not just corporations, but the key individuals (board members, CEOs, VC groups) behind them, piercing the corporate veil.
3. **Chokepoint Index (CI):** Calculating a monopoly threat score for entities based on their behavior (e.g., locking down open standards, hoarding IP, crushing competitors).
4. **Structural Bypass (Routing Around):** Automatically outputting a Non-Aligned Entity list. When an entity crosses the CI threshold, other etzhayyim actors (`procure`, `murakumo`, `suki`) automatically sever all dependencies, blocking purchases, compute routing, or data flows to/from the entity.

## Invariants

- **Bypass, Not Attack:** We do not engage in offensive cyber or physical attacks against monopolists. We simply cut them out of our reality. No money, no data, no labor flows to them.
- **Traceable Evidence:** Every Non-Aligned designation must have a clear, cryptographically signed lineage of evidence. No secret blacklists.
- **Individual Tracking:** Serial monopolists cannot hide behind new LLCs. The graph tracks human individuals as vectors of monopoly.

## Architecture

- **Substrate:** OSINT / Pregel Graph
- **Integration:** Feeds directly into Charter Attestation (`com.etzhayyim.apps.etzhayyim.charter-attestation`).
- **Dependencies:** Relies on `intel` for raw data crawling and `amenominaka` for natural language inference.
