(ns credits.methods.purchase
  "credits -- PurchaseCredits pure computation. Fixed 30% platform-fee deduction on
  credit purchase (CLAUDE.md § Purchase / Allocation Policy, G1). Pure function only:
  no I/O, no live payment-gateway call, no wallet write -- that is later R1 wiring
  (see MIGRATION-TODO.md: Stripe/PayPal -> USDC + ERC-4337 boundary, not yet built).")

(def platform-fee-bps
  "Fixed platform fee in basis points (3000 = 30%). NOT user- or admin-adjustable
  per-transaction -- G1."
  3000)

(defn preview-purchase
  "gross-amount (positive number, credit-denominated) -> {:gross :fee :net}.
  fee = gross * 30%, net = gross - fee. Exact rational arithmetic (no float
  rounding drift) -- callers format for display as needed."
  [gross-amount]
  (when-not (and (number? gross-amount) (pos? gross-amount))
    (throw (ex-info "gross_amount must be a positive number" {:gross-amount gross-amount})))
  (let [fee (/ (* gross-amount platform-fee-bps) 10000)
        net (- gross-amount fee)]
    {:gross gross-amount :fee fee :net net}))
