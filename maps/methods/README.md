# maps — kotoba-native methods + R1 operator runbook

ADR-2606064500. The static-feature substrate migration off RisingWave onto the kotoba Datom
log. These methods are stdlib-only; the real-H3 test layer needs an `h3` install (a venv).

## Methods

| file | what |
|---|---|
| `analyze.py` | Earth-coverage report off the Datom log (features by label, res-6 cell fraction, bbox, anchor density). Real H3 if `h3` installed, else an honest degree-grid stand-in. |
| `ingest.py` | legacy `vertex_spatial` export → `:feature/*`; stamps the H3-cell index (`:feature.cell/rN`) + the name-search tokens (`:feature/name-token`); `--emit-edn` backfill (dedup vs seed); `--push` to `kg.ingest_batch` (G7-gated). |
| `search.py` | kotoba-native name search (R2): the `cmdSearchPlaces` (`name LIKE`) successor. `name_tokens` (ASCII prefixes + CJK bigrams; one tokenizer for write+read) + `search_places` = `AVET(:feature/name-token, …)` ranked by token overlap, label-filterable. |
| `test_search.py` | 12 tests: tokenizer (prefix/bigram/contract), ASCII-prefix + CJK search, ranking, label filter, limit, fail-soft. |
| `kotoba_local.py` | in-memory EAVT/AVET **reference store** — the executable proof of the §2 query design + the contract `src/kotoba-spatial.ts` issues to real kotoba. |
| `test_methods.py` | 12 tests: normalization, sourcing honesty, coverage math, push-gate. |
| `test_avet_roundtrip.py` | 10 tests: AVET cell round-trip (index contract always; real Tokyo-Station H3 e2e when `h3` present). |
| `transit.py` | kotoba-native transit READS (R2 aux): `next_departures_at_stop` = `AVET(:transit.stop-time/stop, …)` sorted by departure-time (idx_maps_stop_time_stop_dep successor); `trips_on_route` = `AVET(:transit.trip/route, …)`. The read complement to the GTFS aux write path. |
| `test_transit.py` | 8 tests: departure sort (incl. GTFS >24:00:00), `after` cutoff, limit, stop isolation, unknown-stop empty, trips-on-route, fail-soft. |
| `reverse.py` | kotoba-native reverse geocoding (R2): the `cmdPlaceReverseGeocode` successor. `reverse_geocode` = `AVET(:feature.cell/r{res}, grid_disk(cell, ring))` candidates → `haversine_m` rank nearest-first, label-filterable. The H3 cell index doubles as a proximity index. |
| `test_reverse.py` | 10 tests: haversine (pure, always) + real-H3 e2e (nearest=Tokyo Station, label filter, limit, distant-Haneda excluded, ocean empty). |
| `chunk.py` | kotoba-native chunk read (§2): the `cmdGetChunk` HTTP reference — `AVET(:feature.cell/r{lod}, cells)` → grouped GeoJSON `{chunks:{cell:{label:[Feature]}}}`. The cell-read sibling of transit/search/reverse (previously only the TS adapter + in-memory store proved it). |
| `test_chunk.py` | 6 tests (real-H3): cell/label grouping, label filter, point geometry, coarse-LOD aggregate, empty cell + the **4-read integration** (chunk · search · reverse · transit compose over ONE graph). |
| `verify.py` | the **R1 readiness check** (operator/CronJob): runs all four reads against a kotoba endpoint, prints `{chunk,search,reverse,transit,allOk}`, exit 0 iff `allOk`. `python3 verify.py --endpoint URL [--lat --lon --query --stop]`. |
| `test_verify.py` | 4 tests (real-H3): all-reads-ready, ocean-probe-not-ready, endpoint-down-fail-soft, report shape. |
| `kotoba_local_server.py` | stdlib HTTP **stand-in** for the kotoba XRPC surface (`kg.ingest_batch` Bearer + `graph.sparql` AVET + `kg.entity`) — the local target for the R1 dry-run. |
| `test_e2e_http.py` | 5 tests: the full maps↔kotoba **HTTP loop** (auth gate 401, real `ingest.push_batch`, AVET cell query, label-filter, point lookup). |

