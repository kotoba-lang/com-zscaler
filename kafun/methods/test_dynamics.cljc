#!/usr/bin/env bb
;; kafun 花粉 — tests for the remediation-readiness system-dynamics stock-flow model.
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_dynamics.cljc
(ns kafun.methods.test-dynamics
  "Tests for dynamics.cljc — the readiness stock-flow. Verifies: purity/determinism, threshold
  crossing re-scores through the UNCHANGED gate (never a duplicate/relaxed gate), hard refusals
  hold through the forecast exactly as they hold live (G1/G4), G5 (stands never mutated)."
  (:require [kafun.methods.remediate :as rem]
            [kafun.methods.dynamics :as dyn]
            [clojure.test :refer [deftest is run-tests]]))

(defn- stand [id ov]
  (merge {:id id :replant true :carbon :net-negative :consent true :protected false
          :sapling-supply :ok :reforest-viability 0.6 :area-ha 10000 :emission-density 0.5
          :exposed-pop-weight 1.0}
         ov))

(def ^:private awaiting-supply (stand "s1" {:sapling-supply :none}))
(def ^:private awaiting-consent (stand "c1" {:consent false}))
(def ^:private refused-clearcut (stand "x1" {:replant false :sapling-supply :none}))
(def ^:private refused-carbon (stand "x2" {:carbon :net-positive :sapling-supply :none}))
(def ^:private already-ok (stand "ok1" {}))

(def ^:private stands
  [awaiting-supply awaiting-consent refused-clearcut refused-carbon already-ok])

(deftest step-system-is-pure-and-deterministic
  (let [stock {:supply-level 0.2 :consent-level 0.3 :cumulative-unblocked 1}
        inputs {:supply-rate 0.1 :consent-rate 0.05}
        r1 (dyn/step-system stock inputs stands)
        r2 (dyn/step-system stock inputs stands)]
    (is (= r1 r2) "same stock+inputs+stands -> byte-identical next stock")
    (is (= (double (+ 0.2 0.1)) (:supply-level r1)))
    (is (= (double (+ 0.3 0.05)) (:consent-level r1)))))

(deftest supply-readiness-below-threshold-does-not-unblock
  (let [r (dyn/step-system {:supply-level 0.0 :consent-level 0.0} {:supply-rate 0.5} stands)]
    (is (< (:supply-level r) dyn/ready-threshold) "0.5 < 1.0 threshold")
    (is (= 1 (:cumulative-unblocked r)) "only the already-:ok stand counts (awaiting-supply not yet ready)")))

(deftest supply-readiness-crossing-threshold-unblocks-through-the-SAME-gate
  (let [r (dyn/step-system {:supply-level 0.0 :consent-level 0.0} {:supply-rate 1.0} stands)]
    (is (>= (:supply-level r) dyn/ready-threshold))
    (is (= 2 (:cumulative-unblocked r))
        "awaiting-supply now reaches :reforest-priority once readiness crosses 1.0 -- via rem/verdict, no duplicated gate")))

(deftest consent-readiness-crossing-threshold-unblocks
  (let [r (dyn/step-system {:supply-level 0.0 :consent-level 0.0} {:consent-rate 1.0} stands)]
    (is (= 2 (:cumulative-unblocked r)) "awaiting-consent now reaches :reforest-priority")))

(deftest hard-refusals-hold-through-the-forecast-g1-g4
  (let [maxed (dyn/step-system {:supply-level 0.0 :consent-level 0.0}
                                {:supply-rate 1.0 :consent-rate 1.0} stands)
        snap (dyn/readiness-snapshot {:supply-level 1.0 :consent-level 1.0} stands)]
    (is (= 3 (:cumulative-unblocked maxed))
        "both bottlenecks maxed: awaiting-supply + awaiting-consent + already-ok unblock; the 2 refused stands never do")
    (is (= :refuse (:verdict (rem/verdict (first (filter #(= "x1" (:id %)) snap)))))
        "replant=false stays refused even at full readiness (G1)")
    (is (= :refuse (:verdict (rem/verdict (first (filter #(= "x2" (:id %)) snap)))))
        "carbon net-positive stays refused even at full readiness (G4)")))

(deftest readiness-snapshot-never-mutates-the-fixed-stands-g5
  (let [snapshot-before (mapv #(select-keys % [:id :sapling-supply :consent]) stands)]
    (dyn/readiness-snapshot {:supply-level 1.0 :consent-level 1.0} stands)
    (is (= snapshot-before (mapv #(select-keys % [:id :sapling-supply :consent]) stands))
        "kafun supplies no sapling and grants no consent -- the input stands are unchanged (G5)")))

(deftest simulate-produces-the-full-trajectory
  (let [inputs [{:supply-rate 0.5} {:supply-rate 0.5} {:supply-rate 0.5}]
        traj (dyn/simulate {:supply-level 0.0 :consent-level 0.0} inputs stands)]
    (is (= 4 (count traj)) "initial + 3 steps")
    (is (>= (:supply-level (peek traj)) dyn/ready-threshold))))

(deftest counterfactual-shows-the-intervention-lift
  (let [baseline (repeat 4 {:supply-rate 0.0})
        intervention (repeat 4 {:supply-rate 0.34})
        {:keys [delta]} (dyn/counterfactual {:supply-level 0.0 :consent-level 0.0}
                                             baseline intervention stands)]
    (is (>= (:cumulative-unblocked delta) 1)
        "the intervention unblocks at least the awaiting-supply stand that the baseline never does")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-dynamics)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
