#!/usr/bin/env bb
;; Axis 9 — Anti-fragility (反脆弱 / Reformed Just War).
;; Port of src/etzhayyim_organism/sensors/antifragility.py
(ns etzhayyim-organism.sensors.antifragility
  "Axis 9 — Anti-fragility: chaos engineering charter + transparent force registry
   + demonstrated recovery from real failures.
   Observable: chaos charter, force-rd package, Scenario rotation breadth."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the antifragility AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))
        ;; Look for chaos charter docs
        base     (java.io.File. (str repo) "90-docs")
        chaos    (when (.isDirectory base)
                   (->> (.listFiles base)
                        (filter #(let [n (clojure.string/lower-case (.getName %))]
                                   (and (clojure.string/includes? n "chaos")
                                        (clojure.string/includes? n "charter"))))
                        seq))]

    (when chaos
      (add! 3 (str "Chaos charter: " (.getName (first chaos))))
      (let [body    (slurp (first chaos))
            n-scen  (+ (count (re-seq #"## [Ss]cenario" body)))]
        (when (>= n-scen 10)
          (add! 2 (str "≥10 chaos scenarios (" n-scen ")")))))

    (when (c/has? repo "60-apps/etzhayyim-transparent-force-rd")
      (add! 3 "Transparent Force R&D registry scaffolded"))
    (when (c/has? repo "50-infra/etzhayyim-force-authorization")
      (add! 2 "Force-authorization on-chain scaffold present"))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "Execute Gen 1 Scenario 1 (network partition) at 2026-08-13"
                        "Scaffold chaos charter and transparent-force registry")]
      (c/->AxisReading "antifragility" final-score @ev next-action 2))))
