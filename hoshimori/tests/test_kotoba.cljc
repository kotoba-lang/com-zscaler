#!/usr/bin/env bb
;; hoshimori 星守 — orbital-stewardship-ledger persistence + heartbeat tests.
;; Run:  bb --classpath 20-actors 20-actors/hoshimori/tests/test_kotoba.cljc
(ns hoshimori.tests.test-kotoba
  (:require [hoshimori.methods.kotoba :as k]
            [hoshimori.methods.autorun :as auto]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/hoshimori-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "shell:leo-550" ":organism/kind" ":shell")
              (k/add "shell:leo-550" ":shell/regime" ":leo")])
(defn- d2 [] [(k/add "en.hazard:debris.congests.shell:leo-550" ":en/kind" ":congests")
              (k/add "en.hazard:debris.congests.shell:leo-550" ":en/orbit-load" 0.6)])

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

(deftest float-orbit-load-roundtrips
  (let [p (tmp)]
    (try
      (k/append-tx (k/make-tx (d2) "t1" "as1" "") p)
      (is (= (d2) (get (first (k/read-log p)) ":tx/datoms")) "float :en/orbit-load round-trips")
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
        (let [corrupted (str/replace (slurp p) ":congests" ":steards")]
          (spit p corrupted)
          (is (not (:ok (k/verify-chain p))) "tamper must break the chain")))
      (finally (io/delete-file p true)))))

(deftest resume-safe-deterministic
  (let [prev "bcafef00d"]
    (is (= (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")
           (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")))))

;; ── heartbeat (autorun) over the REAL committed orbit seed ───────────────────

(deftest beat-ground-only-and-real-seed
  (let [ds (auto/ground-datoms)]
    (is (seq ds) "ground datoms are produced from the real committed orbit seed")
    (is (every? #(= ":db/add" (first %)) ds) "every datom is an :add (EAVT op)")
    (is (some (fn [[_ _ a]] (= ":en/from" a)) ds) "congestion 縁 are persisted (graph not just nodes)")
    (is (not-any? (fn [[_ _ a]] (str/starts-with? (str a) ":bond/")) ds)
        "GROUND only — derived :bond/* readouts not persisted (N1/G2)")))

(deftest beat-no-precise-ephemeris
  ;; G1: a STEWARDSHIP map, NEVER a targeting aid — no per-object lat/lon/alt/velocity/TLE/ephemeris
  (let [ds (auto/ground-datoms)]
    (is (not-any? (fn [[_ _ a]] (let [s (str a)]
                                  (or (str/includes? s "lat") (str/includes? s "lon")
                                      (str/includes? s "alt-deg") (str/includes? s "velocity")
                                      (str/includes? s "ephemeris") (str/includes? s "tle")))) ds)
        "no precise predictive ephemeris in the ledger (G1, shell/regime-aggregate only)")))

(deftest beat-appends-then-idempotent
  (let [p (tmp)]
    (try
      (let [r1 (auto/beat {:tx-id "t1" :as-of "a1" :log-path p})
            r2 (auto/beat {:tx-id "t2" :as-of "a2" :log-path p})]
        (is (:appended r1) "first beat appends")
        (is (pos? (:count r1)))
        (is (not (:appended r2)) "second beat over identical seed is a NO-OP")
        (is (= :no-change (:reason r2)))
        (is (= 1 (:length (k/verify-chain p))) "chain stays length 1 (idempotent-by-content)"))
      (finally (io/delete-file p true)))))

(deftest beat-appends-on-change
  (let [p (tmp)]
    (try
      (auto/beat {:datoms (d1) :tx-id "t1" :as-of "a1" :log-path p})
      (let [r2 (auto/beat {:datoms (d2) :tx-id "t2" :as-of "a2" :log-path p})]
        (is (:appended r2) "changed ground datoms append a new tx")
        (is (= 2 (:length (k/verify-chain p)))))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'hoshimori.tests.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
