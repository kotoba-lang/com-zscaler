(ns credits.methods.spend-allocation
  "credits -- SpendCredits pure computation: fixed 10% public-fund allocation +
  destination resolution (CLAUDE.md § Purchase / Allocation Policy + § Allocation
  Destinations, G2/G3). Pure functions only -- no ledger write, no live db.")

(def public-fund-bps
  "Fixed public-fund allocation in basis points (1000 = 10%) of every spend -- G2."
  1000)

(def default-destination-id
  "Default allocation destination when a user has not set a preference -- G3."
  "public-fund:common")

(def allocation-destinations
  "The ONLY valid public-fund allocation destinations -- G3. Mirrors CLAUDE.md's
  Allocation Destinations table 1:1 (no destination invented here)."
  #{"public-fund:common"
    "public-fund:education-family"
    "public-fund:health-access"
    "public-fund:climate-resilience"})

(defn resolve-destination
  "nil/blank preference -> default-destination-id. Otherwise MUST be one of
  allocation-destinations -- G3."
  [destination-id]
  (let [d (or destination-id default-destination-id)]
    (when-not (contains? allocation-destinations d)
      (throw (ex-info "unknown allocation destination_id"
                       {:destination-id d :allowed allocation-destinations})))
    d))

(defn compute-spend-allocation
  "amount (positive) + optional destination-id -> {:amount :public-fund-amount
  :destination-id}. public-fund-amount = amount * 10% -- G2."
  [amount destination-id]
  (when-not (and (number? amount) (pos? amount))
    (throw (ex-info "amount must be a positive number" {:amount amount})))
  (let [dest (resolve-destination destination-id)
        pf (/ (* amount public-fund-bps) 10000)]
    {:amount amount :public-fund-amount pf :destination-id dest}))
