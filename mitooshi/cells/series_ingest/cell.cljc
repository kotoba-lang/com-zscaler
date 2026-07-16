(ns mitooshi.cells.series-ingest.cell
  "LangGraph Pregel wrapper for mitooshi series_ingest (見通し) — R0 scaffold.
  1:1 port of cells/series_ingest/cell.py (ADR-2606051800).

  G4 source membrane: records a primary-public time-series + its append-only observations
  into the kotoba Datom log. The coded reasoner lives in state_machine.cljc. solve raises
  at R0 — any LIVE ingest (AIS/ADS-B firehose, EDINET pull, Common-Crawl, member-principal
  Google-Trends) is Council Lv6+ + operator gated (G10).")

(defn solve
  [_input-state]
  (throw (ex-info "mitooshi R0 scaffold: series_ingest screens offline; live data ingest (AIS/ADS-B/EDINET/Common-Crawl/member-principal Trends) is Council Lv6+ + operator gated (G10)."
                  {:scaffold true :cell :series-ingest})))
