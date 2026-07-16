(ns fuchi.methods.test-analyze
  "End-to-end tests for 扶持 (fuchi) analyze.cljc over the :representative seed.
  1:1 port of methods/test_analyze.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [fuchi.methods.edn :as edn]
            [fuchi.methods.analyze :as analyze]))

(def seed-path
  (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
      (clojure.java.io/file "data" "seed-sustenance-graph.kotoba.edn") str))

(defn- run* [] (analyze/run (edn/load-edn seed-path)))

(defn- rows-by-did []
  (into {} (map (fn [r] [(last (str/split (get r "did") #":")) r]) (:rows (run*)))))

(deftest test-abel-auto-accepts
  (let [r (get (rows-by-did) "abel")]
    (is (and (= (get r "route") ":auto") (= (get r "outcome") "accepted")))))

(deftest test-seth-goes-to-sbt-vote-and-passes
  (let [r (get (rows-by-did) "seth")]
    (is (and (str/starts-with? (get r "route") ":sbt-vote") (= (get r "outcome") "accepted")))))

(deftest test-eve-escalates-to-council-lv7
  (let [r (get (rows-by-did) "eve")]
    (is (and (= (get r "route") ":council-lv7") (= (get r "outcome") "pending")))))

(deftest test-cain-is-refused-by-rider
  (let [r (get (rows-by-did) "cain")]
    (is (and (= (get r "route") ":refused") (= (get r "outcome") "refused")))))

(deftest test-noah-outreach-zero-share
  (let [r (get (rows-by-did) "noah")]
    (is (and (= (get r "covenant") "outreach") (= (get r "share") 0.0)))))

(deftest test-seth-in-kind-coverage-below-one
  (is (< (get (get (rows-by-did) "seth") "in_kind") 1.0)))

(deftest test-every-derived-alloc-has-zero-cash
  (doseq [d (:derived (run*))]
    (is (= (get d ":alloc/cash-usd-micros") 0))
    (is (false? (get d ":alloc/server-held-key")))))

(deftest test-refused-alloc-not-in-derived
  (let [dids (set (map #(last (str/split (get % ":alloc/maintainer") #":")) (:derived (run*))))]
    (is (not (contains? dids "cain")))))

;; ── R1 a/b/c end-to-end ─────────────────────────────────────────────────────
(deftest test-seth-vote-finalized-after-timelock
  (is (str/includes? (get (get (rows-by-did) "seth") "route") "✓")))

(deftest test-provisioning-intents-emitted
  (let [res (run*)]
    (is (seq (:intents res)) "expected provisioning intents (R1a)")
    (doseq [i (:intents res)]
      (is (and (false? (:published i)) (= (:cash-usd-micros i) 0) (false? (:server-held-key i)))))))

(deftest test-seth-liquidity-intent-is-member-principal
  (let [res (run*)
        liq (filter #(and (str/ends-with? (:alloc-id %) "seth")
                          (= (:rail-kind %) "liquidity-warifu")) (:intents res))]
    (is (and (seq liq) (true? (:member-principal (first liq)))))))

(deftest test-toritate-ledger-is-cashless-no-liquidity
  (let [res (run*)]
    (is (seq (:ledger res)) "expected toritate ledger entries (R1c)")
    (doseq [e (:ledger res)] (is (= (:cash-usd-micros e) 0)))
    (let [cats (set (map :category (:ledger res)))]
      (is (clojure.set/subset? cats #{"subsistence-flow" "vocation-flow" "care-flow" "liberation-flow" "grant"})))))

(deftest test-flow-graph-has-publicfund-source
  (is (some #(= (:flow-class %) "publicfund-to-fuchi") (:flows (run*)))))

;; ── R1(d) coupling ──────────────────────────────────────────────────────────
(defn- coupling-by-cohort []
  (into {} (map (fn [c] [(:cohort-id (get c "earmark")) c]) (:coupling (run*)))))

(deftest test-sanae-cohort-funded-and-admissible
  (let [c (get (coupling-by-cohort) "cohort-sanae-2026")]
    (is (true? (:funded (get c "earmark"))))
    (is (true? (get (get c "gate") "admissible")))
    (is (= (:tithe-usd-micros (get c "earmark")) 6000000000))
    (is (= (:earmark-usd-micros-yr (get c "earmark")) 54000000000))))

(deftest test-hataori-cohort-unfunded-refused
  (let [c (get (coupling-by-cohort) "cohort-hataori-2026")]
    (is (false? (:funded (get c "earmark"))))
    (is (false? (get (get c "gate") "admissible")))
    (is (str/includes? (get (get c "gate") "reason") "G2"))))
