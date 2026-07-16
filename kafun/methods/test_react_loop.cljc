#!/usr/bin/env bb
;; kafun 花粉 — tests for the system-dynamics ReAct loop (react_loop.cljc).
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_react_loop.cljc
(ns kafun.methods.test-react-loop
  "Tests for react_loop.cljc — SENSE->ORIENT->HYPOTHESIZE->REVIEW->RANK->EVOLVE->ACT->
  OBSERVE->LEARN->PERSIST over the readiness stock-flow. Verifies: leak-free scoring, the
  bottleneck resolving as readiness crosses threshold, G5 (stands never mutated, no actuation
  vocabulary ever emitted), idempotent-by-content persistence, resume-safe chaining."
  (:require [kafun.methods.react-loop :as rl]
            [kafun.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is run-tests]]))

(defn- stand [id ov]
  (merge {:id id :replant true :carbon :net-negative :consent true :protected false
          :sapling-supply :ok :reforest-viability 0.6 :area-ha 10000 :emission-density 0.5
          :exposed-pop-weight 1.0}
         ov))

(def ^:private stands
  [(stand "s1" {:sapling-supply :none})
   (stand "s2" {:sapling-supply :none})
   (stand "c1" {:consent false})])

(def ^:private no-bottleneck-stands [(stand "ok1" {})])

(def ^:private tmp
  (str (System/getProperty "java.io.tmpdir") "/kafun-test-react-loop.kotoba.edn"))
(defn- fresh! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))) tmp)

;; ── pure unit tests: HYPOTHESIZE / REVIEW / RANK / EVOLVE ────────────────────

(deftest candidates-restricted-to-the-binding-constraint
  (is (= [:supply-slow :supply-fast] (mapv :id (rl/candidates-for :await-sapling-supply))))
  (is (= [:consent-slow :consent-fast] (mapv :id (rl/candidates-for :await-consent))))
  (is (= [] (rl/candidates-for nil)) "no binding constraint -> no candidates (monitor-only)"))

