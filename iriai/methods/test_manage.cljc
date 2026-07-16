#!/usr/bin/env bb
;; iriai 入会 — management (管理) / governance tests (1 SBT=1 vote, :intent-only, no-server-key).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_manage.cljc
(ns iriai.methods.test-manage
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.fund :as fund]
            [iriai.methods.manage :as manage]
            [iriai.methods.gates :as g]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- cells [] (ie/cells seed-path))
(defn- lg [] (manage/ledger (fund/plan (cells))))

;; ── one decision per funding proposal ──────────────────────────────────────────
(deftest decision-per-proposal
  (is (= 11 (get (lg) "count")) "11 fundable cells (incl. kibou road) → 11 governance decisions"))

;; ── governance route is 1 SBT = 1 vote + Council ───────────────────────────────
(deftest governance-is-one-sbt-one-vote
  (doseq [d (get (lg) "decisions")]
    (is (= "1-sbt-1-vote" (get d "governance")))
    (is (= "48h" (get d "timelock")))
    (is (#{"council-lv6+" "council-lv7+"} (get d "council_attestation")))))

(deftest critical-infra-escalates-to-lv7
  ;; kibou electric + water provision = critical-infra build → Council Lv7+
  (let [bylvl (get (lg) "by_council_level")]
    (is (pos? (get bylvl "council-lv7+" 0)) "critical-infra provision escalates to Lv7+")
    (is (pos? (get bylvl "council-lv6+" 0)) "non-critical decisions stay Lv6+")))

;; ── G5: compute-only R0 — every decision stops at :intent ──────────────────────
(deftest g5-all-intent-only
  (is (true? (get (lg) "all_intent_only")))
  (doseq [d (get (lg) "decisions")]
    (is (= :intent (get d "actuation_class")))
    (is (= :intent (g/check-actuation-intent (get d "actuation_class"))))
    (is (str/includes? (get d "live_actuation_gate") "council-lv7+"))))

;; ── G6: no-server-key — every decision is keyless, member-attributed ───────────
(deftest g6-all-keyless
  (is (true? (get (lg) "all_keyless")))
  (doseq [d (get (lg) "decisions")]
    (is (false? (get d "server_held_key")))
    (is (= "member-cacao-leash" (get d "attribution")))
    (is (false? (g/check-keyless (get d "server_held_key"))))))

;; ── G3/G5 structural: no decide/dispatch/actuate attrs in datoms ───────────────
(deftest g3-g5-no-sovereign-or-actuation-attrs
  (let [edn (manage/render-datoms (lg))]
    (is (not (str/includes? edn ":iriai.manage/decide")))
    (is (not (str/includes? edn ":iriai.manage/dispatch")))
    (is (not (str/includes? edn ":iriai/actuate")))
    (is (str/includes? edn ":iriai.manage/actuation-class"))
    (is (str/includes? edn ":iriai.manage/server-held-key"))
    (is (g/forbidden-absent? edn))))

(deftest report-declares-steward-not-sovereign
  (let [md (manage/render-report (lg))]
    (is (str/includes? md "iriai PROPOSES"))
    (is (str/includes? md "Compute-only R0"))
    (is (str/includes? md "no-server-key"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-manage)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
