#!/usr/bin/env bb
;; Axis 2 — Metabolism (代謝 / 産霊 musuhi).
;; Port of src/etzhayyim_organism/sensors/metabolism.py
(ns etzhayyim-organism.sensors.metabolism
  "Axis 2 — Metabolism: donation inflow → 10% tithe → public-fund redistribution
   loop is deployable.
   Observable: TitheRouter / PublicFund / ChartersCompliance code +
   deployment scripts. Score caps until Base Sepolia deploy is observed on-chain."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the metabolism AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))
        tithe    "50-infra/etzhayyim-tithe-router"
        fund     "50-infra/etzhayyim-public-fund"
        comp     "50-infra/etzhayyim-charters-compliance"]

    (when (c/has? repo tithe) (add! 2 (str tithe "/ scaffolded")))
    (when (c/has? repo fund)  (add! 2 (str fund  "/ scaffolded")))
    (when (c/has? repo comp)  (add! 1 (str comp  "/ scaffolded")))

    ;; Constitution test coverage
    (let [constitution-sols (c/count-glob repo "50-infra/**/Constitution.sol")]
      (when (>= constitution-sols 1)
        (add! 1 (str "Constitution.sol present (" constitution-sols " location(s))"))))

    ;; On-chain anchor — testnet/mainnet broadcast
    (when (some #(c/has? repo (str % "/broadcast")) [tithe fund comp])
      (add! 4 "On-chain Foundry broadcast present (testnet/mainnet deploy observed)"))

    (let [final-score (min @score 10)
          next-action (if (< final-score 9)
                        "Deploy TitheRouter to Base Sepolia (post-Council)"
                        "Confirm first observed tithe routing tx")]
      (c/->AxisReading "metabolism" final-score @ev next-action 3))))
