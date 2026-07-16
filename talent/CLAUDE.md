# talent — Global Talent Registry (PII Tier 3)

> **kotoba-native (ADR-2606072600).** Canonical manifest is now `manifest.edn`; data model in
> `kotoba/schema.edn`; logic + tests in `py/` (13 green). The four PII-Tier-3 rules are now
> STRUCTURAL: self-sovereign write (caller = subject), Signal-E2E PII (plaintext refused),
> k-anonymity cohort stats, GDPR Art 17 hard delete. Legacy `actor-manifest.jsonld` (RisingWave)
> is DEPRECATED (`DEPRECATED-jsonld.md`). The compliance rules below are the design source.

ISCO-08 scoped candidate/workforce registry. T1 MCP-Compose (Logical Actor). **ADR-0018 PII Tier 3**.

## CRITICAL Compliance Rules

1. **Cohort-first default** — 個別 profile read は consent-gated。default read = cohort 集計 (`TalentCohort` node)
2. **Signal E2E 必須** — identifying fields (`fullName` / `email` / `phone` / `address` / `dateOfBirth` / `governmentId`) は `signal:v1:{ciphertext}` 必須。平文投入は governance gate で reject
3. **Self-sovereign 登録のみ** — 個人 profile は `registerSelf` (caller = subject) 経由のみ。第三者代理登録禁止
4. **GDPR Art 17 cascade** — `forgetSelf` で即時 cascade delete。soft delete 禁止
5. **商用候補者 DB 禁止** — LinkedIn / Indeed resumes / 購入リスト / scraped DB は `governance.dataSources.prohibited` で全 block。license 契約があっても gate 解除には ADR 承認必要

## Allowed Enrichment Sources (public-consent)

- **OrcID** (~20M 研究者、公式 API、CC0)
- **GitHub public profile** (~100M 開発者、公式 API、ToS OK)
- **公的資格 registry** (医師/弁護士/会計士 等、国別公開 DB)

## DID Path Convention

```
did:web:talent.etzhayyim.com:cohort:{iscoCode}:{country}
did:web:talent.etzhayyim.com:profile:{subjectDid-hash}     # self-sovereign のみ
```

## Commands

| Command | NSID | Access |
|---|---|---|
| listOccupations | `com.etzhayyim.apps.talent.listOccupations` | public (cohort 集計) |
| getCohortStats | `com.etzhayyim.apps.talent.getCohortStats` | public (cohort 集計) |
| registerSelf | `com.etzhayyim.apps.talent.registerSelf` | caller = subject 必須 |
| forgetSelf | `com.etzhayyim.apps.talent.forgetSelf` | caller = subject 必須、GDPR Art 17 |
| stats | `com.etzhayyim.apps.talent.stats` | public |

## cross-actor

- Upstream: `isco.etzhayyim.com` (occupation taxonomy)
- Peer: `natural-person.etzhayyim.com` (cohort-first 原則は共通、ADR-0018)
- Downstream: `recruit.etzhayyim.com` (matching delegate、only cohort stats を提供)

## Prohibited Writes (gate 実装必須)

- 第三者 subject への write (`caller.did != subject.did`)
- 平文 identifying field の write
- `governance.dataSources.prohibited` domain からの ingest
- soft delete (`_alive = false`) — hard delete のみ
