(ns shomei.cells.test-cell-scaffolds
  "test_cell_scaffolds — every shomei cell solve raises at R0 (G11 outward-gated). ADR-2606072100.
  1:1 Clojure port of `cells/test_cell_scaffolds.py`. clojure.test;
  `expect_raises(contains=…)` → `shomei.methods._t/expect-raises`. The Python `__main__` demo
  (`run(\"cells\", CASES)`) is omitted (clojure.test drives the suite). The Python `CELLS`/`CASES`
  table over `<name>Cell` classes → per-cell deftests over each cell ns's `solve` fn."
  (:require [clojure.test :refer [deftest]]
            [shomei.methods._t :refer [expect-raises]]
            [shomei.cells.shomei-challenge.cell :as challenge]
            [shomei.cells.shomei-verify-claim.cell :as verify-claim]
            [shomei.cells.shomei-aggregate.cell :as aggregate]
            [shomei.cells.shomei-revoke.cell :as revoke]
            [shomei.cells.shomei-gov-attest.cell :as gov-attest]))

;; CELLS table: every cell .solve({}) raises with "R0 scaffold".
(deftest shomei-challenge-solve-raises
  (expect-raises "R0 scaffold" (challenge/solve {})))

(deftest shomei-verify-claim-solve-raises
  (expect-raises "R0 scaffold" (verify-claim/solve {})))

(deftest shomei-aggregate-solve-raises
  (expect-raises "R0 scaffold" (aggregate/solve {})))

(deftest shomei-revoke-solve-raises
  (expect-raises "R0 scaffold" (revoke/solve {})))

(deftest shomei-gov-attest-solve-raises
  (expect-raises "R0 scaffold" (gov-attest/solve {})))

;; gov_attest additionally mentions the Council gate.
(deftest gov-attest-mentions-council-gate
  (expect-raises "Council-gated" (gov-attest/solve {})))
