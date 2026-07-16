#!/usr/bin/env bb
;; uzu 渦 — maturity self-audit (scorecard) tests.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_scorecard.cljc
(ns uzu.methods.test-scorecard
  (:require [uzu.methods.scorecard :as sc]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def manifest (edn/read-string (slurp "20-actors/uzu/manifest.edn")))
(def actor-dir "20-actors/uzu")
(def lex-root "00-contracts/lexicons/com/etzhayyim/uzu")
(def t (sc/tally manifest))
(def a (sc/audit manifest actor-dir lex-root))

(deftest tally-reflects-manifest
  (is (= (count (:actor/methods manifest)) (:methods t)))
  (is (= (count (:actor/gates manifest)) (:gates t)))
  (is (= (count (:actor/lex manifest)) (:lexicons t)))
  (is (= (count (get-in manifest [:actor/tests :suites])) (:suites t)))
  (is (pos? (:methods t))))

(deftest audit-finds-no-drift
  ;; the strong check: every declared method file, suite, and lexicon exists on disk
  (is (true? (:ok a)) (str "manifest↔filesystem drift: " (dissoc a :ok)))
  (is (empty? (:missing-methods a)))
  (is (empty? (:missing-suites a)))
  (is (empty? (:missing-lexicons a))))

(deftest audit-catches-a-fabricated-method
  (let [bad (update manifest :actor/methods conj {:method/id "ghost" :method/file "methods/ghost.cljc"})]
    (is (false? (:ok (sc/audit bad actor-dir lex-root))))
    (is (some #{"methods/ghost.cljc"} (:missing-methods (sc/audit bad actor-dir lex-root))))))

(deftest datoms-are-eavt
  (let [ds (sc/datoms t a)]
    (is (every? #(= 4 (count %)) ds))
    (is (every? #(= "uzu:scorecard/self" (second %)) ds))
    (is (some #(= ":uzu.scorecard/audit-ok" (nth % 2)) ds))))

(deftest report-is-markdown
  (let [r (sc/report t a)]
    (is (str/includes? r "self-audit"))
    (is (str/includes? r "audit: ✅"))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'uzu.methods.test-scorecard)]
    (when (pos? (+ fail error)) (System/exit 1))))
