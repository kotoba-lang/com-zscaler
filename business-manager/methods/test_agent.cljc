(ns business-manager.methods.test-agent
  "business-manager — test harness (clojure.test; no kotoba host needed).

  Verifies the structural invariants of ADR-2606072000 (faithful port of test_agent.py):
    G2 double-entry-balanced — unbalanced entry rejected; balanced entry staged
    G6 approval-thresholds  — `approved` derived from amount (journal >1M JPY, PO >5M JPY)
    G4 no-server-key        — only a member signature posts
    G7 append-only          — postings carry appendOnly; trial balance nets to 0
    fiscal year             — JP Apr 1 - Mar 31 derivation"
  (:require [clojure.test :refer [deftest is testing]]
            [business-manager.methods.agent :as agent]))

;; ---------------------------------------------------------------------------
;; helpers (matching Python _balanced_entry / _inv)
;; ---------------------------------------------------------------------------
(defn- balanced-entry
  ([debit credit]
   (balanced-entry debit credit {}))
  ([debit credit extra]
   (merge {"entryId" "je-1"
           "postedBy" "did:plc:cfo"
           "lines" [{"account" "5000" "debitMinor" debit "creditMinor" 0}
                    {"account" "1000" "debitMinor" 0 "creditMinor" credit}]}
          extra)))

(defn- inv
  ([]
   (inv {}))
  ([extra]
   (merge {"invoiceId" "inv-1"
           "direction" "payable"
           "party" "mitsuho"
           "amountMinor" (* 500000 100)
           "dueDate" "2026-06-30"
           "postedBy" "did:plc:ap"}
          extra)))

;; ---------------------------------------------------------------------------
;; FiscalYear
;; ---------------------------------------------------------------------------
(deftest test-april-starts-new-fy
  (is (= "FY2026" (agent/fiscal-year-of "2026-04-01"))))

(deftest test-march-is-prior-fy
  (is (= "FY2026" (agent/fiscal-year-of "2027-03-31"))))

;; ---------------------------------------------------------------------------
;; DoubleEntry
;; ---------------------------------------------------------------------------
(deftest test-balanced-true
  (is (true? (agent/is-balanced
              [{"debitMinor" 500 "creditMinor" 0}
               {"debitMinor" 0 "creditMinor" 500}]))))

(deftest test-unbalanced-false
  (is (false? (agent/is-balanced
               [{"debitMinor" 500 "creditMinor" 0}
                {"debitMinor" 0 "creditMinor" 400}]))))

(deftest test-single-line-false
  (is (false? (agent/is-balanced [{"debitMinor" 500 "creditMinor" 0}]))))

(deftest test-unbalanced-entry-rejected
  (let [e   (balanced-entry 50000000 40000000)   ; mismatched
        out (agent/post-journal-entry e "2026-05-01")]
    (is (= "rejected" (get out "state")))
    (is (clojure.string/includes? (get out "reason") "G2"))))

;; ---------------------------------------------------------------------------
;; Approval
;; ---------------------------------------------------------------------------
(deftest test-journal-auto-approved-below-1m
  (let [e   (balanced-entry (* 500000 100) (* 500000 100))   ; 500k JPY
        out (agent/post-journal-entry e "2026-05-01")]
    (is (= "staged" (get out "state")))
    (is (= "auto-approved" (get out "approved")))))

(deftest test-journal-approval-required-above-1m
  (let [e   (balanced-entry (* 2000000 100) (* 2000000 100)) ; 2M JPY
        out (agent/post-journal-entry e "2026-05-01")]
    (is (= "approval-required" (get out "approved")))))

(deftest test-caller-cannot-self-approve
  ;; approved is derived; any caller-supplied "approved" is ignored/overwritten
  (let [e   (balanced-entry (* 2000000 100) (* 2000000 100) {"approved" "auto-approved"})
        out (agent/post-journal-entry e "2026-05-01")]
    (is (= "approval-required" (get out "approved")))))   ; G6 derived, not trusted

(deftest test-po-threshold-5m
  (let [below (agent/post-purchase-order
               {"poId" "po1" "vendor" "mitsuho" "amountMinor" (* 4000000 100) "postedBy" "did:plc:buyer"}
               "2026-05-01")
        above (agent/post-purchase-order
               {"poId" "po2" "vendor" "x" "amountMinor" (* 6000000 100) "postedBy" "did:plc:buyer"}
               "2026-05-01")]
    (is (= "auto-approved"     (get below "approved")))
    (is (= "approval-required" (get above "approved")))))

;; ---------------------------------------------------------------------------
;; Posting
;; ---------------------------------------------------------------------------
(deftest test-member-signature-posts
  (let [staged (agent/post-journal-entry (balanced-entry 100000 100000) "2026-05-01")
        out    (agent/authorize-posting staged {"origin" "member" "ref" "sig-1"})]
    (is (= "posted" (get out "state")))
    (is (= "sig-1"  (get out "postedSig")))))

