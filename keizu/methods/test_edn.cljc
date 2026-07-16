(ns keizu.methods.test-edn
  "Tests for keizu.methods.edn — the Clojure port of methods/_edn.py.

  Exercises the reader on keizu's REAL :representative seed
  (data/seed-relation-graph.kotoba.edn) with concrete structural assertions, and
  pins the fidelity invariant the Python `load_edn` guarantees: keywords come back
  as their \":ns/name\" STRINGS (not Clojure keywords), so the offline analyzer keys
  on identical shapes whichever runtime reads the seed."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [keizu.methods.edn :as edn]))

;; ── fixture: locate keizu's committed seed relative to this test file ─────────
;; bb runs with cwd at the worktree root; the seed lives at a fixed actor path.

(def seed-path
  "20-actors/keizu/data/seed-relation-graph.kotoba.edn")

;; ── primitive / atom-level parsing (mirror of Python _atom) ───────────────────

(deftest atoms-and-literals
  (testing "string, bool, nil, int"
    (is (= [1 2 3] (edn/parse-edn "[1 2 3]")))
    (is (= [true false nil] (edn/parse-edn "[true false nil]")))
    (is (= ["hello"] (edn/parse-edn "[\"hello\"]"))))
  (testing "keywords are kept as :ns/name STRINGS, not Clojure keywords"
    (let [v (edn/parse-edn "[:node/scope :rel/kind]")]
      (is (= [":node/scope" ":rel/kind"] v))
      (is (every? string? v))
      (is (not (keyword? (first v))))))
  (testing "scientific-notation doubles (the seed uses 1.2e9 money amounts)"
    (is (= [1.2e9 8.0e8 5.0e6] (edn/parse-edn "[1.2e9 8.0e8 5.0e6]"))))
  (testing "commas are whitespace, ; comments are dropped"
    (is (= [1 2] (edn/parse-edn "[1, 2 ; trailing comment\n]")))))

(deftest nested-maps-and-vectors
  (testing "a map whose keys are :-strings and whose values nest a vector"
    (let [m (edn/parse-edn "{:graph {:name \"keizu-relations-v1\" :visibility :public} :nodes [{:node/id \"a\"} {:node/id \"b\"}]}")]
      (is (map? m))
      (is (= "keizu-relations-v1" (get-in m [":graph" ":name"])))
      (is (= ":public" (get-in m [":graph" ":visibility"])))
      (is (vector? (get m ":nodes")))
      (is (= 2 (count (get m ":nodes"))))
      (is (= "a" (get-in m [":nodes" 0 ":node/id"]))))))

(deftest string-escapes
  (testing "escaped quote + backslash unescape (Python _atom path)"
    (is (= ["a\"b"] (edn/parse-edn "[\"a\\\"b\"]")))
    (is (= ["a\\b"] (edn/parse-edn "[\"a\\\\b\"]")))))

;; ── the real seed: parse → assert structure (round-trip-ish) ──────────────────

(deftest real-seed-structure
  (testing "the committed :representative seed parses into the expected shape"
    (let [g (edn/load-edn seed-path)]
      (is (map? g) "top form is a map")
      ;; keyword-as-string keys (the fidelity invariant)
      (is (contains? g ":nodes"))
      (is (contains? g ":committees"))
      (is (contains? g ":rels"))
      (is (contains? g ":money"))
      (is (contains? g ":statements"))
      ;; nested vector of maps
      (let [nodes (get g ":nodes")]
        (is (vector? nodes))
        (is (pos? (count nodes)))
        (is (every? map? nodes))
        ;; every node carries a string :node/id and a :-string :node/scope
        (is (every? #(string? (get % ":node/id")) nodes))
        (is (every? #(str/starts-with? (get % ":node/scope") ":") nodes))
        ;; G1: a known public-org seat is present with the expected scope string
        (let [mof (first (filter #(= "jp-mof" (get % ":node/id")) nodes))]
          (is (some? mof))
          (is (= ":public-org" (get mof ":node/scope")))
          (is (vector? (get mof ":node/sources")))
          (is (= 1 (count (get mof ":node/sources"))))))
      ;; committees: members are a nested vector of id strings (≥1 nested vector)
      (let [committees (get g ":committees")
            fsc (first (filter #(= "jp-fiscal-system-council" (get % ":committee/id")) committees))]
        (is (some? fsc))
        (is (vector? (get fsc ":committee/members")))
        (is (= ["jp-fsc-chair" "jp-fsc-acad-1" "jp-fsc-biz-1"] (get fsc ":committee/members"))))
      ;; money: scientific-notation amounts come back as doubles
      (let [money (get g ":money")
            m1 (first (filter #(= "m-award-jp-1" (get % ":money/id")) money))]
        (is (some? m1))
        (is (= 1.2e9 (get m1 ":money/amount")))
        (is (= ":procurement-award" (get m1 ":money/kind")))
        (is (= "JPY" (get m1 ":money/currency"))))
      ;; rels carry ≥2 sources (G3) as a nested vector of strings
      (let [rels (get g ":rels")
            r1 (first rels)]
        (is (vector? (get r1 ":rel/sources")))
        (is (>= (count (get r1 ":rel/sources")) 2))))))

(deftest round-trip-ish
  (testing "re-printing parsed atoms and re-parsing yields the same structure"
    (let [src "[{:k \"v\" :n 42} [1 2 3] :a/b true nil]"
          parsed (edn/parse-edn src)]
      (is (= [{":k" "v" ":n" 42} [1 2 3] ":a/b" true nil] parsed)))))
