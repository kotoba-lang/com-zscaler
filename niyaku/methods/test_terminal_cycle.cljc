(ns niyaku.methods.test-terminal-cycle
  "Tests for niyaku.methods.terminal-cycle — end-to-end vessel-discharge orchestration.
  1:1 Clojure port of methods/test_terminal_cycle.py (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [niyaku.methods.agv-transfer :as agv]
            [niyaku.methods.crane-dynamics :as cd]
            [niyaku.methods.stow-plan :as sp]
            [niyaku.methods.terminal-cycle :as tc]))

(defn- approx? [a b]
  (<= (Math/abs (- (double a) (double b))) (* 1e-9 (max 1.0 (Math/abs (double b))))))

(defn- boxes
  ([n] (boxes n "JPYOK"))
  ([n port] (for [i (range n)] (sp/make-container (str "B" i) (- 20.0 i) port))))

(deftest test-basic-discharge-runs-all-boxes
  (let [r (tc/simulate-discharge (boxes 6) ["JPYOK"] "JPYOK" 2 2 3)]
    (is (map? r))
    (is (= 6 (:moves r)))
    (is (= 6 (count (:records r))))
    (is (> (double (:discharge-time-s r)) 0))
    (is (and (< 10 (tc/moves-per-hour r)) (< (tc/moves-per-hour r) 200)))
    (is (every? #(seq (:agv-id %)) (:records r)))))

(deftest test-only-target-port-discharged
  (let [bxs (concat (boxes 3 "JPYOK")
                    (for [i (range 3)] (sp/make-container (str "R" i) 15.0 "NLRTM")))
        r (tc/simulate-discharge bxs ["JPYOK" "NLRTM"] "JPYOK" 3 2 3)]
    (is (= 3 (:moves r)))
    (is (every? #(str/starts-with? (:box-id %) "B") (:records r)))))

(deftest test-discharge-is-max-of-crane-and-agv
  (let [r (tc/simulate-discharge (boxes 4) ["JPYOK"] "JPYOK" 2 2 2)]
    (is (approx? (:discharge-time-s r)
                 (max (double (:crane-timeline-s r)) (double (:agv-makespan-s r)))))))

(deftest test-more-agvs-do-not-raise-crane-bound-time
  ;; The single STS crane is the bottleneck.
  (let [r2 (tc/simulate-discharge (boxes 6) ["JPYOK"] "JPYOK" 2 2 3 :agv-ids ["A1" "A2"])
        r5 (tc/simulate-discharge (boxes 6) ["JPYOK"] "JPYOK" 2 2 3
                                  :agv-ids ["A1" "A2" "A3" "A4" "A5"])]
    (is (approx? (:crane-timeline-s r2) (:crane-timeline-s r5)))
    (is (<= (double (:agv-makespan-s r5)) (double (:agv-makespan-s r2))))
    (is (approx? (:discharge-time-s r5) (:crane-timeline-s r5)))))

(deftest test-empty-port-zero-productivity
  (let [r (tc/simulate-discharge (boxes 3 "JPYOK") ["JPYOK" "SGSIN"] "SGSIN" 2 2 2)]
    (is (= 0 (:moves r)))
    (is (= 0.0 (double (:discharge-time-s r))))
    (is (= 0.0 (tc/moves-per-hour r)))))

(deftest test-accepts-prebuilt-plan
  (let [bxs (boxes 4)
        plan (sp/build-stow-plan bxs ["JPYOK"] 2 2 2)
        r (tc/simulate-discharge bxs ["JPYOK"] "JPYOK" 2 2 2 :plan plan)]
    (is (= 4 (:moves r)))))

(deftest test-custom-crane-yard-agv
  (let [r (tc/simulate-discharge
            (boxes 3) ["JPYOK"] "JPYOK" 2 2 2
            :crane (cd/make-gantry-crane :cable-length 20.0)
            :agv (agv/make-agv :v-max 4.0)
            :yard (tc/make-yard-layout :apron-to-yard-m 200.0))]
    (is (= 3 (:moves r)))
    (is (>= (double (:max-residual-sway-m r)) 0.0))))

(deftest test-isaac-path-runs-or-falls-back
  ;; use-isaac true must produce a valid report whether or not the Isaac surface
  ;; is importable (it falls back to the analytic model).
  (let [r (tc/simulate-discharge (boxes 3) ["JPYOK"] "JPYOK" 2 2 2 :use-isaac true)]
    (is (= 3 (:moves r)))
    (is (> (double (:discharge-time-s r)) 0))))
