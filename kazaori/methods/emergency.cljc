(ns kazaori.methods.emergency
  "kazaori 風折 — civilian disaster-response engine: emergency declaration, time-bounded
  carve-out activation, supply dispatch, evacuation check-in, and post-emergency review
  (R0 reference implementation, ADR-2605263200).

  Pure functions matching the `com.etzhayyim.kazaori.*` Lexicon record shapes exactly
  (`00-contracts/lexicons/com/etzhayyim/kazaori/`).

    G5 civilian-only    : `civilianOnlyAttested` is structurally always true — not a
                         caller input. This module never represents or coordinates armed
                         force action (ADR-2605192315 keeps that a separate authority).
    G6 no surveillance  : `checkInMethod` excludes every surveillance-derived value
                         (aerial-drone-detection / facial-recognition-match /
                         bluetooth-beacon-tracking) — opt-in self-attestation only.
    G8 time-bounded carve-out : `activate-carve-out` REJECTS an `autoRevokeAtUtc` later
                         than the emergency's own `autoLiftAtUtc` — a carve-out cannot
                         outlive the emergency that authorized it.
    G9 Sphere Standards : `silen-kazaori-review` requires all 5 sector attestations
                         (shelter/water/food/health/protection).
    G10 Council supermajority : `declare-emergency` / `activate-carve-out` reject fewer
                         than 4 Council attestations.

  All timestamps are ISO-8601 UTC instants; date arithmetic uses `java.time.Instant`
  (elapsed-seconds, calendar-agnostic — JVM-only, isolated behind #?(:clj ...)).
  House style: result maps stay string-keyed (matching the Lexicon/AT-record camelCase
  shape); pure fns."
  )

(def ^:private declaration-categories
  #{"earthquake" "typhoon-hurricane" "flood" "wildfire" "pandemic-biological"
    "power-outage-extended" "water-shortage" "food-shortage" "building-damage-mass"
    "civil-unrest-non-military" "other"})

(def ^:private checkin-methods
  ;; G6: surveillance-derived methods deliberately excluded (aerial-drone-detection /
  ;; facial-recognition-match / bluetooth-beacon-tracking are NOT in this set).
  #{"self-mobile-app" "self-paper-form-attested" "self-phone-call-attested"
    "family-member-on-behalf-with-prior-consent" "guardian-on-behalf-for-minor-or-incapacitated"})

(def ^:private dispatch-categories
  #{"potable-water-closed-loop-container" "potable-water-single-use-carve-out"
    "food-reserve-stock" "food-mutual-aid" "shelter-material" "medical-supply"
    "power-battery-redirection" "other"})

(def ^:private supply-sources
  #{"mizuho-water-source" "mitsuho-reserve-stock" "mitsuho-mutual-aid" "iyashi-stockpile"
    "yakushi-stockpile" "hikari-grid-edge-battery" "tatekata-shelter-material"
    "external-donation-attested"})

(def ^:private unit-codes #{"liters" "kg" "meal-units" "kwh" "shelter-spaces" "medical-doses"})

(defn- plus-days [iso-instant days]
  #?(:clj (str (.plusSeconds (java.time.Instant/parse iso-instant) (* (long days) 86400)))
     :cljs (throw (ex-info "date arithmetic requires JVM java.time" {}))))

(defn- instant-leq [a b]
  #?(:clj (not (.isAfter (java.time.Instant/parse a) (java.time.Instant/parse b)))
     :cljs (throw (ex-info "date arithmetic requires JVM java.time" {}))))

