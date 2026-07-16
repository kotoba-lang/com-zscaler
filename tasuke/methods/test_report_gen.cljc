(ns tasuke.methods.test-report-gen
  "Tests for 助 (tasuke) document generation — G3 member-authored, G1 free, G2 signature, G9 draft.
  1:1 port of `methods/test_report_gen.py` (pytest → clojure.test)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [tasuke.methods.report-gen :as rg]))

(def ^:private CASE
  {":case/id" "c1" ":case/subject" "did:web:etzhayyim.com:member:alice"
   ":case/scam-kind" ":unauthorized-transfer" ":case/loss-jpy" 480000
   ":case/narrative" "不正送金被害" ":case/occurred-at-text" "2026-06-03"
   ":case/timeline" ["A" "B"] ":case/loss-breakdown" [{":label" "x" ":jpy" 480000}]})

(def ^:private EV
  [{":evidence/id" "e1" ":evidence/case" "c1" ":evidence/kind" ":screenshot"
    ":evidence/envelope-ref" "ipfs://bafyX" ":evidence/bytes" "abc" ":evidence/captured-at" 1}])

(def ^:private ALL
  [(rg/damage-report CASE) (rg/incident-statement CASE) (rg/damage-calculation CASE)
   (rg/evidence-index-doc CASE EV) (rg/bank-freeze-request CASE)
   (rg/platform-request CASE) (rg/recovery-plan CASE :service "Google")])

;; ── G3 every generated document is member-authored, never police/official ─────
(deftest test-all-docs-member-authored
  (doseq [d ALL]
    (is (= ":member" (get d ":doc/authored-by")))
    (is (not (contains? #{":police" ":official" ":server"} (get d ":doc/authored-by"))))))

(deftest test-assert-member-authored-passes-for-all
  (doseq [d ALL]
    (is (nil? (rg/assert-member-authored d)))))   ;; must not raise

(deftest test-assert-rejects-police-authored
  (let [bad (assoc (rg/damage-report CASE) ":doc/authored-by" ":police")]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G3"
          (rg/assert-member-authored bad)))))

;; ── G2/G7 every doc needs the member's signature ─────────────────────────────
(deftest test-all-docs-need-member-signature
  (doseq [d ALL]
    (is (= true (get d ":doc/needs-member-signature")))))

;; ── G1 every doc is free ─────────────────────────────────────────────────────
(deftest test-all-docs-free
  (doseq [d ALL]
    (is (= 0 (get d ":doc/support-cost-jpy")))))

;; ── G9 every doc is draft-only at R0 ─────────────────────────────────────────
(deftest test-all-docs-unpublished
  (doseq [d ALL]
    (is (= false (get d ":doc/published")))))

;; ── content sanity ───────────────────────────────────────────────────────────
(deftest test-damage-report-has-signature-line-and-loss
  (let [body (get (rg/damage-report CASE) ":doc/body")]
    (is (and (str/includes? body "被 害 届") (str/includes? body "480,000") (str/includes? body "署名")))))

(deftest test-bank-request-cites-legal-basis
  (let [d (rg/bank-freeze-request CASE)]
    (is (str/includes? (get d ":doc/body") "振り込め詐欺救済法"))
    (is (str/starts-with? (get d "legal_basis" "") "振り込め詐欺救済法"))))

(deftest test-recovery-plan-is-self-submit
  (let [d (rg/recovery-plan CASE :service "LINE")]
    (is (= ":self-submit" (get d "support_role")))
    (is (and (get d "steps") (str/includes? (get d ":doc/body") "代理ログイン")))))

(deftest test-damage-calculation-sums-breakdown
  (let [d (rg/damage-calculation {":case/id" "c1"
                                  ":case/loss-breakdown" [{":label" "a" ":jpy" 100}
                                                          {":label" "b" ":jpy" 250}]})]
    (is (= 350 (get d "total_jpy")))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-report-gen)))
