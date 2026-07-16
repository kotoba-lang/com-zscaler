#!/usr/bin/env bb
;; iriai 入会 — constitutional gate tests (the charter inversions, structurally enforced).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_gates.cljc
(ns iriai.methods.test-gates
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.infra :as infra]
            [iriai.methods.fund :as fund]
            [iriai.methods.manage :as manage]
            [iriai.methods.gates :as g]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- cells [] (ie/cells seed-path))

;; ── the full emitted datom stream contains NO forbidden attribute (G1/G2/G3/G5) ─
(deftest forbidden-attrs-absent-from-whole-stream
  (let [a (infra/assess (cells))
        pl (fund/plan (cells))
        lg (manage/ledger pl)
        edn (str (infra/render-datoms a) (fund/render-datoms pl) (manage/render-datoms lg))]
    (is (g/forbidden-absent? edn)
        "no :iriai/shutoff / tariff / equity / :fund / decide / actuate anywhere")))

;; ── G2: give-only instrument algebra — equity/debt/subscription throw ──────────
(deftest g2-instrument-throws-on-non-give
  (is (= :grant (g/check-instrument :grant)))
  (is (= :in-kind (g/check-instrument :in-kind)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-instrument :equity)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-instrument :debt)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-instrument :subscription))))

;; ── G2: cash to consumer is structurally zero — nonzero throws ─────────────────
(deftest g2-cash-zero-throws-on-nonzero
  (is (= 0 (g/check-cash-zero 0)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-cash-zero 1)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-cash-zero 999))))

;; ── G3: steward — non-advisory / fund-binding proposal throws ──────────────────
(deftest g3-advisory-throws-on-binding
  (is (true? (g/check-advisory {"advisory" true "binds_fund" false "decided_by" "1-sbt-1-vote"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (g/check-advisory {"advisory" false "binds_fund" false "decided_by" "x"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (g/check-advisory {"advisory" true "binds_fund" true "decided_by" "x"}))))

;; ── G5: assessment only — non-:intent actuation throws ─────────────────────────
(deftest g5-actuation-throws-on-live
  (is (= :intent (g/check-actuation-intent :intent)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-actuation-intent :live)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-actuation-intent :energize))))

;; ── G6: no-server-key — a held key throws ──────────────────────────────────────
(deftest g6-keyless-throws-on-held-key
  (is (false? (g/check-keyless false)))
  (is (thrown? clojure.lang.ExceptionInfo (g/check-keyless true))))

;; ── the gate list itself covers each charter inversion ─────────────────────────
(deftest forbidden-list-covers-the-inversions
  (let [joined (apply str g/forbidden-attrs)]
    (is (clojure.string/includes? joined ":iriai/shutoff")            "G1 never-withheld")
    (is (clojure.string/includes? joined ":iriai.fund/tariff")        "G2 commons-not-market")
    (is (clojure.string/includes? joined ":iriai/fund")               "G3 steward-not-sovereign")
    (is (clojure.string/includes? joined ":iriai/actuate")            "G5 never-acts")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-gates)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
