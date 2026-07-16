(ns credits.methods.ledger-rails
  "credits -- ledger asset + payment-rail declarations (G8/G9). Derived directly from
  MIGRATION-TODO.md's substrate-boundary remediation list: strip Stripe/PayPal/fiat ->
  USDC + ERC-4337 + etzhayyim-tithe-router; strip 3rd-party ad/GA4/Meta Pixel. This ns
  is the structural single-source-of-truth the charter-gate test pins against -- no
  live payment integration exists yet, this slice is declaration + a pure predicate.")

(def native-asset
  "The ledger's own accounting unit -- never a fiat currency (G8)." "credit")

(def fiat-currency-codes
  "Fiat currencies that must NEVER be a representable settlement asset -- G8."
  #{"usd" "jpy" "eur" "gbp" "cny" "fiat"})

(def allowed-payment-rails
  "Settlement rails credits may eventually integrate with (R1+; none live in this
  slice) -- G9. MIGRATION-TODO.md target architecture only."
  #{"internal-ledger" "usdc-base-l2" "etzhayyim-tithe-router" "at-mst-anchor"})

(def banned-payment-vendors
  "Commercial payment-processor / ads-analytics vendors explicitly PROHIBITED by
  MIGRATION-TODO.md's substrate-boundary remediation list -- G9."
  #{"stripe" "paypal" "google-analytics" "ga4" "meta-pixel"})

(defn valid-payment-rail?
  "True iff `rail` is one of the declared allowed-payment-rails -- G9."
  [rail]
  (contains? allowed-payment-rails rail))
