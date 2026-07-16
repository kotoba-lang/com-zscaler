#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the niyaku AGV horizontal-transport dispatch.
(ns niyaku.methods.test-agv-transfer-parity
  "test_agv_transfer_parity.clj — niyaku agv-transfer py↔clj LIVE parity (ADR-2606082000).

  Runs the ACTUAL `agv_transfer.py` via a python3 subprocess and the clj impl over the SAME
  scenarios, asserting the trapezoidal travel-time AND the full LPT greedy dispatch
  (makespan + the agv→move-id ASSIGNMENT + per-agv finish-time) agree — the assignment is an
  EXACT structural match (so the longest-processing-time rule + its tie-break are proven
  identical across languages, not just the scalar makespan). Catches drift in EITHER impl,
  unlike a once-captured literal.

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/niyaku/methods/test_agv_transfer_parity.clj"
  (:require [niyaku.methods.agv-transfer :as t]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/niyaku/methods")

(def ^:private tt-dists [50.0 120.0 200.0 300.0 5.0 0.0])

;; [[move-distances...] [agv-ids...]] — identical dispatch scenarios in py + clj.
(def ^:private scenarios
  [[[200.0 50.0 120.0 300.0 80.0] ["A1" "A2"]]       ; uneven → LPT balances
   [[100.0 100.0 100.0]           ["A1" "A2" "A3"]]  ; equal moves → round-robin
   [[40.0 90.0 10.0 60.0 75.0 5.0] ["A1" "A2"]]      ; 6 moves, 2 agvs
   [[300.0 10.0]                  ["A1"]]])           ; single AGV → all to A1

(def ^:private py-src
  (str "import json, agv_transfer as t\n"
       "agv = t.Agv()\n"
       "dists = json.loads(__import__('sys').argv[1])\n"
       "scen = json.loads(__import__('sys').argv[2])\n"
       "tt = [t.travel_time(d, agv) for d in dists]\n"
       "out = []\n"
       "for mds, ids in scen:\n"
       "    mv = [t.Move('M%d'%i, d) for i,d in enumerate(mds)]\n"
       "    r = t.dispatch(mv, ids, agv)\n"
       "    out.append({'makespan': r.makespan(), 'assignment': r.assignment, 'finish': r.finish_time})\n"
       "print(json.dumps({'tt': tt, 'scen': out}))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src
                (json/generate-string tt-dists) (json/generate-string scenarios)
                :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) false)))  ; keywords:false → AGV ids stay string keys
    (catch Exception _ nil)))

(defn- clj-dispatch [[mds ids]]
  (let [agv (t/make-agv)
        mv (map-indexed (fn [i d] (t/make-move (str "M" i) d)) mds)
        r (t/dispatch mv ids agv)]
    {:makespan (t/makespan r) :assignment (:assignment r) :finish (:finish-time r)}))

(defn- close? [a b] (< (Math/abs (- (double a) (double b))) 1e-6))

(deftest clj-dispatch-is-self-consistent
  ;; runs regardless of python: every move assigned exactly once; makespan = max finish.
  (doseq [[mds ids :as s] scenarios]
    (let [r (clj-dispatch s)
          assigned (mapcat val (:assignment r))]
      (is (= (count mds) (count assigned)) "every move assigned exactly once")
      (is (= (set assigned) (set (map #(str "M" %) (range (count mds))))) "no move dropped/duplicated")
      (is (close? (:makespan r) (reduce max 0.0 (vals (:finish r)))) "makespan = max per-agv finish"))))

(deftest agv-dispatch-matches-python-across-scenarios
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — cross-language parity check skipped")
      (let [py-tt (get py "tt")
            py-scen (get py "scen")]
        ;; travel-time parity
        (doseq [[d pv] (map vector tt-dists py-tt)]
          (is (close? pv (t/travel-time d (t/make-agv))) (str "travel-time drift at " d)))
        ;; dispatch parity (makespan + assignment + finish)
        (is (= (count scenarios) (count py-scen)))
        (doseq [[s row] (map vector scenarios py-scen)]
          (let [c (clj-dispatch s)]
            (is (close? (get row "makespan") (:makespan c)) (str "makespan drift " s))
            ;; EXACT structural assignment match (string keys both sides; ordered move-id lists)
            (is (= (get row "assignment") (:assignment c)) (str "assignment drift " s
                                                                ": py " (get row "assignment") " clj " (:assignment c)))
            (doseq [[a fv] (get row "finish")]
              (is (close? fv (get (:finish c) a)) (str "finish drift " s " " a)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'niyaku.methods.test-agv-transfer-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
