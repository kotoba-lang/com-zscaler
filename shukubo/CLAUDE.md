# 20-actors/shukubo 宿坊

**Pilgrim-lodging commons — Airbnb/Hotels charter-clean inversion. ADR-2606071600. Status: R0.**

Three-ring lodging commons (same shape as `okaimono`):
- **Ring 0 hospitality-first** — covenantal/pilgrim/mutual-aid stays, `cash≡0` or cost-share.
- **Ring 1 internal SBT↔SBT** — member/actor-hosted stays, USDC+TitheRouter, **zero commission**, warifu, member-signed.
- **Ring 2 external mirror** — data-only lodging discovery; member self-books on the operator's own page; **no inflow**.

## Hard prohibitions (structurally unrepresentable, not policy)
- **No commission** field in any lexicon (G2). shukubo is never merchant-of-record for an external stay.
- **No surge / demand pricing** (G13) — price is flat or cost-share; demand never raises it.
- **No discriminatory host/guest scoring** (G12) — only the *space's* habitability+safety is attested; persons are never scored. Pilgrim-welcome default.
- **No in-stay surveillance** (G14) — cameras/biometrics cannot be a listing feature.
- **No server-held key** (G8); **PII encrypted** (G9, `com.etzhayyim.encrypted.*`).

## Gating
Live external OTA ingest + real external booking = **Council Lv7+ + operator** (G11). R0 ships bounded `:representative` seed only; Ring-1 settlement intent-only until warifu Phase-2.

See ADR-2606071600 for the full rationale and gate table; `manifest.edn` for the canonical gate list.
