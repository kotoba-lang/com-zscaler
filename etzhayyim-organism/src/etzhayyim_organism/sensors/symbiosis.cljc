#!/usr/bin/env bb
;; Axis 6 — Symbiosis (共生 / Tree of Life branches).
;; Port of src/etzhayyim_organism/sensors/symbiosis.py
(ns etzhayyim-organism.sensors.symbiosis
  "Axis 6 — Symbiosis: multi-substrate roots present and reachable.
   Observable: did:web Worker + IPFS pinner + L2 contract + MST projector + Base anchor cron."
  (:require [etzhayyim-organism.sensors.common :as c]))

(def substrates
  "Pairs [label path] for each required substrate component.
   Mirrors SUBSTRATES in the Python file."
  [["did:web Worker"     "50-infra/etzhayyim-did-web"]
   ["MST projector"      "50-infra/mst-projector"]
   ["IPFS pinner"        "50-infra/ipfs-pinner"]
   ["L2 anchor contract" "50-infra/l2-anchor-contract"]
   ["Anchor cron"        "50-infra/anchor-cron"]
   ["geth-private"       "50-infra/geth-private"]
   ["Holochain"          "50-infra/holochain"]])

(defn read
  "Compute the symbiosis AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])]
    (doseq [[label p] substrates]
      (when (c/has? repo p)
        (vswap! score + 1)
        (vswap! ev conj (str label " scaffolded"))))

    (let [final-score (min @score 10)
          next-action (if (< final-score 9)
                        "Establish ≥1 substrate pair operating in production"
                        "Verify cross-substrate anchoring liveness")]
      (c/->AxisReading "symbiosis" final-score @ev next-action 2))))
