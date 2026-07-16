#!/usr/bin/env bb
;; Axis 7 — Diversity (多様性 / 八百万-kami).
;; Port of src/etzhayyim_organism/sensors/diversity.py
(ns etzhayyim-organism.sensors.diversity
  "Axis 7 — Diversity: variation in cells, apps, protocols.
   Observable: count of distinct cell directories, app directories, protocol packages."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the diversity AxisReading for `repo` (string path)."
  [repo]
  ;; Java NIO PathMatcher does not treat trailing "/" as "dirs only".
  ;; We count all entries under each dir (matching * at depth=1) and then
  ;; filter to directories only using java.io.File.isDirectory.
  ;; This is equivalent to Python's glob("dir/*/") which yields only dirs.
  (let [count-dirs (fn [rel]
                     (let [d (java.io.File. (str repo) (str rel))]
                       (if (.isDirectory d)
                         (count (filter #(.isDirectory %) (.listFiles d)))
                         0)))
        cells (count-dirs "40-engine/kotoba/crates/kotoba-kotodama/cells")
        apps  (count-dirs "60-apps")
        proto (count-dirs "10-protocol")
        infra (count-dirs "50-infra")
        score (volatile! 0)
        ev    (volatile! [])
        add!  (fn [s e] (vswap! score + s) (vswap! ev conj e))]

    (cond
      (>= cells 10) (add! 3 (str cells " kotodama cells (八百万 variation)"))
      (>= cells 5)  (add! 2 (str cells " kotodama cells")))
    (cond
      (>= apps 10) (add! 3 (str apps " apps"))
      (>= apps 1)  (add! 1 (str apps " apps")))
    (when (>= proto 5)
      (add! 2 (str proto " protocol packages")))
    (when (>= infra 15)
      (add! 2 (str infra " infra components")))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "Exercise idle yorishiro_* cells end-to-end"
                        "Add at least one more cell / app / protocol package")]
      (c/->AxisReading "diversity" final-score @ev next-action 1))))
