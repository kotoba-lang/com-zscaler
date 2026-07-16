(ns wakai.methods.pool
  "wakai 和会 — member-to-member mutual-aid pool: contribution validation, distribution
  validation, and pool-state aggregation (R0 reference implementation, ADR-2605263500).

  Pure functions matching the `com.etzhayyim.wakai.*` Lexicon record shapes exactly
  (`00-contracts/lexicons/com/etzhayyim/wakai/`).

    G3 NOT insurance          : every distribution record structurally carries
                               claimAdjudicated=false — a caller cannot construct an
                               adjudicated record even by mistake.
    G6 NOT investment         : every contribution record structurally carries
                               investmentReturnPromised=false; the pool aggregate is
                               usdc-stable-only with DeFi/token-speculation counts pinned
                               at 0 (Charter Rider §2(b)).
    G7 NOT underwriting       : every distribution record structurally carries
                               noPreExistingConditionExclusion=true.
    G8 voluntary+ability-scaled: contribution carries the member's self-attestation,
                               never a computed/required minimum.
    G9 community discernment  : a distribution requires >=3 community-discernment
                               attestations AND >=3 Council Lv6+ attestations — anything
                               short is REJECTED, never silently truncated or defaulted.

  House style: result maps stay string-keyed, matching the lexicon's camelCase field
  names 1:1 (AT-record / json shape); pure fns; stdlib only."
  (:require [clojure.string :as str]))

(def ^:private contribution-methods
  #{"usdc-base-l2-direct" "usdc-base-l2-erc4337-paymaster"
    "in-kind-attested" "labor-equivalent-attested"})

(def ^:private need-categories
  #{"health-event" "disability" "death-of-breadwinner" "unemployment"
    "disaster-emergency" "subsistence-gap" "child-education"
    "elder-care-gap" "other-community-discerned"})

(def ^:private distribution-methods
  #{"usdc-base-l2-direct" "in-kind-coordination" "labor-equivalent-coordination"
    "kazaori-emergency-dispatch" "cross-actor-routing"})

(defn validate-contribution
  "Validate + construct a `mutualAidContributionAttestation` record (G6+G8). Pure function.

  `investmentReturnPromised` is NOT a caller input — it is always `false` (G6 structural),
  matching the lexicon's `const false` guarantee at the code layer, not merely the schema."
  [{:keys [created-at contributor-pseudonym-did contribution-amount-encrypted-cid
           member-consent-cid contribution-method ability-scaled-attested]}]
  (when-not (and created-at (not= created-at ""))
    (throw (ex-info "contribution: created_at is required" {})))
  (when-not (and contributor-pseudonym-did (not= contributor-pseudonym-did ""))
    (throw (ex-info "contribution: contributor_pseudonym_did is required" {})))
  (when-not (and contribution-amount-encrypted-cid (not= contribution-amount-encrypted-cid ""))
    (throw (ex-info "contribution: contribution_amount_encrypted_cid is required" {})))
  (when-not (and member-consent-cid (not= member-consent-cid ""))
    (throw (ex-info "contribution: member_consent_cid is required" {})))
  (when-not (contains? contribution-methods contribution-method)
    (throw (ex-info (str "contribution: unknown contribution_method " (pr-str contribution-method)) {})))
  {"createdAt" created-at
   "contributorPseudonymDid" contributor-pseudonym-did
   "contributionAmountEncryptedCid" contribution-amount-encrypted-cid
   "memberConsentCid" member-consent-cid
   "investmentReturnPromised" false                    ; G6 — structural, not a caller input
   "abilityScaledAttested" (boolean ability-scaled-attested)  ; G8
   "contributionMethod" contribution-method})