```bash
./run_tests.sh                                  # stdlib (real-H3 layer auto-skips)
PYTHON=/path/to/venv/bin/python ./run_tests.sh  # full real-H3 e2e (python -m venv … ; pip install h3)
```

## R1 — go-live runbook (operator + Council gated)

R0 (landed) is offline-only: kotoba-preferring `getChunk` falls through to RisingWave because
no maps kotoba endpoint is wired. R1 makes kotoba primary.

**Dry-run first (no gate, no live infra)** — prove the wire loop against the local stand-in:
```bash
python3 methods/kotoba_local_server.py --port 8077 --token dev &     # local kotoba stand-in
MAPS_OPERATOR_GATE=1 KOTOBA_AUTH=dev KOTOBA_ENDPOINT=http://127.0.0.1:8077 \
  python3 methods/ingest.py --export data/ingest/sample-vertex-spatial.json --push
# → pushes over real HTTP; then point a maps Worker at the same endpoint to exercise getChunk.
# test_e2e_http.py automates exactly this loop in CI.
```
Then the live steps: **Every step is operator/Council
gated (G7) and member/operator-DID-signed (G4 no-server-key).**

1. **Stand up the kotoba endpoint for maps.** Set the Worker var (wrangler `[vars]` /
   secret), NOT a server key:
   ```
   KOTOBA_ENDPOINT = https://<kotoba-host>     # reads: kg.entity / graph.sparql
   KOTOBA_AUTH     = <member/operator DID bearer, CACAO>   # writes only; never platform-held
   ```
   With `KOTOBA_ENDPOINT` set, `cmdGetChunk` already prefers kotoba (`servedBy:"kotoba"` in the
   response); unset → `servedBy:"risingwave"`. The cut is reversible by unsetting the var.

2. **Backfill `vertex_spatial` → `:feature/*`.** Export the live table (or a region slice) to
   the `{"rows":[…]}` shape (see `data/ingest/sample-vertex-spatial.json`) and:
   ```
   python3 methods/ingest.py --export <vertex_spatial-export>.json --emit-edn   # inspect EDN
   MAPS_OPERATOR_GATE=1 KOTOBA_ENDPOINT=… KOTOBA_AUTH=… \
     python3 methods/ingest.py --export <export>.json --push                    # live ingest
   ```
   `--push` refuses without `MAPS_OPERATOR_GATE=1` + `KOTOBA_AUTH` + `KOTOBA_ENDPOINT`.

3. **Prove parity on the Tokyo anchor.** Drive `getChunk` for the maps-3d walkable cells
   (res-12 around 35.6812,139.7671) against both substrates and compare:
   - correctness: same feature ids per cell (kotoba AVET vs RisingWave bbox); `servedBy` flips.
   - latency: p50/p95 of the kotoba `queryByCells` AVET read must meet the **<50 ms** budget
     the legacy BTREE path holds. Measure with the maps-3d streamer's 4 s tick under a walk.
   The AVET retrieval logic is already proven offline (`test_avet_roundtrip.py`, real Tokyo
   H3); R1 measures it at scale on the live endpoint. **Readiness gate** — run the readiness
   verifier against the live endpoint and require exit 0 before flipping reads kotoba-primary:
   ```
   python3 methods/verify.py --endpoint "$KOTOBA_ENDPOINT" \
     --lat 35.6812 --lon 139.7671 --query tok --stop <a-real-stop-id>
   # exits 0 iff chunk + search + reverse + transit all return → reads are live-ready
   ```

4. **R2/R3** (separate PRs): port the 5 bulk-ingest dumpers + the 172-command tail through the
   adapter (`ingestFeatures` / `queryByCells`); then delete the `HYPERDRIVE` binding, the
   `vertex_*` migrations, and the Kysely path (`MIGRATION-NOTES.md`).
