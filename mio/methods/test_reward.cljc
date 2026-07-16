#!/usr/bin/env bb
;; 澪 mio — reward-proposal emitter tests (the economic invariants of PoUF).
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_reward.cljc
(ns mio.methods.test-reward
  (:require [mio.methods.mio-edn :as me]
            [mio.methods.analyze :as a]
            [mio.methods.reward :as r]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/mio/kotoba/seed.edn")
(defn- analysis [] (a/analyze (me/claims seed-path)))
(defn- proposals [] (r/proposals (analysis)))

;; ── G1: reward only for verified claims ──────────────────────────────────────

(deftest only-verified-claims-earn
  (let [a (analysis)
        verified-ids (set (->> (get a "claims")
                               (filter #(= :verified (get % "verdict")))
                               (map #(get % "id"))))
        proposal-ids (set (map :proposal/claim-id (proposals)))]
    (is (= verified-ids proposal-ids) "every proposal maps to a verified claim, and vice-versa")
    (is (= 9 (count (proposals))) "nine verified claims in the mio seed earn")))

;; ── G2: moyai reciprocity credit, NEVER cash ─────────────────────────────────

(deftest reward-is-moyai-not-cash
  (doseq [p (proposals)]
    (is (= :moyai-reciprocity-credit (:proposal/reward-kind p))))
  (let [edn (r/render-datoms (proposals))]
    (is (str/includes? edn "moyai"))
    (is (not (str/includes? edn ":cash")))
    (is (not (str/includes? edn ":usd")))
    (is (not (str/includes? edn ":money")))
    (is (not (str/includes? edn ":equity")))
    (is (not (str/includes? edn "binds-fund\" true")))))

(deftest moyai-credit-equals-verified-flowrate
  (let [a (analysis)
        flowrate (get-in a ["totals" "verified_flowrate_score"])
        total (:total-moyai-credit (r/totals (proposals)))]
    (is (< (Math/abs (- flowrate total)) 1e-6)
        "total proposed moyai credit = the org Flowrate (1:1 with verified useful-flow)")))

;; ── G7: advisory / drafted-unsent / no-server-key ────────────────────────────

(deftest every-proposal-is-advisory-and-unsent
  (doseq [p (proposals)]
    (is (true? (:proposal/advisory p)) "advisory")
    (is (false? (:proposal/binds-fund p)) "binds no fund")
    (is (true? (:proposal/drafted-unsent p)) "drafted-unsent")
    (is (str/includes? (:proposal/decision p) "1 SBT = 1 vote") "disposed by governance")))

;; ── transparent per-actor allocation ─────────────────────────────────────────

(deftest by-actor-allocation-sums-to-total
  (let [ps (proposals)
        ba (r/by-actor ps)
        sum (reduce + 0.0 (vals ba))]
    (is (< (Math/abs (- sum (:total-moyai-credit (r/totals ps)))) 1e-6)
        "per-actor allocation sums to the total")))

(deftest report-states-the-governance-disposition
  (let [md (r/render-report (analysis))]
    (is (str/includes? md "REWARD PROPOSAL"))
    (is (str/includes? md "1 SBT = 1 vote"))
    (is (str/includes? md "moyai"))
    (is (str/includes? md "cash≡0"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-reward)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
