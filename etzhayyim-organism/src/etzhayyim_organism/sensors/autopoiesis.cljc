#!/usr/bin/env bb
;; Axis 1 — Autopoiesis (自己創出 / 無教会 万人祭司).
;; Port of src/etzhayyim_organism/sensors/autopoiesis.py
(ns etzhayyim-organism.sensors.autopoiesis
  "Axis 1 — Autopoiesis: organisation can reproduce itself without hierarchical clergy.
   Observable: CLAUDE.md + bootstrap docs + Council scaffolding + harness scripts."
  (:require [etzhayyim-organism.sensors.common :as c]))

(defn read
  "Compute the autopoiesis AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))]

    (when (c/has? repo "CLAUDE.md")
      (add! 2 "CLAUDE.md present (operator memory)"))
    (when (and (c/has? repo "COUNCIL.md") (c/has? repo "COUNCIL-BOOTSTRAP-RFP.md"))
      (add! 2 "Council scaffolded (5-seat religious evaluation body)"))
    (when (c/has? repo "MEMBERS.md")
      (add! 1 "MEMBERS.md roster present"))
    (when (c/has? repo "FORK-BOOTSTRAP.md")
      (add! 1 "FORK-BOOTSTRAP.md present (八百万 propagation enabled)"))

    (let [loop-scripts (c/count-glob repo "70-tools/scripts/loop/*.sh")]
      (when (>= loop-scripts 1)
        (add! 2 (str loop-scripts " loop script(s) (self-rescoring harness)"))))

    (when (and (c/has? repo "_observations")
               (>= (c/count-glob repo "_observations/*-cycle-*.md") 3))
      (add! 2 "≥3 persisted cycles (autopoiesis demonstrated longitudinally)"))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "Confirm Council Seats 2-5 by 2026-06-19 (RFP close)"
                        "Verify CLAUDE.md + Council + observations scaffold integrity")]
      (c/->AxisReading "autopoiesis" final-score @ev next-action 2))))
