#!/usr/bin/env bb
;; inochi 命 — biosphere-ledger persistence + heartbeat tests.
;; Run:  bb --classpath 20-actors 20-actors/inochi/tests/test_kotoba.cljc
(ns inochi.tests.test-kotoba
  (:require [inochi.methods.kotoba :as k]
            [inochi.methods.autorun :as auto]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/inochi-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "organism:vaquita" ":organism/kind" ":species")
              (k/add "organism:vaquita" ":taxon/iucn" ":critically-endangered")])
(defn- d2 [] [(k/add "en.pressure:bycatch.grasps.organism:vaquita" ":en/kind" ":grasps")
              (k/add "en.pressure:bycatch.grasps.organism:vaquita" ":en/grasping-load" 0.95)])

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

(deftest float-grasping-load-roundtrips
  (let [p (tmp)]
    (try
      (k/append-tx (k/make-tx (d2) "t1" "as1" "") p)
      (is (= (d2) (get (first (k/read-log p)) ":tx/datoms")) "float :en/grasping-load round-trips")
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
        (let [corrupted (str/replace (slurp p) ":grasps" ":restores")]
          (spit p corrupted)
          (is (not (:ok (k/verify-chain p))) "tamper must break the chain")))
      (finally (io/delete-file p true)))))

(deftest resume-safe-deterministic
  (let [prev "bcafef00d"]
    (is (= (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")
           (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")))))

;; ── heartbeat (autorun) over the REAL committed biosphere seed ───────────────

(deftest beat-ground-only-and-real-seed
  (let [ds (auto/ground-datoms)]
    (is (seq ds) "ground datoms are produced from the real committed biosphere seed")
    (is (every? #(= ":db/add" (first %)) ds) "every datom is an :add (EAVT op)")
    (is (some (fn [[_ _ a]] (= ":en/from" a)) ds) "pressure 縁 are persisted (graph not just nodes)")
    (is (not-any? (fn [[_ _ a]] (str/starts-with? (str a) ":bond/")) ds)
        "GROUND only — derived :bond/* readouts not persisted (N1/G2)")))

(deftest beat-no-occurrence-coordinates
  ;; G1: a RESTORATION map, NEVER a target-list — no lat/long/coordinate datoms
  (let [ds (auto/ground-datoms)]
    (is (not-any? (fn [[_ _ a]] (let [s (str a)]
                                  (or (str/includes? s "lat") (str/includes? s "lon")
                                      (str/includes? s "coord") (str/includes? s "occurrence")))) ds)
        "no occurrence coordinates in the ledger (G1)")))

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
     (let [{:keys [fail error]} (run-tests 'inochi.tests.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
