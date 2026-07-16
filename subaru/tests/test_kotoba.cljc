#!/usr/bin/env bb
;; subaru 昴 — constellation-ledger persistence + heartbeat tests.
;; Run:  bb --classpath 20-actors 20-actors/subaru/tests/test_kotoba.cljc
(ns subaru.tests.test-kotoba
  (:require [subaru.methods.kotoba :as k]
            [subaru.methods.autorun :as auto]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/subaru-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "con.constellation.subaru" ":organism/kind" ":constellation")
              (k/add "con.constellation.subaru" ":constellation/open-bus" true)])
(defn- d2 [] [(k/add "en.con.constellation.subaru.covers.area:unconnected" ":en/kind" ":covers")
              (k/add "en.con.constellation.subaru.covers.area:unconnected" ":en/coverage-pct" 88.5)])

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

(deftest float-coverage-pct-roundtrips
  (let [p (tmp)]
    (try
      (k/append-tx (k/make-tx (d2) "t1" "as1" "") p)
      (is (= (d2) (get (first (k/read-log p)) ":tx/datoms")) "float :en/coverage-pct round-trips")
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
        (let [corrupted (str/replace (slurp p) ":covers" ":jams")]
          (spit p corrupted)
          (is (not (:ok (k/verify-chain p))) "tamper must break the chain")))
      (finally (io/delete-file p true)))))

(deftest resume-safe-deterministic
  (let [prev "bcafef00d"]
    (is (= (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")
           (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")))))

;; ── heartbeat (autorun) over the REAL committed constellation seed ───────────

(deftest beat-ground-only-and-real-seed
  (let [ds (auto/ground-datoms)]
    (is (seq ds) "ground datoms are produced from the real committed constellation seed")
    (is (every? #(= ":db/add" (first %)) ds) "every datom is an :add (EAVT op)")
    (is (some (fn [[_ _ a]] (= ":en/from" a)) ds) "constellation 縁 are persisted (graph not just nodes)")
    (is (not-any? (fn [[_ _ a]] (str/starts-with? (str a) ":bond/")) ds)
        "GROUND only — derived :bond/* readouts not persisted (N1)")))

(deftest beat-no-surveillance-or-targeting
  ;; G1: a connectivity-COMMONS map — no DPI/user-location/ISR-targeting/jam attribute
  (let [ds (auto/ground-datoms)]
    (is (not-any? (fn [[_ _ a]] (let [s (str a)]
                                  (or (str/includes? s "dpi") (str/includes? s "user-location")
                                      (str/includes? s "targeting") (str/includes? s "isr")
                                      (str/includes? s "/jam")))) ds)
        "no DPI/user-location/targeting/jam datoms in the ledger (G1, commons-not-weapon)")))

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
     (let [{:keys [fail error]} (run-tests 'subaru.tests.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
