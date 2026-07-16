(ns toritate.methods.securities-donation
  "toritate 執帳 — donated-securities (stock/equity) intake engine: attestation +
  liquidation recording (R0 reference implementation, ADR-2607061800).

  Pure functions matching the `com.etzhayyim.give.stock.donation` Lexicon record shape
  exactly (`00-contracts/lexicons/com/etzhayyim/give/stock/donation.json`).

    No speculative holding : `heldAsEquityPosition` is structurally always `false` — not a
                            caller input. Donated securities are never retained as an
                            equity position (Charter Rider §2(b), the same discipline
                            already enforced on wakai's pool and this actor's own
                            imputed-income ledger).
    No new token           : liquidation proceeds re-enter the ordinary USDC/TitheRouter
                            rail (`record-liquidation` cross-links a
                            `com.etzhayyim.give.usdc.donation` AT-URI); no bespoke
                            on-chain asset is minted for the donated security.

  House style: result maps stay string-keyed (matching the Lexicon/AT-record camelCase
  shape); pure fns; stdlib only."
  (:require [clojure.string :as str]))

(def ^:private identifier-schemes #{"ticker" "cusip" "isin"})

(defn- unsigned? [n] (and (integer? n) (pos? n)))

(defn validate-securities-donation
  "Validate + construct a `com.etzhayyim.give.stock.donation` record. Pure function.
  `heldAsEquityPosition` is NEVER a caller input — always `false`; there is no key that
  can make this module represent a retained equity position."
  [{:keys [donor-did security-identifier security-identifier-scheme share-quantity
           fair-market-value-usd-micros valuation-date-utc brokerage-transfer-confirmation-cid
           donor-note-ref created-at]}]
  (when-not (and donor-did (not= donor-did ""))
    (throw (ex-info "securities_donation: donor_did is required" {})))
  (when-not (and security-identifier (not= security-identifier ""))
    (throw (ex-info "securities_donation: security_identifier is required" {})))
  (when-not (contains? identifier-schemes security-identifier-scheme)
    (throw (ex-info (str "securities_donation: security_identifier_scheme must be one of "
                        identifier-schemes ", got " (pr-str security-identifier-scheme)) {})))
  (when-not (unsigned? share-quantity)
    (throw (ex-info "securities_donation: share_quantity must be >= 1" {})))
  (when-not (unsigned? fair-market-value-usd-micros)
    (throw (ex-info "securities_donation: fair_market_value_usd_micros must be >= 1" {})))
  (when-not (and valuation-date-utc (not= valuation-date-utc ""))
    (throw (ex-info "securities_donation: valuation_date_utc is required" {})))
  (when-not (and brokerage-transfer-confirmation-cid (not= brokerage-transfer-confirmation-cid ""))
    (throw (ex-info "securities_donation: brokerage_transfer_confirmation_cid is required" {})))
  (cond-> {"donorDid" donor-did
           "securityIdentifier" security-identifier
           "securityIdentifierScheme" security-identifier-scheme
           "shareQuantity" share-quantity
           "fairMarketValueUsdMicros" fair-market-value-usd-micros
           "valuationDateUtc" valuation-date-utc
           "brokerageTransferConfirmationCid" brokerage-transfer-confirmation-cid
           "heldAsEquityPosition" false                 ; structural, not a caller input
           "createdAt" created-at}
    donor-note-ref (assoc "donorNoteRef" donor-note-ref)))

(defn record-liquidation
  "Attach liquidation results to a previously-validated donation record. Pure function
  (returns a NEW map; does not mutate the input). Rejects a donation that already
  declares itself an equity position (structurally impossible via
  `validate-securities-donation`, but checked here too since this fn may receive a
  hand-built map from elsewhere)."
  [donation {:keys [liquidated-at-utc liquidation-proceeds-usd-micros liquidation-donation-ref]}]
  (when (true? (get donation "heldAsEquityPosition"))
    (throw (ex-info "record_liquidation: a donation with heldAsEquityPosition=true cannot be recorded (structural invariant violated upstream)" {})))
  (when-not (and liquidated-at-utc (not= liquidated-at-utc ""))
    (throw (ex-info "record_liquidation: liquidated_at_utc is required" {})))
  (when-not (and (integer? liquidation-proceeds-usd-micros) (>= liquidation-proceeds-usd-micros 0))
    (throw (ex-info "record_liquidation: liquidation_proceeds_usd_micros must be >= 0" {})))
  (when-not (and liquidation-donation-ref (not= liquidation-donation-ref ""))
    (throw (ex-info "record_liquidation: liquidation_donation_ref is required (cross-link to the usdc.donation record)" {})))
  (assoc donation
         "liquidatedAtUtc" liquidated-at-utc
         "liquidationProceedsUsdMicros" liquidation-proceeds-usd-micros
         "liquidationDonationRef" liquidation-donation-ref))

(defn solve
  "Cell entry — R0 is reference-only; LIVE brokerage intake / liquidation instruction /
  fund movement is Council+operator gated (per ADR-2607061800; no real brokerage
  integration exists yet)."
  [& _]
  (throw (ex-info (str "toritate R0: reference validation + record construction only for "
                       "securities donations. Live brokerage intake, liquidation "
                       "instruction, and fund movement are Council+operator gated.")
                  {})))
