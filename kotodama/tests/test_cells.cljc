(ns kotodama.tests.test-cells
  "kotodama — cell stub conformance tests (py→cljc port wave).

  All 13 cells in 20-actors/kotodama/cells/ are R0 scaffolds:
    - 6 tadori cells (ADR-2605301400) disabled until Council-gated R1
    - 7 tsukuroi cells (ADR-2605291500) disabled until Council-gated R1

  These tests pin that each .cljc stub:
    1. Loads without error
    2. Has a solve/1 function
    3. solve throws ex-info with the correct message substring
    4. The ex-info data carries :scaffold true"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [kotodama.cells.tadori-address-label.cell          :as tadori-address-label]
            [kotodama.cells.tadori-attribution-join.cell       :as tadori-attribution-join]
            [kotodama.cells.tadori-case-intake.cell            :as tadori-case-intake]
            [kotodama.cells.tadori-silen-tadori-review.cell    :as tadori-silen-tadori-review]
            [kotodama.cells.tadori-transparent-force-log.cell  :as tadori-transparent-force-log]
            [kotodama.cells.tadori-tx-trace.cell               :as tadori-tx-trace]
            [kotodama.cells.tsukuroi-charter-rider-scan.cell   :as tsukuroi-charter-rider-scan]
            [kotodama.cells.tsukuroi-closure-verification.cell :as tsukuroi-closure-verification]
            [kotodama.cells.tsukuroi-finding-intake.cell       :as tsukuroi-finding-intake]
            [kotodama.cells.tsukuroi-patch-synthesis.cell      :as tsukuroi-patch-synthesis]
            [kotodama.cells.tsukuroi-patch-validation.cell     :as tsukuroi-patch-validation]
            [kotodama.cells.tsukuroi-pr-submission.cell        :as tsukuroi-pr-submission]
            [kotodama.cells.tsukuroi-silen-tsukuroi-review.cell :as tsukuroi-silen-tsukuroi-review]))

;; ── helper ────────────────────────────────────────────────────────────────────

(defn- scaffold-throws?
  "Returns true iff (solve nil) throws an ex-info whose message contains `msg-substr`
   and whose data includes {:scaffold true}."
  [solve-fn msg-substr]
  (try
    (solve-fn nil)
    false
    (catch clojure.lang.ExceptionInfo e
      (and (clojure.string/includes? (ex-message e) msg-substr)
           (true? (:scaffold (ex-data e)))))
    (catch Exception _
      false)))

;; ── tadori cells (6) ─────────────────────────────────────────────────────────

(deftest tadori-address-label-stub
  (testing "tadori-address-label solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tadori-address-label/solve "tadori R0 scaffold"))))

(deftest tadori-attribution-join-stub
  (testing "tadori-attribution-join solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tadori-attribution-join/solve "tadori R0 scaffold"))))

(deftest tadori-case-intake-stub
  (testing "tadori-case-intake solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tadori-case-intake/solve "tadori R0 scaffold"))))

(deftest tadori-silen-tadori-review-stub
  (testing "tadori-silen-tadori-review solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tadori-silen-tadori-review/solve "tadori R0 scaffold"))))

(deftest tadori-transparent-force-log-stub
  (testing "tadori-transparent-force-log solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tadori-transparent-force-log/solve "tadori R0 scaffold"))))

(deftest tadori-tx-trace-stub
  (testing "tadori-tx-trace solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tadori-tx-trace/solve "tadori R0 scaffold"))))

;; ── tsukuroi cells (7) ───────────────────────────────────────────────────────

(deftest tsukuroi-charter-rider-scan-stub
  (testing "tsukuroi-charter-rider-scan solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-charter-rider-scan/solve "tsukuroi R0 scaffold"))))

(deftest tsukuroi-closure-verification-stub
  (testing "tsukuroi-closure-verification solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-closure-verification/solve "tsukuroi R0 scaffold"))))

(deftest tsukuroi-finding-intake-stub
  (testing "tsukuroi-finding-intake solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-finding-intake/solve "tsukuroi R0 scaffold"))))

(deftest tsukuroi-patch-synthesis-stub
  (testing "tsukuroi-patch-synthesis solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-patch-synthesis/solve "tsukuroi R0 scaffold"))))

(deftest tsukuroi-patch-validation-stub
  (testing "tsukuroi-patch-validation solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-patch-validation/solve "tsukuroi R0 scaffold"))))

(deftest tsukuroi-pr-submission-stub
  (testing "tsukuroi-pr-submission solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-pr-submission/solve "tsukuroi R0 scaffold"))))

(deftest tsukuroi-silen-tsukuroi-review-stub
  (testing "tsukuroi-silen-tsukuroi-review solve raises R0 scaffold ex-info"
    (is (scaffold-throws? tsukuroi-silen-tsukuroi-review/solve "tsukuroi R0 scaffold"))))

;; ── kotoba.datom smoke (already-ported, verify loads) ────────────────────────

(deftest kotoba-datom-loads
  (testing "kotoba.datom namespace loads and tx-cid is deterministic"
    (require 'kotoba.datom)
    (let [tx-cid @(resolve 'kotoba.datom/tx-cid)
          datoms [[:db/add "e1" :test/attr "value"]]]
      (is (fn? tx-cid) "kotoba.datom/tx-cid must resolve to a function")
      (is (string? (tx-cid datoms "")) "tx-cid must return a string")
      (is (= (tx-cid datoms "") (tx-cid datoms "")) "tx-cid must be deterministic"))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'kotodama.tests.test-cells)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
