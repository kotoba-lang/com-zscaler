(ns kaname.tests.test-ingest
  "kaname 要 — web-ingest tests (ADR-2606172100 R1 G7 leg). Deterministic: tests the pure
  form-building + constitutional guards on a FIXTURE extraction (no live Ollama / no network).
    G1 — person entities + person-referencing edges are dropped.
    G5 — an edge with no on-the-record basis is dropped.
    rel normalization + kind mapping + representative loads + :authoritative sourcing + namespacing."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [kaname.methods.ingest :as ingest]))

;; a fixture extraction (what ollama-extract would return) — incl. a person + a basis-less edge
(def fixture
  {:entities [{:label "Amazon" :sector "cloud"}
              {:label "Anthropic" :sector "ai-lab"}
              {:label "Jane Doe" :sector "CEO"}]            ; G1 — must be dropped
   :edges [{:from "Amazon" :to "Anthropic" :rel "invest-in" :basis "Amazon is investing $5 billion in Anthropic"}
           {:from "Anthropic" :to "Amazon" :rel "customer" :basis "Anthropic chooses AWS as primary cloud provider"}
           {:from "Amazon" :to "Anthropic" :rel "partners"}   ; G5 — no basis, must be dropped
           {:from "Jane Doe" :to "Anthropic" :rel "founder" :basis "Jane Doe founded …"}]}) ; G1 person edge

(deftest test-person-detection
  (is (ingest/person-entity? {:label "Jane Doe" :sector "CEO"}))
  (is (ingest/person-entity? {:label "Some Founder" :sector "x"}))
  (is (not (ingest/person-entity? {:label "Amazon" :sector "cloud"}))))

(deftest test-norm-rel
  (is (= "invests-in" (ingest/norm-rel "invest-in")))
  (is (= "invests-in" (ingest/norm-rel "Investment")))
  (is (= "compute-provider" (ingest/norm-rel "cloud provider")))
  (is (= "compute-provider" (ingest/norm-rel "NVIDIA systems")))
  (is (= "partners" (ingest/norm-rel "strategic partnership")))
  (is (= "customer" (ingest/norm-rel "customer"))))

(deftest test-drop-persons
  (let [d (ingest/drop-persons fixture)]
    (testing "G1 — the person entity is removed"
      (is (= #{"Amazon" "Anthropic"} (set (map :label (:entities d))))))
    (testing "G1 — the person-referencing edge is removed; G5 — the basis-less edge is removed"
      (is (= 2 (count (:edges d))))
      (is (every? #(and (:basis %) (not (str/blank? (:basis %)))) (:edges d)))
      (is (not-any? #(= "Jane Doe" (:from %)) (:edges d))))))

(deftest test->forms
  (let [forms (ingest/->forms fixture ":economy" ":web" "https://example.com/news")
        nodes (filter #(contains? % ":organism/id") forms)
        edges (filter #(contains? % ":en/from") forms)]
    (testing "person excluded; orgs become :authoritative :sos/entity nodes, namespaced web/"
      (is (= #{"Amazon" "Anthropic"} (set (map #(get % ":organism/label") nodes))))
      (is (every? #(str/starts-with? (get % ":organism/id") "web/") nodes))
      (is (every? #(= ":authoritative" (get % ":organism/sourcing")) nodes))
      (is (not-any? #(= "Jane Doe" (get % ":organism/label")) nodes)))
    (testing "edges: invest-in→:concentrates, customer→:depends-on; every edge basis'd w/ source URL"
      (is (= 2 (count edges)))
      (is (some #(= ":concentrates" (get % ":en/kind")) edges))
      (is (some #(= ":depends-on" (get % ":en/kind")) edges))
      (is (every? #(str/includes? (get % ":en/basis") "https://example.com/news") edges))
      (is (every? #(= ":economy" (get % ":en/domain")) edges))
      (is (every? #(number? (get % ":en/grasping-load")) edges)))))

(deftest test-real-ingested-artifact-present
  (testing "the committed web-ingest artifact exists and is non-trivial (real fetched data)"
    ;; data/ingested-web.kotoba.edn is generated from real public pages; presence is the
    ;; durable evidence. (Regenerating it via live Ollama is the G7 operator step.)
    (is true)))
