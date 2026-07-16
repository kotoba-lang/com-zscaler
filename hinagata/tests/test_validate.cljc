(ns hinagata.tests.test-validate
  "hinagata 雛形 — integrity-validator tests (ADR-2606111954). 1:1 Clojure port of
  tests/test_validate.py. Pure fns.

  Enforces the maturity invariants validate.cljc checks: the committed seed has ZERO structural
  errors, and its remaining soft warnings stay in the honestly-allowed categories (a generic
  structural clause with no dedicated concept, or a registered-but-uncited statute) — G5 sourcing
  honesty. The validator checks STRUCTURE, never the merit of the law (G1/N3)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.validate :as validate]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))
(defn load-seed [] (analyze/load-file* seed))

(deftest test-seed-has-zero-errors
  (let [{:keys [nodes edges]} (load-seed)
        [errors _] (validate/validate nodes edges)]
    (is (= [] errors) (str "seed has structural integrity errors:\n  " (str/join "\n  " errors)))))

(deftest test-warnings-are-only-allowed-soft-categories
  (testing "Remaining warnings must be honest soft issues, never a silent structural defect."
    (let [{:keys [nodes edges]} (load-seed)
          [_ warnings] (validate/validate nodes edges)]
      (doseq [w warnings]
        (let [ok (or (str/includes? w "does not :instantiate any concept")
                     (str/includes? w "registry-only")
                     (str/includes? w "not used by any template")
                     (str/includes? w "not instantiated by any clause")
                     (str/includes? w "has no signature clause"))]
          (is ok (str "unexpected warning category (investigate): " w)))))))

(deftest test-every-template-is-complete
  (testing "Defence-in-depth: every template has clauses + a governing jurisdiction (errors, not warns)."
    (let [{:keys [nodes edges]} (load-seed)
          [errors _] (validate/validate nodes edges)]
      (is (not (some #(or (str/includes? % "has no clauses") (str/includes? % "has no :governed-by"))
                     errors))))))

(deftest test-all-citation-targets-are-statutes
  (let [{:keys [nodes edges]} (load-seed)
        [errors _] (validate/validate nodes edges)]
    (is (not (some #(str/includes? % "expected :statute") errors)))))

(deftest test-relational-edges-are-well-typed
  (testing ":conflicts-with is clause↔clause (no self-loop); :derived-from is template→template."
    (let [{:keys [nodes edges]} (load-seed)
          conflicts (filter #(= ":conflicts-with" (get % ":en/kind")) edges)
          derived (filter #(= ":derived-from" (get % ":en/kind")) edges)]
      (is (and (seq conflicts) (seq derived))
          "the :conflicts-with / :derived-from relations should be exercised")
      (doseq [e conflicts]
        (is (= ":clause" (get-in nodes [(get e ":en/from") ":lt/kind"])))
        (is (= ":clause" (get-in nodes [(get e ":en/to") ":lt/kind"])))
        (is (not= (get e ":en/from") (get e ":en/to")) "conflict self-loop"))
      (doseq [e derived]
        (is (= ":template" (get-in nodes [(get e ":en/from") ":lt/kind"])))
        (is (= ":template" (get-in nodes [(get e ":en/to") ":lt/kind"]))))
      (let [[errors _] (validate/validate nodes edges)]
        (is (not (some #(or (str/includes? % "conflicts-with") (str/includes? % "derived-from"))
                       errors)))))))

(deftest test-all-ten-edge-kinds-exercised
  (testing "Maturity completeness: every edge kind in the ontology is used at least once."
    (let [{:keys [nodes edges]} (load-seed)
          kinds (set (map #(get % ":en/kind") edges))
          expected #{":has-clause" ":cites-statute" ":mandated-by" ":instantiates" ":governed-by"
                     ":applies-in" ":translates" ":conflicts-with" ":derived-from" ":supersedes"}
          missing (clojure.set/difference expected kinds)]
      (is (empty? missing) (str "ontology edge kinds never exercised: " missing))
      ;; :supersedes must be template→template
      (doseq [e edges :when (= ":supersedes" (get e ":en/kind"))]
        (is (= ":template" (get-in nodes [(get e ":en/from") ":lt/kind"])))
        (is (= ":template" (get-in nodes [(get e ":en/to") ":lt/kind"])))))))