(defn declare-emergency
  "Validate + construct an `emergencyDeclarationAttestation` (G5+G8+G10). Pure function.
  `autoLiftAtUtc` is COMPUTED as `declaredStartUtc + initialDurationDays` — never a caller
  input — so the two cannot silently disagree."
  [{:keys [created-at declaration-category declared-scope declared-start-utc
           initial-duration-days council-attestations attesting-did
           sphere-standards-baseline-cid]}]
  (when-not (contains? declaration-categories declaration-category)
    (throw (ex-info (str "declaration: unknown category " (pr-str declaration-category)) {})))
  (when (empty? declared-scope)
    (throw (ex-info "declaration: declared_scope must be non-empty" {})))
  (when (or (< initial-duration-days 1) (> initial-duration-days 60))
    (throw (ex-info "declaration: initial_duration_days must be in [1,60] (G8)" {})))
  (when (< (count council-attestations) 4)
    (throw (ex-info "declaration: requires >= 4 Council Lv6+ attestations (G10)" {})))
  (cond-> {"createdAt" created-at
           "declarationCategory" declaration-category
           "declaredScope" (vec declared-scope)
           "declaredStartUtc" declared-start-utc
           "initialDurationDays" initial-duration-days
           "autoLiftAtUtc" (plus-days declared-start-utc initial-duration-days)
           "councilAttestations" (vec council-attestations)
           "civilianOnlyAttested" true               ; G5 — structural, not a caller input
           "attestingDid" attesting-did}
    sphere-standards-baseline-cid (assoc "sphereStandardsBaselineCid" sphere-standards-baseline-cid)))

(defn activate-carve-out
  "Validate + construct an `emergencyCarveOutLog` (G8+G10). Pure function. REJECTS an
  `auto-revoke-at-utc` later than the parent emergency's own `emergency-auto-lift-at-utc`
  — a carve-out cannot outlive the emergency that authorized it."
  [{:keys [created-at emergency-declaration-cid emergency-auto-lift-at-utc gate-carved
           gate-category-description carve-out-justification-cid council-attestations
           activated-at-utc auto-revoke-at-utc carve-out-scope attesting-did]}]
  (when-not (and gate-carved (not= gate-carved ""))
    (throw (ex-info "carve_out: gate_carved is required" {})))
  (when-not (and carve-out-justification-cid (not= carve-out-justification-cid ""))
    (throw (ex-info "carve_out: carve_out_justification_cid is required" {})))
  (when (< (count council-attestations) 4)
    (throw (ex-info "carve_out: requires >= 4 Council Lv6+ attestations (G8/G10)" {})))
  (when-not (instant-leq auto-revoke-at-utc emergency-auto-lift-at-utc)
    (throw (ex-info "carve_out: auto_revoke_at_utc must be <= the emergency's auto_lift_at_utc (G8)" {})))
  (cond-> {"createdAt" created-at
           "emergencyDeclarationCid" emergency-declaration-cid
           "gateCarved" gate-carved
           "carveOutJustificationCid" carve-out-justification-cid
           "councilAttestations" (vec council-attestations)
           "activatedAtUtc" activated-at-utc
           "autoRevokeAtUtc" auto-revoke-at-utc
           "carveOutScope" (or carve-out-scope {})
           "attestingDid" attesting-did}
    gate-category-description (assoc "gateCategoryDescription" gate-category-description)))

(defn dispatch-supply
  "Validate + construct an `emergencySupplyDispatch` (cross-actor mizuho/mitsuho/etc.).
  Pure function."
  [{:keys [created-at emergency-declaration-cid dispatch-category dispatch-destination-cid
           supply-source supply-source-record-cid quantity-integer unit-code
           carve-out-citations attesting-cell-did]}]
  (when-not (contains? dispatch-categories dispatch-category)
    (throw (ex-info (str "dispatch: unknown dispatch_category " (pr-str dispatch-category)) {})))
  (when-not (contains? supply-sources supply-source)
    (throw (ex-info (str "dispatch: unknown supply_source " (pr-str supply-source)) {})))
  (when-not (contains? unit-codes unit-code)
    (throw (ex-info (str "dispatch: unknown unit_code " (pr-str unit-code)) {})))
  (when (< quantity-integer 1)
    (throw (ex-info "dispatch: quantity_integer must be >= 1" {})))
  (cond-> {"createdAt" created-at
           "emergencyDeclarationCid" emergency-declaration-cid
           "dispatchCategory" dispatch-category
           "dispatchDestinationCid" dispatch-destination-cid
           "supplySource" supply-source
           "quantityInteger" quantity-integer
           "unitCode" unit-code
           "attestingCellDid" attesting-cell-did}
    supply-source-record-cid (assoc "supplySourceRecordCid" supply-source-record-cid)
    (seq carve-out-citations) (assoc "carveOutCitations" (vec carve-out-citations))))

