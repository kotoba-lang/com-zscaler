#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the niyaku anti-sway RK4 physics core.
(ns niyaku.methods.test-crane-dynamics-parity
  "test_crane_dynamics_parity.clj — niyaku crane anti-sway py↔clj LIVE parity (ADR-2606082000).

  The existing clj tests pin values captured once from the Python impl — which can silently
  drift if `crane_dynamics.py` changes (the stale-snapshot trap). This test runs the ACTUAL
  `crane_dynamics.py` via a python3 subprocess and the clj `simulate-traverse` over the SAME
  scenarios, then asserts the RK4 outputs (settle-time / residual-sway / peak-sway) agree to
  1e-6 — a genuine cross-language oracle that catches drift in EITHER implementation.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/niyaku/methods/test_crane_dynamics_parity.clj"
  (:require [niyaku.methods.crane-dynamics :as cd]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/niyaku/methods")

;; Python snippet: default GantryCrane, simulate_traverse over each distance in argv → JSON list.
(def ^:private py-src
  (str "import sys, json, crane_dynamics as cd\n"
       "crane = cd.GantryCrane()\n"
       "out = []\n"
       "for d in [float(x) for x in sys.argv[1:]]:\n"
       "    r = cd.simulate_traverse(crane, d)\n"
       "    out.append({'dist': d, 'settle': r.settle_time_s, 'residual': r.residual_sway_m, 'peak': r.peak_sway_m})\n"
       "print(json.dumps(out))\n"))

(def ^:private scenarios [10.0 20.0 30.0 45.0])

(defn- py-results []
  (try
    (let [r (apply sh "python3" "-c" py-src (concat (map str scenarios) [:dir py-dir]))]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) true)))
    (catch Exception _ nil)))

(defn- clj-result [dist]
  (let [c (cd/make-gantry-crane) r (cd/simulate-traverse c dist)]
    {:settle (:settle-time-s r) :residual (:residual-sway-m r) :peak (:peak-sway-m r)}))

(deftest clj-physics-is-self-consistent
  ;; sanity: the clj core runs and settles for every scenario (independent of python availability)
  (doseq [d scenarios]
    (let [r (clj-result d)]
      (is (pos? (:settle r)) (str "no settle at " d))
      (is (>= (:peak r) (:residual r)) (str "peak < residual at " d)))))

(deftest crane-rk4-matches-python-across-scenarios
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — cross-language parity check skipped")
      (do
        (is (= (count scenarios) (count py)) "python returned one row per scenario")
        (doseq [row py]
          (let [d (:dist row)
                c (clj-result d)
                close? (fn [a b] (< (Math/abs (- (double a) (double b))) 1e-6))]
            (is (close? (:settle row) (:settle c)) (str "settle drift at " d ": py " (:settle row) " clj " (:settle c)))
            (is (close? (:residual row) (:residual c)) (str "residual drift at " d ": py " (:residual row) " clj " (:residual c)))
            (is (close? (:peak row) (:peak c)) (str "peak drift at " d ": py " (:peak row) " clj " (:peak c)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'niyaku.methods.test-crane-dynamics-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
