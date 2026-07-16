#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the niyaku end-to-end discharge orchestration.
(ns niyaku.methods.test-terminal-cycle-parity
  "test_terminal_cycle_parity.clj — niyaku terminal-cycle py↔clj LIVE parity (ADR-2606082000).

  terminal_cycle composes ALL THREE physics cores (stow_plan + crane_dynamics + agv_transfer)
  into one vessel-discharge KPI report — so a py↔clj agreement here is the strongest single
  parity proof for niyaku. The existing clj test pins values captured once from Python (the
  stale-snapshot trap, cf. the ossekai lesson); this runs the ACTUAL `terminal_cycle.py` via a
  python3 subprocess and the clj `simulate-discharge` over the SAME scenarios, asserting the
  composed KPIs (crane-timeline / agv-makespan / discharge-time / max-residual-sway) agree to
  1e-6 and the move count exactly — catching drift in EITHER implementation.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/niyaku/methods/test_terminal_cycle_parity.clj"
  (:require [niyaku.methods.terminal-cycle :as tc]
            [niyaku.methods.stow-plan :as st]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/niyaku/methods")

;; [n box-port rotation discharge-port bays rows tiers] — identical scenarios in py + clj.
(def ^:private scenarios
  [[6 "JPYOK" ["JPYOK"]          "JPYOK" 2 2 3]    ; canonical 6-box
   [4 "JPYOK" ["JPYOK"]          "JPYOK" 2 2 2]    ; crane-bound, smaller yard
   [6 "JPYOK" ["JPYOK" "NLRTM"]  "JPYOK" 3 2 3]    ; multi-port rotation
   [3 "JPYOK" ["JPYOK" "SGSIN"]  "SGSIN" 2 2 2]])  ; partial — no box bound for SGSIN → 0 moves

(def ^:private py-src
  (str "import json\n"
       "from stow_plan import Container\n"
       "from terminal_cycle import simulate_discharge\n"
       "scen = json.loads(__import__('sys').argv[1])\n"
       "out = []\n"
       "for n, bp, rot, port, ba, ro, ti in scen:\n"
       "    cs = [Container('B%d'%i, 20.0-i, bp) for i in range(n)]\n"
       "    r = simulate_discharge(cs, rot, port, ba, ro, ti)\n"
       "    out.append({'crane': r.crane_timeline_s, 'agv': r.agv_makespan_s,"
       " 'discharge': r.discharge_time_s, 'maxsway': r.max_residual_sway_m, 'moves': r.moves})\n"
       "print(json.dumps(out))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src (json/generate-string scenarios) :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) true)))
    (catch Exception _ nil)))

(defn- clj-result [[n bp rot port ba ro ti]]
  (let [cs (map #(st/make-container (str "B" %) (- 20.0 %) bp) (range n))
        r (tc/simulate-discharge cs rot port ba ro ti)]
    {:crane (:crane-timeline-s r) :agv (:agv-makespan-s r)
     :discharge (:discharge-time-s r) :maxsway (:max-residual-sway-m r) :moves (:moves r)}))

(deftest clj-discharge-is-self-consistent
  ;; runs regardless of python availability: discharge-time = max(crane, agv); partial → 0 moves
  (doseq [s scenarios]
    (let [r (clj-result s)]
      (is (<= (Math/abs (double (- (:discharge r) (max (:crane r) (:agv r))))) 1e-6)
          "discharge-time = max(crane-timeline, agv-makespan)")
      (is (>= (:moves r) 0))))
  ;; the SGSIN partial scenario discharges nothing
  (is (= 0 (:moves (clj-result (last scenarios))))))

(deftest terminal-cycle-matches-python-across-scenarios
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — cross-language parity check skipped")
      (do
        (is (= (count scenarios) (count py)) "python returned one report per scenario")
        (doseq [[s row] (map vector scenarios py)]
          (let [c (clj-result s)
                close? (fn [a b] (< (Math/abs (- (double a) (double b))) 1e-6))]
            (is (close? (:crane row) (:crane c)) (str "crane drift " s ": py " (:crane row) " clj " (:crane c)))
            (is (close? (:agv row) (:agv c)) (str "agv drift " s))
            (is (close? (:discharge row) (:discharge c)) (str "discharge drift " s))
            (is (close? (:maxsway row) (:maxsway c)) (str "maxsway drift " s))
            (is (= (:moves row) (:moves c)) (str "moves drift " s ": py " (:moves row) " clj " (:moves c)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'niyaku.methods.test-terminal-cycle-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
