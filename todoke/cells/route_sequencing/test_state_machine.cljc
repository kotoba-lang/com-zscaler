(ns todoke.cells.route-sequencing.test-state-machine
  "clojure.test port of the route_sequencing assertions from
  `cells/test_state_machines.py` (ADR-2606042300).

  Ports the route_sequencing state-machine cases:
    - test_route_happy_path_sequences_and_emits
    - test_route_g7_refuses_road_zone
    - test_route_g7_refuses_sae_level_5
    - test_route_g7_refuses_speed_over_sidewalk_cap

  The Python file's `test_route_solve_raises_at_r0` is a `cell.py` .solve() concern
  (the R0 RuntimeError scaffold) — that stays in the Python cell.py and is NOT part
  of the state-machine port (mirrors how shionome's .cljc test covers only the
  transitions). The handoff_proof cases are a sibling cell, out of scope here."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [todoke.cells.route-sequencing.state-machine :as sm]))

;; A scrambled set of collinear stops: optimal open path = ascending x.
(def ^:private stops
  [{:id 0 :x 0.0  :y 0.0 :zone "sidewalk"}
   {:id 1 :x 30.0 :y 0.0 :zone "doorpath"}
   {:id 2 :x 10.0 :y 0.0 :zone "sidewalk"}
   {:id 3 :x 20.0 :y 0.0 :zone "doorpath"}
   {:id 4 :x 5.0  :y 0.0 :zone "crosswalk"}])

(defn- run-route
  "Mirror of `_run_route`: job_loaded → envelope_checked → (if ok) sequenced → emitted."
  [& {:keys [stops* sae-level commanded-mps]
      :or {sae-level 4 commanded-mps 1.0}}]
  (let [s (sm/transition-to-job-loaded
           {:stops (or stops* stops) :sae-level sae-level :commanded-mps commanded-mps})
        s (sm/transition-to-envelope-checked s)]
    (if (get-in s [:cell-state :envelope-ok])
      (-> s sm/transition-to-sequenced sm/transition-to-route-emitted)
      s)))

(deftest test-route-happy-path-sequences-and-emits
  (let [s (run-route)
        cs (:cell-state s)
        rec (get-in cs [:payload :last-mile-route])]
    (is (= "route_emitted" (:phase cs)))
    (is (= [0 4 2 3 1] (:order rec)))   ; ascending-x optimal open path
    (is (< (Math/abs (- (:lengthM rec) 30.0)) 1e-6))
    (testing "N2 SAE ceiling surfaced on the record"
      (is (true? (:saeWithinCeiling rec))))))

(deftest test-route-g7-refuses-road-zone
  (let [stops* (conj stops {:id 9 :x 40.0 :y 0.0 :zone "road"})
        s (run-route :stops* stops*)
        cs (:cell-state s)]
    (is (false? (:envelope-ok cs)))
    (is (str/includes? (:refusal cs) "outside todoke ODD"))))

(deftest test-route-g7-refuses-sae-level-5
  (let [s (run-route :sae-level 5)
        cs (:cell-state s)]
    (is (false? (:envelope-ok cs)))
    (is (str/includes? (:refusal cs) "exceeds ceiling"))))

(deftest test-route-g7-refuses-speed-over-sidewalk-cap
  (let [s (run-route :commanded-mps 3.0)  ; > 1.8 sidewalk cap
        cs (:cell-state s)]
    (is (false? (:envelope-ok cs)))
    (is (str/includes? (:refusal cs) "exceeds"))))

;; ── extra parity guards on the ported state machine ──────────────

(deftest test-sequencing-a-refused-job-raises
  (testing "transition_to_sequenced on a refused job is itself a G7 violation"
    (let [refused (run-route :sae-level 5)]
      (is (false? (get-in refused [:cell-state :envelope-ok])))
      (is (thrown? clojure.lang.ExceptionInfo
                   (sm/transition-to-sequenced refused))))))

(deftest test-closed-route-state-surface
  (testing "an unexpected cell-state field raises (RouteState(**...) parity)"
    (is (thrown? clojure.lang.ExceptionInfo
                 (sm/transition-to-job-loaded {:cell-state {:bogus 1}})))))

#?(:clj
   (defn -main [& _]
     (run-tests 'todoke.cells.route-sequencing.test-state-machine)))
