# 20-actors/demining

Humanitarian Mine Action actor (T1 MCP-Compose). See `60-apps/etzhayyim-project-demining/CLAUDE.md` for project-level design; this file documents actor-manifest-level invariants.

## Actor composition (path-based Multi-DID)

| DID | Role |
|---|---|
| `did:web:demining.etzhayyim.com` | Controller |
| `did:web:demining.etzhayyim.com:actor:survey` | IMAS 08.10 NTS + 08.20 TS |
| `did:web:demining.etzhayyim.com:actor:clearance` | IMAS 09.10 / 09.30 / 09.40 / 09.50 |
| `did:web:demining.etzhayyim.com:actor:release` | IMAS 07.11 Land Release |
| `did:web:demining.etzhayyim.com:actor:eore` | IMAS 12.10 EORE |
| `did:web:demining.etzhayyim.com:actor:victim-assistance` | IMAS 13.10 VA |
| `did:web:demining.etzhayyim.com:actor:assets` | Detector / PPE / MDD / flail inventory |
| `did:web:demining.etzhayyim.com:actor:imsma-sync` | IMSMA XML interop |

## Invariants (CRITICAL)

1. **APM manufacture / stockpile / transfer / deploy is out of scope.** Any pipeline step that could enable these must reject via `validateScope`.
2. **SHA / CHA coordinates are Tier 3** (`Preferences()`). Never write to `app.bsky.feed.post` or public `com.etzhayyim.apps.demining.*` AT Record until `landReleaseDecision` fires.
3. **Victim PII is Tier 3.** Only de-identified aggregates may be socialized.
4. **IMAS is authoritative.** UNSPSC/CPC/HS/ISIC codes are crosswalk only.
5. **Convo system prompt** enforces 1–4. Do not weaken without ADR.
