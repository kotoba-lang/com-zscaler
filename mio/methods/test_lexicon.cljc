#!/usr/bin/env bb
;; 澪 mio — flowClaim lexicon validator tests.
;; Run:  bb --classpath 20-actors 20-actors/mio/methods/test_lexicon.cljc
(ns mio.methods.test-lexicon
  (:require [mio.methods.lexicon :as lex]
            [mio.methods.mio-edn :as me]
            [clojure.test :refer [deftest is run-tests]]))

(def schema (lex/load-schema "20-actors/mio/kotoba/lexicon.flowClaim.edn"))

(def good
  {:type :claim :id "x-1" :flow-class :waste-heat :source-actor "okibi"
   :order-delta-kwh 1200 :baseline-method "counterfactual: dumped to ambient"
   :additionality 0.8 :measurement-source :signed-meter
   :double-count-key "okibi:a~b" :leakage 0.1})

(deftest schema-loads
  (is (= "com.etzhayyim.mio.flowClaim" (:lexicon/id schema)))
  (is (seq (:required schema)))
  (is (seq (:forbidden schema))))

(deftest valid-claim-passes
  (is (lex/valid? schema good))
  (is (empty? (lex/validate-claim schema good))))

(deftest each-violation-is-caught
  (is (seq (lex/validate-claim schema (dissoc good :baseline-method))) "missing required field")
  (is (seq (lex/validate-claim schema (assoc good :flow-class :nonsense))) "bad flow-class enum")
  (is (seq (lex/validate-claim schema (assoc good :additionality 1.5))) "additionality > 1")
  (is (seq (lex/validate-claim schema (assoc good :leakage -0.1))) "leakage < 0")
  (is (seq (lex/validate-claim schema (assoc good :order-delta-kwh -5))) "negative order-delta")
  (is (seq (lex/validate-claim schema (assoc good :baseline-method "  "))) "blank baseline")
  (is (seq (lex/validate-claim schema (assoc good :measurement-source :guess))) "bad measurement-source")
  (is (seq (lex/validate-claim schema (assoc good :type :note))) "wrong :type const")
  (is (seq (lex/validate-claim schema (assoc good :source-actor "stranger"))) "unknown source-actor"))

(deftest forbidden-fields-rejected
  ;; the PoW→PoUF + map-not-market gates at the interface
  (is (seq (lex/validate-claim schema (assoc good :consumed-reward-kwh 5))) "consumption-reward forbidden")
  (is (seq (lex/validate-claim schema (assoc good :cash 100))) "cash forbidden")
  (is (seq (lex/validate-claim schema (assoc good :person "alice"))) "person forbidden"))

(deftest mio-seed-fixtures-vs-lexicon
  ;; mio's seed includes deliberately-degenerate fixtures to exercise the §9 gate.
  ;; The lexicon (write-surface contract) accepts the well-formed claims and rejects
  ;; the malformed blank-baseline fixture — the same degeneracy §9 routes to
  ;; :insufficient-evidence, here caught one layer earlier at the interface.
  (let [claims (me/claims "20-actors/mio/kotoba/seed.edn")
        blank (first (filter #(= "in-no-baseline-01" (:id %)) claims))
        well-formed (remove #(= "in-no-baseline-01" (:id %)) claims)]
    (is (not (lex/valid? schema blank)) "the blank-baseline fixture is rejected by the lexicon")
    (doseq [c well-formed]
      (is (lex/valid? schema c) (str (:id c) " conforms: " (lex/validate-claim schema c))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'mio.methods.test-lexicon)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