(defn record-evacuation-checkin
  "Validate + construct an `evacuationCheckIn` (G6: opt-in, encrypted, member-signed).
  Pure function. `checkInMethod` structurally excludes every surveillance-derived value."
  [{:keys [created-at emergency-declaration-cid check-in-pseudonym-did safe-site-cid
           encrypted-payload-cid member-signature check-in-method vulnerable-population-flag]}]
  (when-not (and encrypted-payload-cid (not= encrypted-payload-cid ""))
    (throw (ex-info "checkin: encrypted_payload_cid is required (G6)" {})))
  (when-not (and member-signature (not= member-signature ""))
    (throw (ex-info "checkin: member_signature is required (G6)" {})))
  (when-not (contains? checkin-methods check-in-method)
    (throw (ex-info (str "checkin: check_in_method must be a self/consented method (G6), got "
                        (pr-str check-in-method)) {})))
  {"createdAt" created-at
   "emergencyDeclarationCid" emergency-declaration-cid
   "checkInPseudonymDid" check-in-pseudonym-did
   "safeSiteCid" safe-site-cid
   "encryptedPayloadCid" encrypted-payload-cid
   "memberSignature" member-signature
   "checkInMethod" check-in-method
   "vulnerablePopulationFlag" (boolean vulnerable-population-flag)})

(defn silen-kazaori-review
  "Validate + construct a `silenKazaoriReview` post-emergency record (G4+G5+G6+G9+G12).
  Pure function. REJECTS a `completed-at-utc` more than 90 days after `lifted-at-utc` —
  the timeliness invariant is VERIFIED here, never merely asserted."
  [{:keys [created-at emergency-declaration-cid lifted-at-utc completed-at-utc
           sphere-compliance carve-out-audit-entries council-attestations]}]
  (when (< (count council-attestations) 3)
    (throw (ex-info "review: requires >= 3 Council Lv6+ attestations" {})))
  (doseq [field ["shelterAttested" "waterAttested" "foodAttested" "healthAttested" "protectionAttested"]]
    (when-not (contains? sphere-compliance field)
      (throw (ex-info (str "review: sphere_compliance must attest " field " (G9)") {}))))
  (when-not (instant-leq completed-at-utc (plus-days lifted-at-utc 90))
    (throw (ex-info "review: completed_at_utc must be within 90 days of lifted_at_utc" {})))
  {"createdAt" created-at
   "emergencyDeclarationCid" emergency-declaration-cid
   "reviewCompletedWithin90DaysOfLifting" true                        ; verified above
   "sphereCompliance" sphere-compliance
   "carveOutAuditEntries" (vec carve-out-audit-entries)
   "surveillancePenetrationPct" 0                                     ; G6 — structural
   "civilianOnlyCompliance" true                                      ; G5 — structural
   "commercialDisasterMgmtSoftwarePenetrationPct" 0                   ; G4 — structural
   "responderVocationFlowCompliantRatioPctIntegerHundredths" 10000    ; G12 — structural
   "councilAttestations" (vec council-attestations)})

(defn solve
  "Cell entry — R0 is reference-only; LIVE emergency declaration / carve-out activation /
  supply dispatch is Council+operator gated (per ADR-2605263200's R0->R1 gate)."
  [& _]
  (throw (ex-info (str "kazaori R0: reference validation + construction only. Live "
                       "emergency declaration / carve-out activation / dispatch is "
                       "Council+operator gated.")
                  {})))
