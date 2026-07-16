#!/usr/bin/env bb
;; uzu 渦 — heartbeat tests (deterministic + idempotent-by-content).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_autorun.cljc
(ns uzu.methods.test-autorun
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.autorun :as auto]
            [uzu.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(defn tmp [] (str (System/getProperty "java.io.tmpdir") "/uzu-auto-" (gensym) ".kotoba.edn"))

(deftest assess-runs-everything
  (let [a (auto/assess seed)]
    (is (= 3 (count (:lives a))) "all three organisms lived the tape")
    (is (pos? (count (:datoms a))) "emits organism + flow datoms")
    (is (some #(true? (:alive? %)) (:lives a)) "at least one survivor (kurage)")
    (is (some #(false? (:alive? %)) (:lives a)) "at least one death (meial/gyoja)")))

(deftest assess-includes-self-reflection
  (let [a (auto/assess seed)]
    (is (map? (:digest a)) "the heartbeat reflects on its own colony")
    (is (= 1 (get-in a [:digest :n-alive])) "it knows exactly one organism self-maintained")
    (is (some #(= "uzu:digest/colony" (second %)) (:datoms a))
        "colony digest datoms are persisted alongside organism + flow datoms")))

(deftest beat-appends-then-is-idempotent
  (let [p (tmp)
        r1 (auto/beat {:seed seed :tx-id "b1" :as-of "a1" :log-path p})
        r2 (auto/beat {:seed seed :tx-id "b2" :as-of "a2" :log-path p})]
    (is (true? (:appended r1)) "first beat appends")
    (is (false? (:appended r2)) "identical content ⇒ NO-OP")
    (is (= :no-change (:reason r2)))
    (is (= (:head r1) (:head r2)) "head unchanged on a no-op")
    (is (= 1 (:length (k/verify-chain p))) "log has exactly one tx")
    (is (:ok (k/verify-chain p)) "chain verifies")
    (.delete (io/file p))))

(deftest determinism-same-inputs-same-cid
  (let [p1 (tmp) p2 (tmp)
        r1 (auto/beat {:seed seed :tx-id "x" :as-of "y" :log-path p1})
        r2 (auto/beat {:seed seed :tx-id "x" :as-of "y" :log-path p2})]
    (is (= (:head r1) (:head r2)) "no wall clock / no randomness ⇒ identical content-address")
    (.delete (io/file p1)) (.delete (io/file p2))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-autorun)]
  (when (pos? (+ fail error)) (System/exit 1)))
