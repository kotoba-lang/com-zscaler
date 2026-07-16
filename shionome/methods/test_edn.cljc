(ns shionome.methods.test-edn
  "Cross-language oracle tests for shionome.methods.edn — the Clojure port of
  methods/_edn.py.

  No test_edn.py existed, so the expected values below were produced by running
  the REAL Python `_edn._parse(_edn._tokens(s))` and embedded verbatim — a genuine
  cross-language oracle, not a tautology.

  The fidelity invariant pinned here: keywords come back as \":ns/name\" STRINGS
  (never Clojure keywords), AND — the bug this suite was written to catch — quoted
  strings are decoded the way Python's `json.loads` does (\\n / \\t / \\uXXXX), not
  just \\\" / \\\\. shionome serializes strings with `json.dumps` (kotoba.py), so a
  lossy unescape would diverge the content-addressed-log CID across runtimes."
  (:require [clojure.test :refer [deftest is testing]]
            [shionome.methods.edn :as edn]))

;; ── primitive / atom-level parsing (mirror of Python _atom) ───────────────────

(deftest atoms-and-literals
  (testing "ints — oracle: [1 2 3]"
    (is (= [1 2 3] (edn/parse-edn "[1 2 3]"))))
  (testing "bool + nil — oracle: [true false null]"
    (is (= [true false nil] (edn/parse-edn "[true false nil]"))))
  (testing "floats + int mix — oracle: [1.5 -2.0 3]"
    (is (= [1.5 -2.0 3] (edn/parse-edn "[1.5 -2.0 3]"))))
  (testing "signed ints — oracle: [-7 3]  (Python int(\"+3\") == 3)"
    (is (= [-7 3] (edn/parse-edn "[-7 +3]")))))

(deftest keywords-stay-strings
  (testing "keywords kept as \":ns/name\" STRINGS, not Clojure keywords — oracle: [\":node/scope\" \":rel/kind\"]"
    (let [v (edn/parse-edn "[:node/scope :rel/kind]")]
      (is (= [":node/scope" ":rel/kind"] v))
      (is (every? string? v))
      (is (not (keyword? (first v)))))))

(deftest maps-and-nesting
  (testing "map keyword-keys stay strings — oracle: {\":a\" 1 \":b\" 2}"
    (is (= {":a" 1 ":b" 2} (edn/parse-edn "{:a 1 :b 2}"))))
  (testing "nested map/vector — oracle: {\":xs\" [1 2] \":y\" {\":z\" null}}"
    (is (= {":xs" [1 2] ":y" {":z" nil}} (edn/parse-edn "{:xs [1 2] :y {:z nil}}")))))

(deftest comments-and-commas
  (testing "leading ; comment is dropped — oracle: [1 \":k\" \"s\"]"
    (is (= [1 ":k" "s"] (edn/parse-edn "; hi\n[1 :k \"s\"]"))))
  (testing "commas are whitespace"
    (is (= [1 2 3] (edn/parse-edn "[1, 2, 3]")))))

;; ── the regression this suite exists for: JSON-faithful string unescape ───────
;; The old port handled only \" and \\, so \n / \t survived as literal backslash-n.
;; Python's _edn uses json.loads → real control chars. Oracle (json.dumps of input):
;;   "[\"line1\\nline2\\ttab\\\"q\\\"\"]"  ->  ["line1\nline2\ttab\"q\""]

(deftest json-faithful-string-unescape
  (testing "\\n and \\t decode to REAL newline/tab (not literal backslash) — the CID-roundtrip bug"
    (let [v (edn/parse-edn "[\"line1\\nline2\\ttab\\\"q\\\"\"]")]
      (is (= ["line1\nline2\ttab\"q\""] v))
      (is (= "line1\nline2\ttab\"q\"" (first v)))
      ;; explicitly: a real newline char is present, no literal backslash-n
      (is (= 1 (count (filter #(= \newline %) (first v)))))
      (is (not (clojure.string/includes? (first v) "\\n")))))
  (testing "\\uXXXX decodes — oracle: [\"café\"]"
    (is (= ["café"] (edn/parse-edn "[\"caf\\u00e9\"]"))))
  (testing "\\\\ and \\\" still round-trip"
    (is (= ["a\\b" "x\"y"] (edn/parse-edn "[\"a\\\\b\" \"x\\\"y\"]")))))

;; ── error path (mirror of Python next() StopIteration → our ex-info) ──────────

(deftest unexpected-end-throws
  (testing "truncated input raises rather than returning a partial"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (edn/parse-edn "[1 2")))))