(deftest hypothesize-scores-every-candidate-without-mutating-stands
  (let [snapshot-before (mapv #(select-keys % [:id :sapling-supply :consent]) stands)
        hyps (rl/hypothesize {:supply-level 0.0 :consent-level 0.0}
                              stands (rl/candidates-for :await-sapling-supply))]
    (is (= 2 (count hyps)))
    (is (every? #(contains? % :efficiency) hyps))
    (is (= snapshot-before (mapv #(select-keys % [:id :sapling-supply :consent]) stands)) "G5")))

(deftest rank-orders-by-weighted-efficiency-deterministically
  (let [hyps [{:id :a :efficiency 1.0} {:id :b :efficiency 2.0}]
        ranked (rl/rank {} hyps)]
    (is (= [:b :a] (mapv :id ranked)) "higher efficiency ranks first")))

(deftest evolve-recombines-different-bottlenecks-when-it-helps
  (let [ranked [{:id :supply-fast :target :await-sapling-supply :supply-rate 0.34 :consent-rate 0.0 :efficiency 1.0}
                {:id :consent-fast :target :await-consent :supply-rate 0.0 :consent-rate 0.34 :efficiency 0.9}]
        chosen (rl/evolve {:supply-level 0.0 :consent-level 0.0} stands ranked)]
    (is (some? chosen))
    (is (contains? #{:supply-fast :joint-evolved} (:id chosen)))))

;; ── full beat: leak-free scoring + threshold-crossing resolution ─────────────

(deftest beat-0-baseline-has-no-prior-outcome
  (let [path (fresh!)
        r (rl/beat {:stands stands :tx-id "b0" :as-of "t0" :log-path path})]
    (is (:appended r))
    (is (nil? (:outcome-score r)) "no prior forecast exists at beat 0 -- nothing to score")
    (is (some? (:binding-constraint r)) "2 stands await sapling supply -- that is the binding constraint")))

(deftest beat-1-scores-beat-0s-forecast-leak-free
  (let [path (fresh!)]
    (rl/beat {:stands stands :tx-id "b0" :as-of "t0" :log-path path})
    (let [r1 (rl/beat {:stands stands :tx-id "b1" :as-of "t1" :log-path path})]
      (is (some? (:outcome-score r1)) "beat 1 scores beat 0's PRE-REGISTERED forecast")
      (is (<= 0.0 (:outcome-score r1) 1.0)))))

(deftest binding-constraint-clears-once-both-bottlenecks-fully-ready
  (let [path (fresh!)
        n 30
        results (mapv (fn [i] (rl/beat {:stands stands :tx-id (str "b" i) :as-of (str "t" i) :log-path path}))
                      (range n))
        last-r (peek results)]
    (is (>= (:supply-level last-r) 1.0) "readiness saturates well within 30 beats")
    (is (>= (:consent-level last-r) 1.0))
    (is (nil? (:binding-constraint last-r)) "once both bottlenecks are ready, nothing is left stalled")
    (is (= 3 (:cumulative-unblocked last-r)) "all 3 stands eventually reach :reforest-priority")))

(deftest no-bottleneck-stands-produce-a-monitor-only-beat
  (let [path (fresh!)
        r (rl/beat {:stands no-bottleneck-stands :tx-id "b0" :as-of "t0" :log-path path})]
    (is (:appended r))
    (is (nil? (:binding-constraint r)))
    (is (nil? (:chosen r)))
    (is (nil? (:predicted-unblocked r)))))

;; ── G5 + vocabulary hygiene across a real ledger ─────────────────────────────

(deftest stands-are-never-mutated-across-many-beats-g5
  (let [path (fresh!)
        snapshot-before (mapv #(select-keys % [:id :sapling-supply :consent]) stands)]
    (dotimes [i 5] (rl/beat {:stands stands :tx-id (str "b" i) :as-of (str "t" i) :log-path path}))
    (is (= snapshot-before (mapv #(select-keys % [:id :sapling-supply :consent]) stands))
        "kafun supplies no sapling and grants no consent -- the input stands are unchanged")))

(deftest no-actuation-vocabulary-ever-appears-in-the-ledger-g5
  (let [path (fresh!)]
    (dotimes [i 4] (rl/beat {:stands stands :tx-id (str "b" i) :as-of (str "t" i) :log-path path}))
    (let [all-attrs (mapcat (fn [tx] (map (fn [[_ _ a _]] (str a)) (get tx ":tx/datoms")))
                            (k/read-log path))]
      (is (every? #(re-matches #":(kafun\.react|react-beat)[./].*" %) all-attrs)
          "every datom stays in the react-loop's own namespace -- never :kafun/actuate or similar"))))

;; ── ledger integrity ──────────────────────────────────────────────────────────

(deftest verify-chain-holds-across-many-beats
  (let [path (fresh!)]
    (dotimes [i 6] (rl/beat {:stands stands :tx-id (str "b" i) :as-of (str "t" i) :log-path path}))
    (is (:ok (k/verify-chain path)))))

(deftest resume-safe-head-chaining
  (let [path (fresh!)
        r0 (rl/beat {:stands stands :tx-id "b0" :as-of "t0" :log-path path})
        r1 (rl/beat {:stands stands :tx-id "b1" :as-of "t1" :log-path path})]
    (is (= (:head r0) (get (first (k/read-log path)) ":tx/cid")))
    (is (= (:head r1) (k/head-cid path)))))

;; ── persist! idempotency (unit-level, independent of the always-incrementing beat counter) ──

(deftest persist-is-a-noop-for-content-identical-datoms
  (let [path (fresh!)
        ds [[":db/add" "e" ":a" 1]]]
    (rl/persist! ds {:tx-id "t0" :as-of "t0" :log-path path})
    (let [r2 (rl/persist! ds {:tx-id "t1" :as-of "t1" :log-path path})]
      (is (not (:appended r2)))
      (is (= :no-change (:reason r2)))
      (is (= 1 (count (k/read-log path)))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-react-loop)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
