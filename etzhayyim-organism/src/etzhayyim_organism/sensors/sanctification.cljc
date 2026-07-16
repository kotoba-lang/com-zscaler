#!/usr/bin/env bb
;; Axis 10 — Sanctification (聖化 / Sola Scriptura → Charter Rider).
;; Port of src/etzhayyim_organism/sensors/sanctification.py
(ns etzhayyim-organism.sensors.sanctification
  "Axis 10 — Sanctification: Charter Rider on all first-party Apache-2.0 packages.
   Observable: NOTICE files + CHARTER-RIDER.md presence + applicator tool."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the sanctification AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))]

    (when (c/has? repo "CHARTER-RIDER.md")
      (add! 3 "CHARTER-RIDER.md canonical at root"))

    (let [notice-count (c/count-glob repo "**/NOTICE")]
      (cond
        (>= notice-count 39) (add! 4 (str "NOTICE propagated to first-party packages (" notice-count ")"))
        (>= notice-count 10) (add! 2 (str "NOTICE partially propagated (" notice-count ")"))
        (>= notice-count 1)  (add! 1 (str "NOTICE seeded (" notice-count ")"))))

    (when (c/has? repo "70-tools/charter-rider-applicator")
      (add! 2 "Charter Rider applicator tool present"))

    (when (c/has? repo "LICENSE")
      (add! 1 "LICENSE (Apache 2.0) at root"))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "Propagate organism-axis affiliation to package READMEs"
                        "Run charter-rider-applicator across first-party packages")]
      (c/->AxisReading "sanctification" final-score @ev next-action 1))))
