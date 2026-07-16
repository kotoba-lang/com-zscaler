#!/usr/bin/env bb
;; Axis 8 — Wellbecoming (動的軌跡 / 子・孫 priority).
;; Port of src/etzhayyim_organism/sensors/wellbecoming.py
(ns etzhayyim-organism.sensors.wellbecoming
  "Axis 8 — Wellbecoming: dynamic trajectory across generations.
   Observable: MGI compute script, LANDS.md (inalienable inheritance),
   MEMBERS.md (multi-generation roster)."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the wellbecoming AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))]

    (when (c/has? repo "LANDS.md")
      (add! 2 "LANDS.md present (inalienable inheritance roster)"))
    (when (c/has? repo "MEMBERS.md")
      (add! 2 "MEMBERS.md present (multi-generation member roster)"))

    ;; MGI artefacts — count files matching *mgi* or *MGI* anywhere in the tree
    (let [mgi-scripts (+ (c/count-glob repo "**/*mgi*")
                         (c/count-glob repo "**/*MGI*"))]
      (when (>= mgi-scripts 1)
        (add! 2 (str "MGI artefact(s) present (" mgi-scripts ")"))))

    (when (c/has? repo "_observations/mgi")
      (add! 2 "_observations/mgi/ tracking directory present"))

    ;; Multi-generation references in CLAUDE.md
    ;; (the Python code has an erroneous `.is_file()` path check on _observations/ — port faithfully
    ;; matches the intent, which is to look at CLAUDE.md for multi-gen tokens)
    (let [txt (c/read-text repo "CLAUDE.md")]
      (when (or (clojure.string/includes? txt "子・孫")
                (clojure.string/includes? (clojure.string/lower-case txt) "multi-generation"))
        (add! 2 "CLAUDE.md affirms multi-generational priority")))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "First operative MGI report 2027-02-09"
                        "Author MGI compute script")]
      (c/->AxisReading "wellbecoming" final-score @ev next-action 2))))
