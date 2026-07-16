(ns magatama.cells.shionome-flow-graph.state-machine
  "shionome_flow_graph — per-bucket net capital-flow index (shionome).
  Per ADR-2606072200. Capital-movement kinds only; edge-primary (G4).
  Port of shionome_flow_graph/cell.py (kotoba-WASM wrapper stripped — pure logic only)."
  (:require [magatama.cells.shionome-core :as core]))

;; ── State ────────────────────────────────────────────────────────────────────────
;; {:context map? :flows coll? :net coll?}

(defn index
  "Compute per-bucket net flows from state. Mirrors Python _index node."
  [state]
  (let [flows (or (:flows state)
                  (get-in state [:context :flows])
                  (get-in state ["context" "flows"])
                  [])]
    (assoc state :net (core/net-flow flows))))

(defn run-chain
  "Thread state through the single-node graph: START → index → END."
  [state]
  (index state))
