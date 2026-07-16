#!/usr/bin/env bb
;; iriai 入会 — heartbeat tests (deterministic, idempotent-by-content, resume-safe).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_autorun.cljc
(ns iriai.methods.test-autorun
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.autorun :as autorun]
            [iriai.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(def ^:private tmp (str (System/getProperty "java.io.tmpdir") "/iriai-test-autorun.kotoba.edn"))
(defn- clean! [] (let [f (io/file tmp)] (when (.exists f) (.delete f))))
(defn- cells [] (ie/cells seed-path))

;; ── one beat appends the combined infra+fund+manage tx ─────────────────────────
(deftest first-beat-appends
  (clean!)
  (let [r (autorun/beat {:cells (cells) :tx-id "b1" :as-of "a1" :log-path tmp})]
    (is (:appended r))
    (is (pos? (:count r)) "infra + fund + manage datoms")
    (is (= 11 (:fund r)) "11 fundable cells (incl. kibou road)")
    (is (= 11 (:gov r))  "11 governance decisions")
    (is (:ok (k/verify-chain tmp)))
    (clean!)))

;; ── IDEMPOTENT-BY-CONTENT: an unchanged beat is a no-op ────────────────────────
(deftest second-beat-no-op-when-unchanged
  (clean!)
  (let [r1 (autorun/beat {:cells (cells) :tx-id "b1" :as-of "a1" :log-path tmp})
        r2 (autorun/beat {:cells (cells) :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r1))
    (is (not (:appended r2)) "unchanged assessment → no append")
    (is (= :no-change (:reason r2)))
    (is (= (:head r1) (:head r2)) "head unchanged")
    (is (= 1 (count (k/read-log tmp))) "ledger records CHANGES, not liveness ticks")
    (clean!)))

;; ── a CHANGED assessment DOES append (and chains) ──────────────────────────────
(deftest changed-assessment-appends-and-chains
  (clean!)
  (let [cs (cells)
        ;; flip kibou electric from unconnected (provision) to fully served (maintain)
        cs2 (mapv (fn [c]
                    (if (and (= "kibou" (:region c)) (= :electric (:lifeline c)))
                      (assoc c :served-pop 1000)
                      c))
                  cs)
        r1 (autorun/beat {:cells cs  :tx-id "b1" :as-of "a1" :log-path tmp})
        r2 (autorun/beat {:cells cs2 :tx-id "b2" :as-of "a2" :log-path tmp})]
    (is (:appended r1))
    (is (:appended r2) "a different assessment appends a new tx")
    (is (not= (:head r1) (:head r2)))
    (is (= 2 (count (k/read-log tmp))))
    (is (:ok (k/verify-chain tmp)) "chain still verifies after change")
    (clean!)))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
