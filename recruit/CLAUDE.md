# recruit — Global Job-Posting Aggregator

Public-feed-only corporate-linked 求人 aggregator. T1 MCP-Compose (Logical Actor).

## Scope

- 6 public sources (ESCO / O*NET / EURES / HelloWork / USAJOBS / Job Bank) のみ
- 全 JobPosting は `legalEntityDid` anchor 必須 (LEI / JP 法人番号 / SIREN / CH number 等)
- ISCO-08 occupation mapping 必須
- 商用 scrape (LinkedIn / Indeed / Wantedly 等) は **禁止** — `governance.dataSources.prohibited` で明示

## DID Path Convention

```
did:web:recruit.etzhayyim.com:posting:{sourceCode}:{sourceId}
did:web:recruit.etzhayyim.com:source:{sourceCode}
```

`sourceCode` = `esco` / `onet` / `eures` / `hellowork` / `usajobs` / `jobbank`

## Commands

| Command | NSID | Description |
|---|---|---|
| listPostings | `com.etzhayyim.apps.recruit.listPostings` | Filter by iscoCode / country / employerDid |
| getPosting | `com.etzhayyim.apps.recruit.getPosting` | Detail + legal-entity JOIN |
| ingestTaxonomy | `com.etzhayyim.apps.recruit.ingestTaxonomy` | ESCO/O*NET taxonomy batch ingest |
| ingestJobPostings | `com.etzhayyim.apps.recruit.ingestJobPostings` | Ingest real public ATS job postings |
| listJobIngestRuns | `com.etzhayyim.apps.recruit.listJobIngestRuns` | List public job ingest run history |
| recommendCohorts | `com.etzhayyim.apps.recruit.recommendCohorts` | JobPosting × TalentCohort × DemandForecast cohort-first recommendations |
| matchStats | `com.etzhayyim.apps.recruit.matchStats` | Cohort-first matching operational counts |
| proposeCohortMatch | `com.etzhayyim.apps.recruit.proposeCohortMatch` | Persist a PII-free cohort match proposal |
| listMatchProposals | `com.etzhayyim.apps.recruit.listMatchProposals` | List persisted cohort match proposals |
| getMatchProposal | `com.etzhayyim.apps.recruit.getMatchProposal` | Get one persisted cohort match proposal |
| decideMatchProposal | `com.etzhayyim.apps.recruit.decideMatchProposal` | Mark a persisted cohort match proposal accepted/rejected/expired |
| listMatchDecisionEvents | `com.etzhayyim.apps.recruit.listMatchDecisionEvents` | List decision events for a persisted cohort match proposal |
| stats | `com.etzhayyim.apps.recruit.stats` | Coverage stats |

## Real Job Ingest

```bash
pnpm run recruit:jobs:dry-run -- --platform lever --limit 5
pnpm run recruit:jobs:ingest -- --platform lever --limit 100 --batch-size 50
```

`recruit:jobs:ingest` requires `RW_CONN` or the default local RisingWave/Postgres endpoint. It runs graph migrations before insert unless `--skip-migrate` is passed, then writes `/tmp/recruit-real-job-ingest-run.json`.

K8s worker:

- Manifests: `50-infra/k8s/recruit-job-ingester/`
- Internal endpoint: `http://recruit-job-ingester.recruit-actors.svc.cluster.local:8080/xrpc/com.etzhayyim.apps.recruit.ingestJobPostings`
- Health: `/healthz`; readiness: `/readyz`
- Real writes persist run history into `vertex_recruit_job_ingest_run`

Live smoke (2026-05-07):

```bash
RW_CONN="$(security find-generic-password -s etzhayyim.rw -a ROOT_URL -w)" \
DATABASE_URL="$RW_CONN" \
pnpm run recruit:jobs:ingest -- \
  --platform lever --limit 1 --batch-size 1 \
  --skip-migrate --ignore-checkpoint --allow-unanchored
```

Result: 1 public Lever posting inserted, latest live counts observed: `ashby=176`, `greenhouse=6877`, `lever=21`; latest run `status=succeeded`, `inserted=1`.

Operational notes:

- Use `--skip-migrate` for smoke only after required DDL exists; full migration/index creation can run long on live RisingWave.
- `--ignore-checkpoint` is for smoke/replay. Normal scheduled runs should keep checkpointing enabled.
- Default strict mode skips unanchored rows unless `RECRUIT_ENABLE_LIVE_ANCHOR_LOOKUP=1` enables bounded legal-entity lookup.
- `--allow-unanchored` is an explicit escape hatch for public posting smoke or backfill; do not use it for compliance-strict scheduled ingestion.

## Compliance

- 全 ingest は `validateTaxonomySource` gate を通過必須 → `governance.dataSources.allowed` domain のみ許可
- 商用 source 追加は ADR + partnership contract が prerequisites
- Archive: Iceberg S3 Parquet、`sourceLicense` field で lineage 保持 (CC-BY-4.0 / public-domain 等)
- Matching/recommendation は cohort-first のみ。個人 candidate profile / identifying fields を読まず、`mv_recruit_cohort_match_candidate` から aggregate recommendation を返す。
- Persisted proposals は `vertex_recruit_match_proposal` に保存する。`props` も PII-free evidence のみ許可。
- Proposal decision は review state の記録だけ。個人 candidate への contact / outreach / notification は別の consent-gated flow が必要。

## cross-actor

- Upstream: `isco.etzhayyim.com` (occupation taxonomy)、`legal-entity.etzhayyim.com` (employer authenticity)
- Downstream: `talent.etzhayyim.com` (matching delegate、PII 分離)、`business-person.etzhayyim.com`
