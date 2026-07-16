(ns silicon.kotoba.test-ingest-mcp
  "Representative tests for the shared `ingest_mcp.cljc` template (pure-fn logic).
  silicon 珪 (ADR-2605242500) has the richest non-empty seed (17 entities /
  ~150 datoms) so it is the parity anchor; the pure-fn proofs apply identically
  to all 19 ported actors (mechanical template copies).

  Covers:
    - strip-comments: line-comment removal, string-literal passthrough
    - count-top-level-entities: nested map counting, comment-stripped input
    - estimate-datoms: `{:` + ` :` heuristic (Python parity)
    - run (dry-run default): returns expected map keys + prints two lines

  PARITY verified vs Python ingest_mcp.py --dry-run on silicon/seed.edn:
    Python: parsed 17 entities (~150 datoms) from seed.edn
    Clojure: parsed 17 entities (~150 datoms) from seed.edn  (exact match)"
  (:require [clojure.test :refer [deftest is testing]]
            [silicon.kotoba.ingest-mcp :as mcp]))

;; ── strip-comments ────────────────────────────────────────────────────────────

(deftest strip-comments-removes-line-comment
  (is (= "abc " (mcp/strip-comments "abc ; trailing\n"))))

(deftest strip-comments-keeps-semicolon-inside-string
  (is (= "\"a;b\" " (mcp/strip-comments "\"a;b\" ; gone\n"))))

(deftest strip-comments-full-line-comment
  ;; the scanner skips up-to-but-not-including the newline, then the loop
  ;; recurs at the newline position; (inc newline-pos) steps past it — net
  ;; effect: the newline is consumed along with the comment
  (is (= "keep" (mcp/strip-comments ";; full line\nkeep"))))

;; ── count-top-level-entities ─────────────────────────────────────────────────

(deftest count-top-level-entities-simple
  (is (= 2 (mcp/count-top-level-entities "[ {:a 1} {:b 2} ]"))))

(deftest count-top-level-entities-nested-map-counts-once
  ;; nested { } inside a top-level entity counts as 1
  (is (= 1 (mcp/count-top-level-entities "[ {:a {:nested 2}} ]"))))

(deftest count-top-level-entities-empty-vector
  (is (= 0 (mcp/count-top-level-entities "[]"))))

(deftest count-top-level-entities-no-vector
  (is (= 0 (mcp/count-top-level-entities "{:a 1}"))))

(deftest count-top-level-entities-strips-comments-first
  ;; brace inside a comment must not be counted
  (is (= 1 (mcp/count-top-level-entities "[ ;; stray { brace\n {:a 1} ]"))))

;; ── estimate-datoms ───────────────────────────────────────────────────────────

(deftest estimate-datoms-single-attr
  ;; "{:" counts as 1; no " :" → 1 total
  (is (= 1 (mcp/estimate-datoms "{:a 1}"))))

(deftest estimate-datoms-two-attrs
  ;; "{:a" + " :b" = 2
  (is (= 2 (mcp/estimate-datoms "{:a 1 :b 2}"))))

(deftest estimate-datoms-ignores-comments
  ;; " :kw" inside a line comment must not be counted
  (is (= 1 (mcp/estimate-datoms "{:a 1} ; ignore :this"))))

;; ── seed parity against silicon/seed.edn ─────────────────────────────────────

(def ^:private seed-raw
  #?(:clj (slurp (clojure.java.io/file "20-actors/silicon/kotoba/seed.edn"))))

(deftest silicon-seed-entity-count-matches-python
  ;; Python --dry-run: 17 entities
  (is (= 17 (mcp/count-top-level-entities seed-raw))))

(deftest silicon-seed-datom-estimate-matches-python
  ;; Python --dry-run: ~150 datoms
  (is (= 150 (mcp/estimate-datoms seed-raw))))

;; ── run (dry-run default, no KOTOBA_TOKEN) ────────────────────────────────────

(deftest run-dry-run-returns-expected-keys
  (let [result (mcp/run {:dry-run? true})]
    (is (= :dry-run (:status result)))
    (is (= 17 (:entities result)))
    (is (= 150 (:datoms result)))))
