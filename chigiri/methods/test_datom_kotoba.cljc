#!/usr/bin/env bb
;; chigiri 契 — datom-emit + referral-registry-ledger persistence/heartbeat tests.
;; Run:  bb --classpath 20-actors 20-actors/chigiri/methods/test_datom_kotoba.cljc
(ns chigiri.methods.test-datom-kotoba
  (:require [chigiri.methods.registry :as reg]
            [chigiri.methods.datom-emit :as de]
            [chigiri.methods.kotoba :as k]
            [chigiri.methods.autorun :as auto]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/chigiri-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "referral:jp-houterasu-legal-aid" ":referral/jurisdiction" "jpn")
              (k/add "referral:jp-houterasu-legal-aid" ":referral/confidence" "high")])
(defn- d2 [] [(k/add "referral:us-lsc" ":referral/jurisdiction" "usa")
              (k/add "referral:us-lsc" ":referral/verification-status" ":unverified-seed")])

;; ── registry loader over the REAL committed seed ─────────────────────────────

(deftest registry-loads-real-seed
  (let [rs (reg/load-referrals)]
    (is (= 71 (count rs)) "all 71 seed referrals load")
    (is (every? #(contains? % ":referral/id") rs) "every entry has an id")
    (is (every? #(contains? % ":referral/jurisdiction") rs) "every entry has a jurisdiction")
    (is (>= (count (set (map #(get % ":referral/jurisdiction") rs))) 52)
        "≥52 distinct jurisdictions (matches COVERAGE.md)")))

(deftest registry-preserves-unverified-seed
  ;; G14: the loader NEVER upgrades verification — every seed entry stays :unverified-seed
  (let [rs (reg/load-referrals)]
    (is (every? #(= "unverified-seed" (get % ":referral/verification-status")) rs)
        "all seed entries unverified (loader preserves status verbatim)")))

;; ── datom_emit (EAVT projection) ─────────────────────────────────────────────

(deftest emit-produces-eavt-add-datoms
  (let [rs (reg/load-referrals)
        text (de/emit rs 1)]
    (is (str/includes? text "[\"referral:jp-houterasu-legal-aid\" :referral/jurisdiction"))
    (is (str/includes? text ":add]") "datoms are :add ops")
    (is (str/includes? text (str "referrals=" (count rs))) "footer counts referrals")))

(deftest emit-is-deterministic
  (let [rs (reg/load-referrals)]
    (is (= (de/emit rs 1) (de/emit rs 1)) "emission is deterministic (registry order stable)")))

;; ── ledger machinery ─────────────────────────────────────────────────────────

(deftest tx-cid-deterministic-and-content-sensitive
  (is (= (k/tx-cid (d1) "") (k/tx-cid (d1) "")) "same input → same cid")
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d2) "")) "different datoms → different cid")
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d1) "bdeadbeef")) "different prev → different cid")
  (is (str/starts-with? (k/tx-cid (d1) "") "b")))

(deftest append-read-roundtrip
  (let [p (tmp)]
    (try
      (let [tx (k/make-tx (d1) "t1" "as1" "")
            cid (k/append-tx tx p)]
        (is (= cid (get tx ":tx/cid")))
        (let [txs (k/read-log p)]
          (is (= 1 (count txs)))
          (is (= (d1) (get (first txs) ":tx/datoms")) "datoms round-trip byte-faithfully")))
      (finally (io/delete-file p true)))))

(deftest chaining-and-verify
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)
            c2 (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)]
        (is (not= c1 c2))
        (is (= c2 (k/head-cid p)) "head = last tx cid")
        (let [v (k/verify-chain p)]
          (is (:ok v)) (is (= 2 (:length v))) (is (= -1 (:broken-at v)))))
      (finally (io/delete-file p true)))))

(deftest tamper-detected
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)]
        (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)
        (let [corrupted (str/replace (slurp p) "usa" "jpn")]
          (spit p corrupted)
          (is (not (:ok (k/verify-chain p))) "tamper must break the chain")))
      (finally (io/delete-file p true)))))

;; ── heartbeat (autorun) over the REAL committed registry ─────────────────────

(deftest beat-ground-from-real-registry
  (let [ds (auto/ground-datoms)]
    (is (seq ds))
    (is (every? #(= ":db/add" (first %)) ds) "every datom is an :add (EAVT op)")
    (is (some (fn [[_ _ a]] (= ":referral/verification-status" a)) ds)
        "verification-status persisted (G14 record)")))

(deftest beat-appends-then-idempotent
  (let [p (tmp)]
    (try
      (let [r1 (auto/beat {:tx-id "t1" :as-of "a1" :log-path p})
            r2 (auto/beat {:tx-id "t2" :as-of "a2" :log-path p})]
        (is (:appended r1) "first beat appends")
        (is (pos? (:count r1)))
        (is (not (:appended r2)) "second beat over identical registry is a NO-OP")
        (is (= :no-change (:reason r2)))
        (is (= 1 (:length (k/verify-chain p))) "chain stays length 1 (idempotent-by-content)"))
      (finally (io/delete-file p true)))))

(deftest beat-appends-on-change
  (let [p (tmp)]
    (try
      (auto/beat {:datoms (d1) :tx-id "t1" :as-of "a1" :log-path p})
      (let [r2 (auto/beat {:datoms (d2) :tx-id "t2" :as-of "a2" :log-path p})]
        (is (:appended r2) "changed registry datoms append a new tx")
        (is (= 2 (:length (k/verify-chain p)))))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'chigiri.methods.test-datom-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
