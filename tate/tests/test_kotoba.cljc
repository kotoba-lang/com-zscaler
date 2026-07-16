#!/usr/bin/env bb
;; tate 盾 — defense-ledger persistence + heartbeat tests.
;; Run:  bb --classpath 20-actors 20-actors/tate/tests/test_kotoba.cljc
(ns tate.tests.test-kotoba
  (:require [tate.methods.kotoba :as k]
            [tate.methods.autorun :as auto]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/tate-ledger-test-" (gensym) ".edn"))
(defn- d1 [] [(k/add "clause:auto-renewal" ":clause/jurisdiction" ":jp")
              (k/add "clause:auto-renewal" ":clause/route" ":kaiyaku")])
(defn- d2 [] [(k/add "proc:shiharai-tokusoku" ":proc/jurisdiction" ":jp")
              (k/add "proc:shiharai-tokusoku" ":proc/verify-current-law" true)])

(deftest tx-cid-deterministic-and-content-sensitive
  (is (= (k/tx-cid (d1) "") (k/tx-cid (d1) "")) "same input → same cid")
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d2) "")) "different datoms → different cid")
  (is (not= (k/tx-cid (d1) "") (k/tx-cid (d1) "bdeadbeef")) "different prev → different cid")
  (is (clojure.string/starts-with? (k/tx-cid (d1) "") "b")))

(deftest append-read-roundtrip
  (let [p (tmp)]
    (try
      (let [tx (k/make-tx (d1) "t1" "as1" "")
            cid (k/append-tx tx p)]
        (is (= cid (get tx ":tx/cid")))
        (let [txs (k/read-log p)]
          (is (= 1 (count txs)))
          (is (= cid (get (first txs) ":tx/cid")))
          (is (= (d1) (get (first txs) ":tx/datoms")) "datoms round-trip byte-faithfully")))
      (finally (io/delete-file p true)))))

(deftest chaining-and-verify
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)
            c2 (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)]
        (is (not= c1 c2) "two distinct txs")
        (is (= c2 (k/head-cid p)) "head = last tx cid")
        (let [v (k/verify-chain p)]
          (is (:ok v))
          (is (= 2 (:length v)))
          (is (= -1 (:broken-at v)))))
      (finally (io/delete-file p true)))))

(deftest tamper-detected
  (let [p (tmp)]
    (try
      (let [c1 (k/append-tx (k/make-tx (d1) "t1" "as1" "") p)]
        (k/append-tx (k/make-tx (d2) "t2" "as2" c1) p)
        ;; corrupt the 2nd tx's datoms in place (cid no longer matches)
        (let [corrupted (clojure.string/replace (slurp p) ":kaiyaku" ":referral")]
          (spit p corrupted)
          (let [v (k/verify-chain p)]
            (is (not (:ok v)) "tamper must break the chain"))))
      (finally (io/delete-file p true)))))

(deftest resume-safe-deterministic
  ;; same datoms + same prev → identical cid across runs (no wall clock)
  (let [prev "bcafef00d"]
    (is (= (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")
           (get (k/make-tx (d1) "t" "a" prev) ":tx/cid")))))

;; ── heartbeat (autorun) ──────────────────────────────────────────────────────

(deftest beat-ground-only-and-real-registries
  (let [ds (auto/ground-datoms)]
    (is (seq ds) "ground datoms are produced from the real committed registries")
    (is (every? #(= ":db/add" (first %)) ds) "every datom is an :add (EAVT op)")
    (is (not-any? #(= ":bond/is-transient" (nth % 2)) ds)
        "GROUND only — no derived/transient datoms persisted (G2)")))

(deftest beat-appends-then-idempotent
  (let [p (tmp)]
    (try
      (let [r1 (auto/beat {:tx-id "t1" :as-of "a1" :log-path p})
            r2 (auto/beat {:tx-id "t2" :as-of "a2" :log-path p})]
        (is (:appended r1) "first beat appends")
        (is (pos? (:count r1)))
        (is (not (:appended r2)) "second beat over identical ground state is a NO-OP")
        (is (= :no-change (:reason r2)))
        (is (= 1 (:length (k/verify-chain p))) "chain stays length 1 (idempotent-by-content)"))
      (finally (io/delete-file p true)))))

(deftest beat-appends-on-change
  (let [p (tmp)]
    (try
      (auto/beat {:datoms (d1) :tx-id "t1" :as-of "a1" :log-path p})
      (let [r2 (auto/beat {:datoms (d2) :tx-id "t2" :as-of "a2" :log-path p})]
        (is (:appended r2) "changed ground datoms append a new tx")
        (let [v (k/verify-chain p)]
          (is (:ok v))
          (is (= 2 (:length v)))))
      (finally (io/delete-file p true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tate.tests.test-kotoba)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
