# jinushi 地主 — production-scale runbook (R2, operator-run)

The sample-scale pipeline (committed snapshots) proves correctness; this runbook takes it to
**full bulk** — NYC PLUTO ~860k tax lots, nationwide DVF (~millions of mutations), more
jurisdictions — **without burdening sources** (one bulk file per source, never the loop / never the
paged API hammered), with every charter gate intact at scale.

All steps are **operator-run** (no-server-key; the loop never does this). Source rate-limits are a
hard stop — if a source pushes back, stop (the standing 負担をかけない directive).

## 1. Acquire the bulk file ONCE (operator)

Use each source's BULK export (one file), not the paged API:

- **NYC PLUTO** (full, ~860k lots, public domain):
  `curl -L 'https://data.cityofnewyork.us/api/views/64uk-42ks/rows.csv?accessType=DOWNLOAD' -o 80-data/jinushi-land/nyc-pluto-full.raw.csv`
  (gitignored — ordinary-person names; local / IPFS cold-tier only, NEVER committed.)
- **geo-dvf** (per-department or whole-France, Licence Ouverte):
  `curl -L 'https://files.data.gouv.fr/geo-dvf/latest/csv/2023/full.csv.gz' -o … && gunzip`
  or per department `…/2023/departements/<dd>.csv`.
- Other cadastres: per the jurisdiction gate (`methods/jurisdiction.cljc`) — only bulk-public +
  owner-names-visible jurisdictions ingest natural persons; others stay aggregate/excluded.

## 2. Process at scale (streaming, bounded memory)

`methods/scale_ingest.cljc` streams the bulk CSV LINE BY LINE — it never materializes the
full vector-of-maps, so 860k lots / millions of mutations run in bounded memory:

```clojure
;; DVF: bounded-memory aggregate of a huge geo-dvf CSV → identical aggregates to the sample path
(jinushi.methods.scale-ingest/dvf-stream-file "80-data/jinushi-land/<dvf-bulk>.csv")
;; PLUTO: stream rows → pluto-aggregate-step → pluto-finalize
;;   natural-person names are ANONYMIZED ON THE FLY (sha256-key) and NEVER accumulated — a
;;   860k-lot run holds/emits zero ordinary-individual names (publish-prudence at scale).
```

Verified: streaming aggregates are byte-identical to the in-memory `dvf-values/analyze*` on the
committed samples (`test_scale_ingest.cljc`).

**Gates that hold at scale**: person anonymization on the fly (no names retained); `sanitize`
(parcel > country area dropped, G4); confidence tiers per source; aggregate-first (G2). Commit only
the **aggregate** snapshot + anonymized records — never the raw bulk with person names.

## 3. datalad superdataset registration (cold tier)

Register the data layer into the DataLad superdataset for git-annex → IPFS (ADR-2605241500):

```bash
e7m-dataset add 80-data/jinushi-land    # → superdataset 90-docs/baien/datasets
                                        #   git-annex local-store → IPFS CID map → PDS datasetPin
```

(The bulk person-bearing raw goes to annex/IPFS cold-tier ONLY, never to the public git working
tree — same line as the sample PLUTO raw.)

## 4. Durable public IPFS pin

The sample clean bundle is already DHT-announced from a local node
(`bafybeih33zveijs2zkc35srt25fcvwsegdvjmddlqb6lorrejwopha5pbe`), but public availability needs an
always-on node or a remote pinning service:

```bash
ipfs add -r --cid-version=1 --pin --ignore 'nyc-pluto*.raw.*' 80-data/jinushi-land   # clean bundle
ipfs routing provide <dir-cid>                                                       # announce
# durable: pin the clean CID to a remote pinning service / the kotobase.net pod (ADR-2606111330)
```

Only the **clean** bundle (no ordinary-person raw) is announced/remote-pinned; the person-bearing
bulk stays local/private.

## Scale invariants (test-enforced where possible)

- 負担をかけない: ONE bulk file per source; processing is offline; the loop never fetches at scale.
- Person privacy at scale: names anonymized streaming; clean public bundle excludes person raw.
- Honesty: coverage is national-park (protected-public-land) share, not all-land; values are
  aggregate medians; non-adjudicating.
