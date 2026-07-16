#!/usr/bin/env bb
;; Axis 5 — Reproduction (生殖 / 八百万 propagation).
;; Port of src/etzhayyim_organism/sensors/reproduction.py
(ns etzhayyim-organism.sensors.reproduction
  "Axis 5 — Reproduction: fork-bootstrap path documented + ≥1 sister-corp fork.
   Observable: FORK-BOOTSTRAP.md + sister-corp registrations."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the reproduction AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))
        fb       "FORK-BOOTSTRAP.md"]

    (when (c/has? repo fb)
      (add! 3 (str fb " present"))
      (let [body (c/read-text repo fb)]
        (when (clojure.string/includes? body "did:web:")
          (add! 1 "Fork bootstrap mentions did:web (identity-rotated forks)"))
        (when (> (count body) 2000)
          (add! 1 "Fork bootstrap is substantive (>2000 chars)"))))

    (when (c/has? repo "SISTER-CORPS.md")
      (add! 4 "SISTER-CORPS.md present (first observed sister-corp registration)"))

    (let [final-score (min @score 10)
          next-action (if (< final-score 9)
                        "Author first SISTER-CORPS.md registration template"
                        "Maintain sister-corp registry")]
      (c/->AxisReading "reproduction" final-score @ev next-action 3))))