(deftest test-server-signature-refused
  (let [staged (agent/post-journal-entry (balanced-entry 100000 100000) "2026-05-01")
        out    (agent/authorize-posting staged {"origin" "server" "ref" "x"})]
    (is (true? (get out "refused")))
    (is (clojure.string/includes? (get out "reason") "G4"))))

(deftest test-missing-poster-rejected
  (let [e (balanced-entry 100 100 {"postedBy" ""})]
    (is (= "rejected" (get (agent/post-journal-entry e "2026-05-01") "state")))))

(deftest test-append-only-flag
  (let [staged (agent/post-journal-entry (balanced-entry 100000 100000) "2026-05-01")]
    (is (true? (get staged "appendOnly")))))

;; ---------------------------------------------------------------------------
;; TrialBalance
;; ---------------------------------------------------------------------------
(deftest test-ledger-nets-to-zero
  (let [es [(balanced-entry 100 100) (balanced-entry 250 250)]
        tb (agent/trial-balance es)]
    (is (true? (get tb "balanced")))
    (is (= (get tb "totalDebitMinor") (get tb "totalCreditMinor")))))

;; ---------------------------------------------------------------------------
;; Invoice
;; ---------------------------------------------------------------------------
(deftest test-payable-staged-auto-approved
  (let [out (agent/post-invoice (inv) "2026-05-01")]
    (is (= "staged"        (get out "state")))
    (is (= "payable"       (get out "direction")))
    (is (= "auto-approved" (get out "approved")))
    (is (= "open"          (get out "paymentStatus")))))

(deftest test-large-invoice-approval-required
  (let [out (agent/post-invoice (inv {"amountMinor" (* 2000000 100)}) "2026-05-01")]
    (is (= "approval-required" (get out "approved")))))

(deftest test-bad-direction-rejected
  (is (= "rejected" (get (agent/post-invoice (inv {"direction" "both"}) "2026-05-01") "state"))))

(deftest test-nonpositive-rejected
  (is (= "rejected" (get (agent/post-invoice (inv {"amountMinor" 0}) "2026-05-01") "state"))))

(deftest test-settle-member-only
  (let [staged (agent/post-invoice (inv) "2026-05-01")
        srv    (agent/settle-invoice staged "2026-06-01" {"origin" "server" "ref" "x"})
        mem    (agent/settle-invoice staged "2026-06-01" {"origin" "member" "ref" "s"})]
    (is (true? (get srv "refused")))
    (is (= "paid"       (get mem "paymentStatus")))
    (is (= "2026-06-01" (get mem "paidAt")))))

(deftest test-aging-splits-ap-ar-and-overdue
  (let [invs [(agent/post-invoice (inv {"invoiceId" "ap1"
                                       "direction" "payable"
                                       "amountMinor" 100
                                       "dueDate" "2026-01-01"}) "2026-05-01")
              (agent/post-invoice (inv {"invoiceId" "ar1"
                                       "direction" "receivable"
                                       "amountMinor" 250
                                       "dueDate" "2026-12-31"}) "2026-05-01")]
        out  (agent/invoice-aging invs "2026-06-07")]
    (is (= 100 (get out "apOpenMinor")))
    (is (= 250 (get out "arOpenMinor")))
    (is (some #(= "ap1" %) (get out "overdue")))      ; due 2026-01-01 < as-of
    (is (not (some #(= "ar1" %) (get out "overdue")))))) ; due 2026-12-31 > as-of

;; ---------------------------------------------------------------------------
;; Budget
;; ---------------------------------------------------------------------------
(deftest test-budget-vs-actual-variance
  (let [budget  {"account" "5000" "fiscalYear" "FY2026" "allocatedMinor" 1000000}
        entries [{"fiscalYear" "FY2026" "lines" [{"account" "5000" "debitMinor" 300000 "creditMinor" 0}]}
                 {"fiscalYear" "FY2026" "lines" [{"account" "5000" "debitMinor" 200000 "creditMinor" 0}]}
                 {"fiscalYear" "FY2025" "lines" [{"account" "5000" "debitMinor" 999 "creditMinor" 0}]}]  ; other FY ignored
        out     (agent/budget-vs-actual budget entries)]
    (is (= 500000 (get out "spentMinor")))
    (is (= 500000 (get out "remainingMinor")))
    (is (false? (get out "over")))))

(deftest test-over-budget-flagged
  (let [budget  {"account" "5000" "fiscalYear" "FY2026" "allocatedMinor" 100}
        entries [{"fiscalYear" "FY2026" "lines" [{"account" "5000" "debitMinor" 250 "creditMinor" 0}]}]
        out     (agent/budget-vs-actual budget entries)]
    (is (true? (get out "over")))
    (is (= -150 (get out "remainingMinor")))))
