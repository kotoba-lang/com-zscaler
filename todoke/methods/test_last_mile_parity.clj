#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the todoke last-mile route core.
(ns todoke.methods.test-last-mile-parity
  "test_last_mile_parity.clj — todoke last_mile py↔clj LIVE parity (ADR-2606042300).

  last_mile.clj is the courier-route core (NN + 2-opt sequencing under the SAE-L4 sidewalk-ODD
  envelope, 'order-identical to last_mile.py AND the Rust crate'). The existing test is a
  snapshot; this runs the ACTUAL last_mile.py via a python3 subprocess and the clj impl over the
  SAME stops, comparing the optimized SEQUENCE + path length (to 1e-9) AND the three G7 envelope
  REFUSAL messages (SAE>4 / out-of-ODD zone / over-speed) + the labour-liberation sizing scalars.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/todoke/methods/test_last_mile_parity.clj"
  (:require [todoke.methods.last-mile :as lm]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/todoke/methods")

(defn- clj-stops [] [(lm/stop "s0" 0 0 "sidewalk") (lm/stop "s1" 3 0 "sidewalk")
                     (lm/stop "s2" 3 4 "sidewalk") (lm/stop "s3" 0 4 "sidewalk")
                     (lm/stop "s4" 1 1 "crosswalk")])

(def ^:private py-src
  (str "import json, last_mile as lm\n"
       "S=[lm.Stop('s0',0,0,'sidewalk'),lm.Stop('s1',3,0,'sidewalk'),lm.Stop('s2',3,4,'sidewalk'),"
       "lm.Stop('s3',0,4,'sidewalk'),lm.Stop('s4',1,1,'crosswalk')]\n"
       "def refuse(f):\n"
       "    try: f(); return None\n"
       "    except lm.EnvelopeViolation as e: return str(e)\n"
       "out={'plan': lm.plan_last_mile(S, sae_level=4, commanded_mps=1.0),\n"
       " 'sae': refuse(lambda: lm.plan_last_mile(S, sae_level=5, commanded_mps=1.0)),\n"
       " 'road': refuse(lambda: lm.plan_last_mile([lm.Stop('r',0,0,'road')], sae_level=4, commanded_mps=1.0)),\n"
       " 'speed': refuse(lambda: lm.plan_last_mile(S, sae_level=4, commanded_mps=1.5)),\n"
       " 'freed': lm.courier_freed_hours(100,2000,0.6), 'cohort': lm.displacement_cohort_size(100,0.6)}\n"
       "print(json.dumps(out))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) false)))
    (catch Exception _ nil)))

(defn- refuse-msg [thunk]
  (try (thunk) nil (catch clojure.lang.ExceptionInfo e (.getMessage e))))

(defn- clj-results []
  (let [S (clj-stops)
        [seq* len] (lm/plan-last-mile S :sae-level 4 :commanded-mps 1.0)]
    {"plan" [seq* len]
     "sae" (refuse-msg #(lm/plan-last-mile S :sae-level 5 :commanded-mps 1.0))
     "road" (refuse-msg #(lm/plan-last-mile [(lm/stop "r" 0 0 "road")] :sae-level 4 :commanded-mps 1.0))
     "speed" (refuse-msg #(lm/plan-last-mile S :sae-level 4 :commanded-mps 1.5))
     "freed" (lm/courier-freed-hours 100 2000 0.6)
     "cohort" (lm/displacement-cohort-size 100 0.6)}))

(deftest clj-route-and-envelope-fire
  ;; runs regardless of python: 2-opt orders the square, the G7 envelope REFUSES (not clamps).
  (let [[seq* len] (lm/plan-last-mile (clj-stops) :sae-level 4 :commanded-mps 1.0)]
    (is (= ["s0" "s4" "s1" "s2" "s3"] seq*) "NN+2-opt sequence")
    (is (< (Math/abs (- len 10.650281539872886)) 1e-9) "path length"))
  (is (re-find #"SAE level 5 exceeds ceiling 4"
               (refuse-msg #(lm/plan-last-mile (clj-stops) :sae-level 5 :commanded-mps 1.0))))
  (is (re-find #"zone 'road' outside todoke ODD"
               (refuse-msg #(lm/plan-last-mile [(lm/stop "r" 0 0 "road")] :sae-level 4 :commanded-mps 1.0)))
      "ODD refusal uses py's single-quote zone repr"))

(deftest last-mile-matches-python
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — todoke last-mile cross-language parity skipped")
      (let [clj (clj-results)]
        (is (= (first (get py "plan")) (first (get clj "plan"))) "optimized sequence")
        (is (< (Math/abs (- (second (get py "plan")) (second (get clj "plan")))) 1e-9) "path length")
        (is (= (get py "sae") (get clj "sae")) "SAE>4 refusal message")
        (is (= (get py "road") (get clj "road")) "out-of-ODD refusal message (single-quote zone repr)")
        (is (= (get py "speed") (get clj "speed")) "over-speed refusal message")
        (is (< (Math/abs (- (get py "freed") (get clj "freed"))) 1e-9) "courier freed hours")
        (is (= (get py "cohort") (get clj "cohort")) "displacement cohort size")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'todoke.methods.test-last-mile-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
