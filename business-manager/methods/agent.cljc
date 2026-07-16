(ns business-manager.methods.agent
  "business-manager — kotoba-native internal ERP actor.
  ADR-2606072000: append-only double-entry bookkeeping on the kotoba Datom log.

  Handlers:
    fiscal-year-of       JP fiscal year (Apr 1 - Mar 31) derivation
    is-balanced          double-entry balance check (G2)
    approval-status      G6 approval routing (derived, never caller-set)
    post-journal-entry   validate + stage a journal entry
    post-purchase-order  stage a purchase order
    authorize-posting    member-signed posting only (G4 no-server-key)
    trial-balance        Σdebit / Σcredit roll-up (must net to 0)
    post-invoice         stage an AP/AR invoice
    settle-invoice       mark a posted invoice paid (member-signed only)
    invoice-aging        open AP/AR totals + overdue list
    budget-vs-actual     budget allocation vs actual spend variance

  Hard invariants (structurally unrepresentable, not policy):
    G2  double-entry balanced — unbalanced entry → rejected
    G4  no-server-key        — only member signature posts
    G6  approval thresholds  — approved is DERIVED from amount
    G7  append-only          — postings carry appendOnly; no mutate/delete")

;; ---------------------------------------------------------------------------
;; Approval thresholds (G6), JPY minor units (1 JPY = 100 minor)
;; ---------------------------------------------------------------------------
(def JOURNAL-APPROVAL-MINOR (* 1000000 100))
(def PO-APPROVAL-MINOR (* 5000000 100))
(def INVOICE-APPROVAL-MINOR (* 1000000 100))
(def INVOICE-DIRECTIONS #{"payable" "receivable"})

;; ---------------------------------------------------------------------------
;; fiscal year (JP: Apr 1 - Mar 31)
;; ---------------------------------------------------------------------------
(defn fiscal-year-of
  "Return the JP fiscal-year label for an ISO date (YYYY-MM-DD).
  Apr–Dec → FY of that calendar year; Jan–Mar → FY of the previous year.
  e.g. 2027-03-15 → FY2026."
  [iso-date]
  (let [parts (clojure.string/split (str iso-date) #"-")
        y (Long/parseLong (nth parts 0 "0"))
        m (Long/parseLong (nth parts 1 "0"))]
    (str "FY" (if (>= m 4) y (dec y)))))

;; ---------------------------------------------------------------------------
;; double-entry balance (G2)
;; ---------------------------------------------------------------------------
(defn is-balanced
  "True iff Σ debitMinor == Σ creditMinor and there are ≥2 lines (a real double entry)."
  [lines]
  (and (>= (count lines) 2)
       (let [debit  (reduce + 0 (map #(long (get % "debitMinor"  0)) lines))
             credit (reduce + 0 (map #(long (get % "creditMinor" 0)) lines))]
         (and (= debit credit) (pos? debit)))))

;; ---------------------------------------------------------------------------
;; approval routing (G6) — `approved` is DERIVED, never caller-set
;; ---------------------------------------------------------------------------
(defn approval-status
  "Return \"approval-required\" if amount > threshold, else \"auto-approved\"."
  [amount-minor threshold-minor]
  (if (> (long amount-minor) (long threshold-minor))
    "approval-required"
    "auto-approved"))

;; ---------------------------------------------------------------------------
;; postings (G4 no-server-key, G7 append-only)
;; ---------------------------------------------------------------------------
(defn post-journal-entry
  "Validate + stage a journal entry. Rejects an unbalanced entry (G2). Derives
  `approved` from the entry magnitude (G6) and `fiscalYear` from the date.
  Produces an UNSIGNED posting that only the member can authorize (G4).
  Never mutates — append-only (G7)."
  [entry posted-at]
  (let [lines (get entry "lines" [])]
    (cond
      (not (is-balanced lines))
      {"state" "rejected"
       "reason" "entry does not balance: Σdebit ≠ Σcredit (G2)"}

      (let [pb (get entry "postedBy" "")]
        (or (nil? pb) (empty? (clojure.string/trim (str pb)))))
      {"state" "rejected"
       "reason" "missing member postedBy (G4)"}

      :else
      (let [amount (reduce + 0 (map #(long (get % "debitMinor" 0)) lines))]
        {"state"        "staged"
         "kind"         "journalEntry"
         "entryId"      (get entry "entryId")
         "lines"        lines
         "amountMinor"  amount
         "currency"     "JPY"
         "fiscalYear"   (fiscal-year-of posted-at)
         "approved"     (approval-status amount JOURNAL-APPROVAL-MINOR) ; G6 derived
         "postedBy"     (get entry "postedBy")
         "postedSig"    nil                                              ; G4: unsigned until member authorizes
         "appendOnly"   true}))))                                        ; G7

(defn post-purchase-order
  "Stage a purchase order; derive `approved` from the PO threshold (G6);
  member-signed (G4)."
  [po posted-at]
  (let [pb (get po "postedBy" "")]
    (if (or (nil? pb) (empty? (clojure.string/trim (str pb))))
      {"state" "rejected" "reason" "missing member postedBy (G4)"}
      (let [amount (long (get po "amountMinor" 0))]
        {"state"       "staged"
         "kind"        "purchaseOrder"
         "poId"        (get po "poId")
         "vendor"      (get po "vendor" "")
         "amountMinor" amount
         "currency"    "JPY"
         "items"       (get po "items" [])
         "fiscalYear"  (fiscal-year-of posted-at)
         "approved"    (approval-status amount PO-APPROVAL-MINOR) ; G6 derived
         "postedBy"    (get po "postedBy")
         "postedSig"   nil
         "appendOnly"  true}))))

(defn authorize-posting
  "Post a staged entry. ONLY a member-origin signature authorizes (G4 no-server-key);
  a server signature is refused. Posting is append-only (G7)."
  [posting signature]
  (cond
    (not= (get posting "state") "staged")
    (assoc posting "refused" true "reason" "posting is not in :staged state")

    (not= (get signature "origin") "member")
    (assoc posting
           "refused" true
           "reason" "only a member passkey/wallet signature posts to the ledger (G4 no-server-key)")

    :else
    (assoc posting "state" "posted" "postedSig" (get signature "ref"))))

;; ---------------------------------------------------------------------------
;; trial balance (must net to 0 across the ledger)
;; ---------------------------------------------------------------------------
(defn trial-balance
  "Roll up Σdebit / Σcredit across posted journal entries. A consistent ledger
  nets to 0 (G2 holds per-entry, so the total holds too)."
  [entries]
  (let [debit  (reduce + 0 (for [e entries
                                  l (get e "lines" [])]
                              (long (get l "debitMinor" 0))))
        credit (reduce + 0 (for [e entries
                                  l (get e "lines" [])]
                              (long (get l "creditMinor" 0))))]
    {"totalDebitMinor"  debit
     "totalCreditMinor" credit
     "balanced"         (= debit credit)}))

;; ---------------------------------------------------------------------------
;; invoice — AP (payable) / AR (receivable), member-signed, approval-routed (G6)
;; ---------------------------------------------------------------------------
(defn post-invoice
  "Stage an invoice. `direction` is payable (AP) or receivable (AR). `approved`
  is DERIVED from the amount (G6); `fiscalYear` from the date. Member-signed (G4).
  Starts :open."
  [inv posted-at]
  (let [direction (get inv "direction")]
    (cond
      (not (contains? INVOICE-DIRECTIONS direction))
      {"state"  "rejected"
       "reason" (str "direction must be one of " (pr-str (vec INVOICE-DIRECTIONS))
                     " (got " (pr-str direction) ")")}

      (let [pb (get inv "postedBy" "")]
        (or (nil? pb) (empty? (clojure.string/trim (str pb)))))
      {"state" "rejected" "reason" "missing member postedBy (G4)"}

      :else
      (let [amount (long (get inv "amountMinor" 0))]
        (if (<= amount 0)
          {"state" "rejected" "reason" "invoice amount must be positive"}
          {"state"         "staged"
           "kind"          "invoice"
           "invoiceId"     (get inv "invoiceId")
           "direction"     direction
           "party"         (get inv "party" "")
           "amountMinor"   amount
           "currency"      "JPY"
           "dueDate"       (get inv "dueDate" "")
           "fiscalYear"    (fiscal-year-of posted-at)
           "approved"      (approval-status amount INVOICE-APPROVAL-MINOR) ; G6 derived
           "paymentStatus" "open"
           "postedBy"      (get inv "postedBy")
           "postedSig"     nil
           "appendOnly"    true})))))

(defn settle-invoice
  "Mark a posted invoice paid. Member-signed only (G4 no-server-key). Append-only:
  emits a new :paid state (the original is never mutated, G7)."
  [invoice paid-at signature]
  (cond
    (not (#{"open" "overdue"} (get invoice "paymentStatus")))
    (assoc invoice "refused" true "reason" "invoice is not open/overdue")

    (not= (get signature "origin") "member")
    (assoc invoice
           "refused" true
           "reason" "only a member signature settles an invoice (G4 no-server-key)")

    :else
    (assoc invoice
           "paymentStatus" "paid"
           "paidAt"        paid-at
           "settledSig"    (get signature "ref"))))

(defn invoice-aging
  "Aging summary: open AP vs AR totals and which invoices are overdue
  (dueDate < as-of and still open). Pure date-string comparison (ISO dates sort lexically)."
  [invoices as-of-date]
  (loop [ap       0
         ar       0
         overdue  []
         invs     invoices]
    (if (empty? invs)
      {"apOpenMinor" ap "arOpenMinor" ar "overdue" overdue}
      (let [inv (first invs)]
        (if (= "open" (get inv "paymentStatus"))
          (let [amt      (long (get inv "amountMinor" 0))
                due      (get inv "dueDate" "")
                overdue? (and (seq due) (neg? (compare due as-of-date)))
                new-od   (if overdue? (conj overdue (get inv "invoiceId")) overdue)]
            (case (get inv "direction")
              "payable"    (recur (+ ap amt) ar       new-od (rest invs))
              "receivable" (recur ap          (+ ar amt) new-od (rest invs))
              (recur ap ar new-od (rest invs))))
          (recur ap ar overdue (rest invs)))))))

;; ---------------------------------------------------------------------------
;; budget allocation — budget vs actual / variance, over-budget flag
;; ---------------------------------------------------------------------------
(defn budget-vs-actual
  "Compare a budget allocation (account + fiscalYear + allocatedMinor) to actual
  spend on that account in that fiscal year (Σ debit on the account across posted
  entries). Returns spent / remaining / over flag. Over-budget is a flag, never
  a silent overwrite."
  [budget journal-entries]
  (let [account   (get budget "account")
        fy        (get budget "fiscalYear")
        allocated (long (get budget "allocatedMinor"))
        spent     (reduce + 0
                          (for [e journal-entries
                                :when (= fy (get e "fiscalYear"))
                                l (get e "lines" [])
                                :when (= account (get l "account"))]
                            (long (get l "debitMinor" 0))))]
    {"account"        account
     "fiscalYear"     fy
     "allocatedMinor" allocated
     "spentMinor"     spent
     "remainingMinor" (- allocated spent)
     "over"           (> spent allocated)}))
