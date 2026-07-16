(ns mimamori.tests.test-shakai
  "mimamori social-capital bridge tests — moyai-family invariants + keeper-only mint.
  Port of tests/test_shakai.py."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [mimamori.methods.autorun :as autorun]
            [mimamori.methods.bond :as bond]
            [mimamori.methods.shakai :as shakai]
            [moyai.ledger :as ledger]))

(def A "did:web:etzhayyim.com:member:fictional:aleph")
(def B "did:web:etzhayyim.com:member:fictional:bet")

(defn- seed []
  (bond/load-seed-file (io/resource "mimamori/data/seed-mimamori-bonds.json")))

(defn- tmplog []
  (str (java.nio.file.Files/createTempDirectory
        "mimamori-shakai" (make-array java.nio.file.attribute.FileAttribute 0))
       "/log.kotoba.edn"))

(deftest mint-per-consented-act
  ;; seed has 2 heartbeats (aleph→bet, vav→he)
  (let [[log s] (shakai/mint-from-keeping (bond/replay (seed)) [] 1)]
    (is (= 2 (:acts s)))
    (is (= 2 (:minted-units s)))
    (is (= 2 (:keepers-minted s)))
    (is (= 2 (ledger/total-minted log)))))   ;; one unit per content-free keeping act

(deftest mint-to-keeper-never-kept
  (let [eng (-> (bond/new-engine) (bond/offer A B) (bond/consent A B) (bond/heartbeat A B))
        [log _] (shakai/mint-from-keeping eng [] 1)]
    (is (= 1.0 (ledger/balance log A 1)))    ;; the keeper earned
    (is (= 0.0 (ledger/balance log B 1)))    ;; the kept has NO entry — not even zero-units
    (is (every? #(= A (:holder-did %)) log))))

(deftest earn-cap
  (let [eng (reduce (fn [m _] (bond/heartbeat m A B))
                    (-> (bond/new-engine) (bond/offer A B) (bond/consent A B))
                    (range (+ shakai/earn-cap-per-epoch 2)))
        [_ s] (shakai/mint-from-keeping eng [] 1)]
    (is (= shakai/earn-cap-per-epoch (:minted-units s)))
    (is (= 2 (:capped-acts s)))))

(deftest decay-flow-not-store
  (let [log (ledger/mint [] A 4 1 "keep:test")]
    (is (< (abs (- (ledger/balance log A (+ 1 ledger/half-life-epochs)) 2.0)) 1e-9))))

(deftest non-transferable-and-firewalls
  ;; the verb does not exist — there is no transfer/gift/pool var in the ledger ns
  (is (nil? (ns-resolve 'moyai.ledger 'transfer)))
  (is (nil? (ns-resolve 'moyai.ledger 'gift)))
  (is (nil? (ns-resolve 'moyai.ledger 'pool)))
  (is (zero? (ledger/redeemable-usd-micros)))          ;; cash≡0 / BHI firewall
  (is (false? (ledger/grants-governance-weight?)))     ;; 1 SBT = 1 vote untouched
  (is (false? (ledger/grants-benefit-or-stage?))))     ;; 救済 floor unconditional

(deftest ref-opacity-no-kept-did
  (let [[log _] (shakai/mint-from-keeping (bond/replay (seed)) [] 1)]
    (doseq [e log]
      (is (not (str/includes? (:ref e) "did:")))
      (is (str/starts-with? (:ref e) "keep:")))))      ;; provenance, not registry

(deftest datoms-and-autorun-integration
  (let [log-path (tmplog)
        s1 (autorun/run-cycle (seed) log-path)]
    (is (= 2 (get-in s1 [:shakai :minted-units])))
    (let [s2 (autorun/run-cycle (seed) log-path)]
      (is (not= (:cid s2) (:cid s1))))                 ;; prev-linked
    (let [[log _] (shakai/mint-from-keeping (bond/replay (seed)) [] 1)
          ds (shakai/social-capital-datoms log 1)
          attrs (set (map #(nth % 2) ds))]
      (is (contains? attrs ":social.capital/holder"))
      (is (contains? attrs ":social.capital/units"))
      (is (every? #(= ":db/add" (first %)) ds)))))     ;; append-only
