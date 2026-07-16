(ns magatama.cells.shionome-ingest.state-machine
  "shionome_ingest — cross-asset capital-flow intake membrane (shionome).
  Per ADR-2606072200. Screens a public market-data batch from context (G1/G2/G3).
  Port of shionome_ingest/cell.py (kotoba-WASM wrapper stripped — pure logic only).
  Live market-data ingest is Council Lv6+ + operator gated (G8)."
  (:require [magatama.cells.shionome-core :as core]))

;; ── State ────────────────────────────────────────────────────────────────────────
;; {:context map? :flows coll? :refusal str?}

(defn screen
  "G1/G2/G3 screen node. Returns updated state with :flows and :refusal.
  Refuses the whole batch on violation — never silently ingest a trade-token / undersourced flow."
  [state]
  (let [ctx    (or (:context state) {})
        batch  (or (:market_batch ctx) (get ctx "market_batch") [])]
    (try
      (assoc state :flows (core/screen-flows batch) :refusal "")
      (catch Exception e
        (assoc state :flows [] :refusal (ex-message e))))))

(defn run-chain
  "Thread state through: START → screen → END."
  [state]
  (screen state))
