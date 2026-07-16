(ns magatama.cells.shionome-rotation-weave.state-machine
  "shionome_rotation_weave — top rotation pair どこからどこへ (shionome).
  Per ADR-2606072200. Aggregate, edge-primary (G4); no per-asset score.
  Port of shionome_rotation_weave/cell.py."
  (:require [magatama.cells.shionome-core :as core]))

;; ── State ────────────────────────────────────────────────────────────────────────
;; {:context map? :flows coll? :rotation map?}

(defn weave
  "Compute top rotation. Mirrors Python _weave node."
  [state]
  (let [flows (or (:flows state)
                  (get-in state [:context :flows])
                  (get-in state ["context" "flows"])
                  [])]
    (assoc state :rotation (or (core/top-rotation flows) {}))))

(defn run-chain
  "Thread state through: START → weave → END."
  [state]
  (weave state))
