(ns kamado.cells.asset-observation.state-machine
  "cljc port of cells/asset_observation/cell.py (ADR-2606051500).
  R0 scaffold — kotoba-native public-refinery asset observation.
  A resilience + transition map, NEVER a target-list (G4).
  .solve() raises until Council Lv6+ + operator activation (G8).")

(defn solve [_state]
  (throw (ex-info "kamado R0 scaffold: asset_observation live ingest is Council Lv6+ + operator gated (G8); R0 = offline analyze.py over the :representative seed (ADR-2606051500)"
                  {:cell :asset-observation :actor :kamado :status :r0-scaffold})))
