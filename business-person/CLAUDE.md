# 20-actors/business-person

**T1 MCP-Compose Actor.** Public organizational business person intelligence. Murakumo LLM-powered structured extraction, dedup, and normalization.

-> nanoid / domain: `deps.toml [[mitama_actors]]`
-> Pipeline: `20-actors/business-person/actor-manifest.jsonld`

## Identity

| Key | Value |
|---|---|
| **AT bot DID** | `did:web:business-person.etzhayyim.com` |
| **executionTier** | T1 (PDS Shared Executor) |
| **performerType** | `service` |
| **uiType** | `yoro` |
| **governance** | `public` (public-filings-only) |

## Capabilities

`graph.query`, `graph.write`, `graph.vectorSearch`, `agent.chat`, `browser.fetch`, `derive:social`

## Graph Labels

`BusinessPerson`, `Person`, `OfficerRole`, `BoardMembership`, `Affiliation`, `PersonnelChange`, `RegistrySource`, `RoleType`

## LLM Agent (Murakumo)

- **Wiki markup cleanup**: regex pre-extract + Murakumo structured JSON output (name/title/keep)
- **Dedup**: graph lookup + LLM fuzzy match (same person different formatting)
- **Unstructured extraction**: HTML/XBRL/filing text -> Murakumo officer extraction
- **SEC EDGAR DEF 14A**: inline XBRL text -> Murakumo structured parse

## Data Sources

- Wikipedia API (key_people infobox, public domain)
- SEC EDGAR (XBRL filings, no auth)
- EDINET (Japan FSA, subscription key)
- gBizINFO (Japan corporate registry)
- Companies House (UK, API key)
- Corporate HP (HTML table extraction)

## Triggers

- `subscribeRepos`: `com.etzhayyim.apps.businessPerson.{person,officerRole,boardMembership,affiliation,personnelChange,collectionJob}`
- `cron`: `0 3 * * 1` (weekly report), `0 */6 * * *` (coverage snapshot)
