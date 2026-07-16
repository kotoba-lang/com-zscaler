#!/usr/bin/env bb
;; Axis 4 — Active Inference (能動推論 / 縁起).
;; Port of src/etzhayyim_organism/sensors/active_inference.py
(ns etzhayyim-organism.sensors.active-inference
  "Axis 4 — Active Inference: persisted observations grow, trajectory-stats works,
   stall detection emits ADRs.  Observable: _observations/*-cycle-NN.md count + monotonicity."
  (:require [etzhayyim-organism.sensors.common :as c]
            [clojure.string :as str]))

(def ^:private cycle-pattern
  "Regex matching -cycle-<digits>.md at end of filename."
  (re-pattern "-cycle-(\\d+)\\.md$"))

(defn read
  "Compute the active-inference AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))
        ;; Enumerate _observations/*-cycle-*.md
        cycles   (let [base (java.io.File. (str repo) "_observations")]
                   (if (.isDirectory base)
                     (->> (.listFiles base)
                          (filter #(re-find cycle-pattern (.getName %)))
                          (sort-by #(.getName %)))
                     []))
        n        (count cycles)]

    (when (>= n 1)
      (add! 2 (str n " observation cycle(s) persisted")))
    (when (>= n 5)
      (add! 2 "≥5 cycles — short-run trajectory established"))
    (when (>= n 15)
      (add! 2 "≥15 cycles — long-run trajectory"))

    ;; Monotone numbering check (no gaps)?
    (let [nums (keep #(when-let [m (re-find cycle-pattern (.getName %))]
                        (Long/parseLong (second m)))
                     cycles)
          gaps (when (seq nums)
                 (->> (partition 2 1 nums)
                      (filter (fn [[a b]] (not= (- b a) 1)))
                      count))]
      (if (and (seq nums) (zero? (or gaps 0)))
        (add! 2 (str "Cycle numbering monotone (cycles 1.." (last nums) ")"))
        (when (pos? (or gaps 0))
          (vswap! ev conj (str "Cycle numbering has " gaps " gap(s)")))))

    (when (c/has? repo "70-tools/scripts/loop/trajectory-stats.sh")
      (add! 2 "trajectory-stats.sh harness live"))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "Emit ADR template when 3× Δ=0 (stall detection)"
                        "Persist next cycle observation")]
      (c/->AxisReading "active_inference" final-score @ev next-action 1))))
