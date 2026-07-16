(ns credits.methods.identity-gate
  "credits -- DID-bind identity gate (G10), the MIGRATION-TODO.md 'DID-bind
  authentication' item this actor previously left unwired. Closes the gap by
  requiring a shomei-verified DID before PurchaseCredits/SpendCredits proceed.

  THIN ADAPTER, not a reimplementation: this ns calls shomei's own public
  aggregation API (`shomei.methods.aggregate/aggregate`, ADR-2606072100)
  directly -- shomei is a sibling actor under the same `20-actors/` bb.edn
  `:paths` root, so a direct `:require` of its namespace is structural, not a
  new coupling mechanism. Precedent for this exact pattern already exists in
  this monorepo: `ainori.methods.pooled-route` directly requires
  `todoke.methods.last-mile` for a reused pure-computation core (ADR-2606071500)
  -- 封じ込め (containment) governs an actor's I/O/authority boundary (each
  actor's own state/credentials/side-effects stay behind its own governor); it
  does not forbid one actor's PURE function calling another's PURE function.
  credits does not gain shomei's I/O, keys, or write authority by this require
  -- it only gains a deterministic assurance-level computation.

  Identity Assurance Level SSoT stays in shomei (`shomei.methods.factors` /
  `shomei.methods.aggregate`) -- this ns never recomputes IAL itself.

  TODO (R1+, still out of scope this slice): today `active-verified-factors`
  is a caller-supplied SET (synthetic in tests) -- there is no live call to a
  running shomei_verify_claim/shomei_aggregate cell or shared kotoba-datomic
  substrate read. Wiring THAT (an actual cross-actor I/O call, cell-to-cell or
  via the kotoba Datom log) is real R1 work, gated by shomei's own G11
  (Council-gated proofs raise at R0) and is NOT done here."
  (:require [credits.methods.purchase :as purchase]
            [credits.methods.spend-allocation :as spend]
            [shomei.methods.aggregate :as shomei-agg]))

(def min-assurance-level
  "Minimum shomei Identity Assurance Level (shomei.methods.factors/assurance-level
  ladder: 0 did-only .. 4 government-verified) required before a purchase/spend
  proceeds -- G10. IAL >= 1 (self-attested, >=1 verified factor) is the credits-side
  requirement: ANY shomei-verified DID-bound factor, not multi-factor/covenant/gov
  tiers (raising the bar further is shomei's own policy to legislate, not credits')."
  1)

(defn identity-check
  "subject-did (string) + active-verified-factors (set of shomei factorKind
  strings, e.g. #{\"webauthn\" \"wallet-evm\"}) + now (epoch-millis long, the
  credential issuance timestamp shomei's aggregate requires) ->
  {:allowed :assurance-level :proof-of-personhood? :reason (when denied)}.

  Delegates the IAL computation to shomei.methods.aggregate/aggregate (never
  reimplements the ladder). An unknown/garbage factor string is silently
  dropped by shomei's own aggregate (mirrors shomei's own filtering), not an
  error here -- it just cannot raise the assurance level."
  [subject-did active-verified-factors now]
  (when-not (and (string? subject-did) (seq subject-did))
    (throw (ex-info "subject_did must be a non-blank string" {:subject-did subject-did})))
  (let [cred (shomei-agg/aggregate subject-did (or active-verified-factors #{}) :issued-at now)
        level (get cred "assuranceLevel")
        pop? (boolean (get cred "proofOfPersonhood"))]
    (if (>= level min-assurance-level)
      {:allowed true :assurance-level level :proof-of-personhood? pop?}
      {:allowed false
       :assurance-level level
       :proof-of-personhood? pop?
       :reason (str "identity-gate: DID " subject-did " shomei assuranceLevel=" level
                     " (" (shomei-agg/assurance-label level) "), requires >= "
                     min-assurance-level " (" (shomei-agg/assurance-label min-assurance-level) ")")})))

(defn require-identity!
  "Same as identity-check but THROWS ex-info (:type :credits/identity-gate-denied,
  ex-data carries the full identity-check result) when the gate denies -- the form
  gated-preview-purchase / gated-compute-spend-allocation call directly."
  [subject-did active-verified-factors now]
  (let [r (identity-check subject-did active-verified-factors now)]
    (when-not (:allowed r)
      (throw (ex-info (:reason r) (assoc r :type :credits/identity-gate-denied))))
    r))

(defn gated-preview-purchase
  "PurchaseCredits, IDENTITY-GATED (G10): requires shomei IAL >= min-assurance-level
  for subject-did (throws :credits/identity-gate-denied otherwise), THEN delegates
  to purchase/preview-purchase (G1, unchanged fee math) -- the gate composes in
  front of the existing pure fee computation, it does not alter it."
  [subject-did active-verified-factors now gross-amount]
  (let [gate (require-identity! subject-did active-verified-factors now)]
    (assoc (purchase/preview-purchase gross-amount)
           :subject-did subject-did
           :assurance-level (:assurance-level gate))))

(defn gated-compute-spend-allocation
  "SpendCredits, IDENTITY-GATED (G10): requires shomei IAL >= min-assurance-level
  for subject-did (throws :credits/identity-gate-denied otherwise), THEN delegates
  to spend/compute-spend-allocation (G2/G3, unchanged allocation math)."
  [subject-did active-verified-factors now amount destination-id]
  (let [gate (require-identity! subject-did active-verified-factors now)]
    (assoc (spend/compute-spend-allocation amount destination-id)
           :subject-did subject-did
           :assurance-level (:assurance-level gate))))