(defn validate-distribution
  "Validate + construct a `mutualAidDistributionAttestation` record (G3+G7+G9). Pure function.

  Rejects when either attestation chain has fewer than 3 entries (G9) — no silent
  truncation, no default fill. `noPreExistingConditionExclusion` (G7) and
  `claimAdjudicated` (G3) are NOT caller inputs — always `true` / `false` respectively."
  [{:keys [created-at recipient-pseudonym-did need-attestation-cid need-category
           community-discernment-attestations council-attestations distribution-method]}]
  (when-not (and created-at (not= created-at ""))
    (throw (ex-info "distribution: created_at is required" {})))
  (when-not (and recipient-pseudonym-did (not= recipient-pseudonym-did ""))
    (throw (ex-info "distribution: recipient_pseudonym_did is required" {})))
  (when-not (and need-attestation-cid (not= need-attestation-cid ""))
    (throw (ex-info "distribution: need_attestation_cid is required" {})))
  (when-not (contains? need-categories need-category)
    (throw (ex-info (str "distribution: unknown need_category " (pr-str need-category)) {})))
  (when (< (count community-discernment-attestations) 3)
    (throw (ex-info "distribution: requires >= 3 community discernment attestations (G9)" {})))
  (when (< (count council-attestations) 3)
    (throw (ex-info "distribution: requires >= 3 Council Lv6+ attestations (G9)" {})))
  (when-not (contains? distribution-methods distribution-method)
    (throw (ex-info (str "distribution: unknown distribution_method " (pr-str distribution-method)) {})))
  {"createdAt" created-at
   "recipientPseudonymDid" recipient-pseudonym-did
   "needAttestationCid" need-attestation-cid
   "needCategory" need-category
   "communityDiscernmentAttestations" (vec community-discernment-attestations)
   "councilAttestations" (vec council-attestations)
   "noPreExistingConditionExclusion" true               ; G7 — structural, not a caller input
   "claimAdjudicated" false                             ; G3 — structural, not a caller input
   "distributionMethod" distribution-method})

(defn aggregate-pool-state
  "Aggregate a period's contributions + distributions into a `mutualAidPoolStateReport`.
  Pure function. NO individual member amounts appear in the result — aggregate only
  (member-by-amount linkage is PII; the encrypted per-member records are the SSoT).

  `contributions` = seq of `{:amount-usd-millicents int}`.
  `distributions` = seq of `{:amount-usd-millicents int :need-category string}`."
  [{:keys [report-period-start-utc report-period-end-utc contributions distributions
           council-attestations]}]
  (when (< (count council-attestations) 3)
    (throw (ex-info "pool_state: requires >= 3 Council Lv6+ attestations" {})))
  (let [total-contrib (reduce + 0 (map :amount-usd-millicents contributions))
        total-distrib (reduce + 0 (map :amount-usd-millicents distributions))
        by-category (->> distributions
                         (group-by :need-category)
                         (map (fn [[cat ds]]
                                {"needCategory" cat
                                 "distributionCount" (count ds)
                                 "totalAmountUsdMillicents" (reduce + 0 (map :amount-usd-millicents ds))}))
                         vec)]
    {"reportPeriodStartUtc" report-period-start-utc
     "reportPeriodEndUtc" report-period-end-utc
     "totalPoolBalanceUsdMillicents" (- total-contrib total-distrib)
     "totalContributionsUsdMillicents" total-contrib
     "totalDistributionsUsdMillicents" total-distrib
     "contributorCount" (count contributions)
     "distributionRecipientCount" (count distributions)
     "averageContributionUsdMillicents" (if (seq contributions)
                                          (long (/ total-contrib (count contributions)))
                                          0)
     "distributionsByNeedCategory" by-category
     "poolAssetClass" "usdc-stable-only"                 ; G6 — structural
     "defiYieldFarmingActiveCount" 0                     ; G6 — structural
     "tokenSpeculationActiveCount" 0                     ; G6 — structural
     "communityDiscernmentEventsCount" (count distributions)
     "councilAttestations" (vec council-attestations)}))

(defn solve
  "Cell entry — R0 is reference-only; LIVE pooling against real member funds is
  Council+operator gated (per ADR-2605263500's R0->R1 gate: Council Lv6+ >=3 baseline +
  the 4 pool cells wired + Public Fund backstop cross-linked)."
  [& _]
  (throw (ex-info (str "wakai R0: reference validation + aggregation only. Live "
                       "contribution/distribution against real member funds is "
                       "Council+operator gated.")
                  {})))
