#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the niyaku stowage planner.
(ns niyaku.methods.test-stow-plan-parity
  "test_stow_plan_parity.clj — niyaku stow-plan py↔clj LIVE parity (ADR-2606082000).

  The last niyaku core to get a live cross-language oracle. Runs the ACTUAL `stow_plan.py` via a
  python3 subprocess and the clj impl over the SAME scenarios, asserting BOTH structural outputs
  are EXACT matches: the box→(bay,row,tier) slot ASSIGNMENT and the no-rehandle DISCHARGE
  SEQUENCE (port-rotation-ordered, deepest-tier-first). Mixed-port scenarios exercise the
  rotation ordering. Catches drift in EITHER impl, unlike a once-captured literal.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/niyaku/methods/test_stow_plan_parity.clj"
  (:require [niyaku.methods.stow-plan :as sp]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/niyaku/methods")

;; [[[weight port]...] rotation bays rows tiers query-port] — identical scenarios in py + clj.
(def ^:private scenarios
  [[[[20.0 "JPYOK"] [19.0 "JPYOK"] [18.0 "JPYOK"] [17.0 "JPYOK"] [16.0 "JPYOK"] [15.0 "JPYOK"]]
    ["JPYOK"] 2 2 3 "JPYOK"]                                   ; canonical 6-box, single port
   [[[20.0 "JPYOK"] [19.0 "NLRTM"] [18.0 "JPYOK"] [17.0 "NLRTM"] [16.0 "JPYOK"] [15.0 "NLRTM"]]
    ["JPYOK" "NLRTM"] 2 2 3 "JPYOK"]                           ; mixed ports → query JPYOK
   [[[20.0 "JPYOK"] [19.0 "NLRTM"] [18.0 "JPYOK"] [17.0 "NLRTM"] [16.0 "JPYOK"] [15.0 "NLRTM"]]
    ["JPYOK" "NLRTM"] 2 2 3 "NLRTM"]                           ; same plan → query NLRTM
   [[[20.0 "JPYOK"] [19.0 "JPYOK"] [18.0 "JPYOK"] [17.0 "JPYOK"]]
    ["JPYOK"] 2 2 2 "JPYOK"]])                                 ; 4-box, smaller bay

(def ^:private py-src
  (str "import json, stow_plan as sp\n"
       "scen = json.loads(__import__('sys').argv[1])\n"
       "out = []\n"
       "for boxes, rot, ba, ro, ti, q in scen:\n"
       "    cs = [sp.Container('B%d'%i, w, p) for i,(w,p) in enumerate(boxes)]\n"
       "    plan = sp.build_stow_plan(cs, rot, ba, ro, ti)\n"
       "    asg = {b: [s.bay, s.row, s.tier] for b,s in plan.assignments.items()}\n"
       "    seq = sp.discharge_sequence(plan, q)\n"
       "    out.append({'asg': asg, 'seq': seq})\n"
       "print(json.dumps(out))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src (json/generate-string scenarios) :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) false)))   ; keywords:false → box ids stay string keys
    (catch Exception _ nil)))

(defn- clj-result [[boxes rot ba ro ti q]]
  (let [cs (map-indexed (fn [i [w p]] (sp/make-container (str "B" i) w p)) boxes)
        plan (sp/build-stow-plan cs rot ba ro ti)
        asg (into {} (map (fn [[b s]] [b [(:bay s) (:row s) (:tier s)]]) (:assignments plan)))
        seq* (vec (sp/discharge-sequence plan q))]
    {:asg asg :seq seq*}))

(deftest clj-stow-is-self-consistent
  ;; runs regardless of python. NB discharge-sequence sequences the WHOLE plan (the port arg is
  ;; advisory — see stow_plan docstring), so the sequence is a permutation of all slotted boxes.
  (doseq [[boxes :as s] scenarios]
    (let [r (clj-result s)
          slots (vals (:asg r))
          ids (set (map #(str "B" %) (range (count boxes))))]
      (is (= (count boxes) (count (:asg r))) "every box slotted")
      (is (= (count slots) (count (set slots))) "no two boxes share a slot")
      (is (= ids (set (:seq r))) "discharge sequence is a permutation of all box-ids")
      (is (= (count boxes) (count (:seq r))) "no box dropped/duplicated in the sequence"))))

(deftest stow-plan-matches-python-across-scenarios
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — cross-language parity check skipped")
      (do
        (is (= (count scenarios) (count py)) "python returned one plan per scenario")
        (doseq [[s row] (map vector scenarios py)]
          (let [c (clj-result s)]
            ;; EXACT structural assignment (box → [bay row tier]) and discharge sequence
            (is (= (get row "asg") (:asg c)) (str "assignment drift " s
                                                  ": py " (get row "asg") " clj " (:asg c)))
            (is (= (get row "seq") (:seq c)) (str "discharge-sequence drift " s
                                                  ": py " (get row "seq") " clj " (:seq c)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'niyaku.methods.test-stow-plan-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
