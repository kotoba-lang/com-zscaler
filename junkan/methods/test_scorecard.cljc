#!/usr/bin/env bb
;; junkan 循環 — scorecard generator tests.
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_scorecard.cljc
(ns junkan.methods.test-scorecard
  (:require [junkan.methods.junkan-edn :as je]
            [junkan.methods.analyze :as az]
            [junkan.methods.validate :as v]
            [junkan.methods.scorecard :as sc]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
(def onto-path "20-actors/junkan/kotoba/ontology.junkan-gov.edn")
(defn- is* [] (je/instruments seed-path))
(defn- enums [] (:enums (edn/read-string (slurp onto-path))))

(deftest renders-from-live-substrate
  (let [a (az/analyze (is*))
        val (v/check (is*) (enums))
        md (sc/render a val)]
    (is (str/includes? md "governance-asymmetry SCORECARD"))
    (is (str/includes? md "Continental balance"))
    (is (str/includes? md "Asymmetry stock regimes"))
    (is (str/includes? md "Substrate integrity"))
    (is (str/includes? md "next focus (self-balancing)"))
    ;; reflects the real instrument count + clean integrity
    (is (str/includes? md (str "instruments**: " (count (is*)))))
    (is (str/includes? md "✅ OK") "live substrate integrity is clean in the scorecard")))

(deftest scorecard-is-read-only
  (let [src (slurp "20-actors/junkan/methods/scorecard.cljc")]
    ;; the pure render carries no outward/dispatch verb (G4); only -main does I/O (spit)
    (is (nil? (re-find #"(?im)\((?:post|dispatch|send|transact!|append-tx)\b" src))
        "scorecard.cljc has no outward-channel call (G4)")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-scorecard)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
