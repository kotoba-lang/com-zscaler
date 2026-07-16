(ns hinagata.tests.test-query
  "hinagata 雛形 — knowledge-graph query tests (ADR-2606111954). 1:1 Clojure port of
  tests/test_query.py. Pure fns over the loaded {:nodes :edges}; nothing mutates (N1).

  G1/N3 reminder: a query result is a DISCLOSED structural fact (this clause cites this
  statute / this template is governed by this jurisdiction), never a hinagata verdict."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.java.io :as io]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.query :as query]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))
(defn load-seed [] (analyze/load-file* seed))

(deftest test-templates-in-jurisdiction
  (let [{:keys [nodes edges]} (load-seed)
        jp (query/templates-in-jurisdiction nodes edges "jx.jp")]
    (is (seq jp) "expected templates governed by Japan")
    (doseq [t jp]
      (is (= ":template" (get-in nodes [t ":lt/kind"]))))
    ;; international has the broad-reach templates
    (let [intl (query/templates-in-jurisdiction nodes edges "jx.intl")]
      (is (some #{"tmpl.sales-intl"} intl)))))

(deftest test-statutes-grounding-template-are-real-statutes
  (let [{:keys [nodes edges]} (load-seed)
        st (query/statutes-grounding-template nodes edges "tmpl.dpa-gdpr")]
    (is (seq st) "GDPR DPA should rest on statutes")
    (doseq [s st]
      (is (= ":statute" (get-in nodes [s ":lt/kind"]))))
    ;; the DPA must rest on at least one GDPR article
    (is (some #(clojure.string/includes? % "gdpr") st))))

(deftest test-translations-of-nda-are-multilingual
  (let [{:keys [nodes edges]} (load-seed)
        tr (query/translations-of nodes edges "tmpl.nda-mutual")]
    (is (>= (count tr) 5) (str "NDA should have many translations, got " tr))
    (let [langs (set (map #(get-in nodes [% ":template/lang"]) tr))]
      (is (>= (count langs) 5) (str "translations should span many languages, got " langs)))))

(deftest test-conflicting-clauses-symmetric-lookup
  (let [{:keys [nodes edges]} (load-seed)
        c (query/conflicting-clauses nodes edges "cl.ip-assignment")]
    (is (or (some #{"cl.cc-license"} c) (some #{"cl.copyleft-license"} c)))
    ;; the relation resolves from either side
    (let [back (query/conflicting-clauses nodes edges "cl.copyleft-license")]
      (is (some #{"cl.ip-assignment"} back)))))

(deftest test-jurisdictions-for-concept-data-protection-is-global
  (let [{:keys [nodes edges]} (load-seed)
        jx (query/jurisdictions-for-concept nodes edges "concept.data-protection")]
    (is (>= (count jx) 4) (str "data-protection should be grounded across many jurisdictions, got " jx))))

(deftest test-coverage-gaps-is-the-inverse-worklist
  (testing "gaps(concept) = MAJOR jurisdictions present in the graph that don't ground the concept."
    (let [{:keys [nodes edges]} (load-seed)
          have (set (query/jurisdictions-for-concept nodes edges "concept.data-protection"))
          gaps (query/coverage-gaps nodes edges "concept.data-protection")]
      (doseq [g gaps]
        (is (not (contains? have g)))
        (is (and (some #{g} query/major-jurisdictions) (contains? nodes g))))
      ;; electronic-signature is broadly grounded, so it should have few/zero gaps
      (let [esign-gaps (query/coverage-gaps nodes edges "concept.electronic-signature")]
        (is (<= (count esign-gaps) (count (query/coverage-gaps nodes edges "concept.escrow")))
            "broadly-grounded e-signature should have no more gaps than a niche concept")))))
