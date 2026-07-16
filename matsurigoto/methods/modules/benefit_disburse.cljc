(ns matsurigoto.methods.modules.benefit-disburse
  "benefit_disburse.py — matsurigoto 政 `benefit-disburse` module (R0 reference implementation).

  Pure-function COFOG division 10 (social protection) entitlement ASSESSMENT — never a cash
  transfer. Spec basis (G2): the OpenG2P government-to-person benefit-registry pattern (the
  OSS-GovTech precedent matsurigoto's CLAUDE.md already cites alongside X-Road/MOSIP/OpenCRVS/
  DIGIT), generalized so it can express etzhayyim's OWN non-cash provision doctrine
  (ADR-2605301020 Basic High Income: imputed income [flow] + commons-asset access [stock],
  cash≡0 structural invariant) under principal A, or an adopting nation-state's ordinary G2P
  cash-benefit programme under principal B — same assessment shape, different `medium`.

    G1 no-operator-master-key : SERVER-HELD-AUTHORITY false; certificate returned UNSIGNED.
    G2 spec-derived-only      : OpenG2P G2P registry pattern + ADR-2605301020 non-cash doctrine.
    G6 anti-class            : this module assesses ONE claimant's entitlement at a time and
                               never aggregates, ranks, or compares across claimants
                               (ADR-2605261000 N6 / ADR-2605301020 §7 no-leaderboard).

  Structural cash≡0 proof (for principal A only — see note on `disbursement-medium` below):
  the medium enum used by an etzhayyim (principal A) deployment has exactly two values —
  :in-kind-service and :commons-asset-access — with no :cash case, so a caller cannot construct
  a cash entitlement even by mistake; the type itself forecloses it (mirrors ADR-2605301020's
  `cashStipendUsdMicros: const 0` field, moved from a runtime-checked value into the type).
  A principal-B (adopting nation-state) deployment MAY additionally declare :cash-transfer —
  ordinary G2P cash benefits are not unconstitutional for a state's own programme; only an
  etzhayyim (principal A) profile is restricted to the non-cash media (see `for-principal`).

  House style: result maps stay string-keyed (json.loads shapes); pure fns; stdlib only."
  (:require [clojure.string :as str]))

;; G1: this module holds NO signing authority and disburses nothing itself.
(def SERVER-HELD-AUTHORITY false)

;; COFOG division 10 groups this module can assess (10.8 R&D / 10.9 n.e.c. excluded — not
;; individually claimable benefit categories at the group level).
(def ^:private entitlement-categories
  #{"sickness-disability"   ; COFOG 10.1
    "old-age"                ; COFOG 10.2
    "survivors"              ; COFOG 10.3
    "family-children"        ; COFOG 10.4
    "unemployment"           ; COFOG 10.5
    "housing"                ; COFOG 10.6
    "social-exclusion"})     ; COFOG 10.7

;; principal A (etzhayyim itself) may express ONLY these two non-cash media (ADR-2605301020).
(def ^:private non-cash-media #{"in-kind-service" "commons-asset-access"})

;; principal B (an adopting nation-state's own G2P programme) may additionally use cash —
;; ordinary state cash-benefit programmes are not an etzhayyim constitutional matter.
(def ^:private all-media (conj non-cash-media "cash-transfer"))

(defn- media-for [for-principal]
  (case for-principal
    "sovereign-governance" non-cash-media
    "supplied-to-state" all-media
    (throw (ex-info (str "for_principal must be sovereign-governance or supplied-to-state, got "
                        (pr-str for-principal)) {}))))

(defn- unsigned-certificate
  [category claimant-did]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" "EntitlementCertificate"]
   "credentialSubject" {"id" claimant-did "category" category}
   "proof" nil                                      ; G1 — this module signs nothing
   "server_held_authority" SERVER-HELD-AUTHORITY    ; false
   "status" "assessed-unsigned"})

(defn assess-entitlement
  "Validate + construct an entitlement assessment. Pure function; never disburses anything
  itself (assessment only — G1 unsigned, live disbursement is Council+operator gated).

  `for-principal` selects which media are representable: \"sovereign-governance\" (principal A)
  is restricted to the two non-cash media (ADR-2605301020); \"supplied-to-state\" (principal
  B) may additionally use \"cash-transfer\" for its own ordinary G2P programme. Any other
  `medium` — including an attempt to pass \"cash-transfer\" under principal A — is rejected."
  [claimant-did category medium evidence-basis for-principal]
  (when-not (and claimant-did (not= claimant-did ""))
    (throw (ex-info "entitlement: claimant_did is required" {})))
  (when-not (contains? entitlement-categories category)
    (throw (ex-info (str "entitlement: unknown category " (pr-str category)) {})))
  (let [allowed (media-for for-principal)]
    (when-not (contains? allowed medium)
      (throw (ex-info (str "entitlement: medium must be one of " allowed
                          " for " for-principal ", got " (pr-str medium)) {}))))
  (when-not (and evidence-basis (not= evidence-basis ""))
    (throw (ex-info "entitlement: evidence_basis is required (G2 spec-derived-only)" {})))
  {"category" category
   "medium" medium
   "claimant_did" claimant-did
   "evidence_basis" evidence-basis
   "for_principal" for-principal
   "certificate" (unsigned-certificate category claimant-did)})

(defn compute-imputed-value
  "Market-equivalent valuation of in-kind/commons-access provision (ADR-2605301020 §4 valuation
  method). ACCOUNTING-ONLY — the result is never a transfer amount, only a transparency figure
  (median/percentile aggregate publication happens elsewhere, per-claimant here only)."
  [units-consumed unit-reference-price-usd-micros]
  (when (neg? units-consumed)
    (throw (ex-info "imputed_value: units_consumed must be >= 0" {})))
  (when (neg? unit-reference-price-usd-micros)
    (throw (ex-info "imputed_value: unit_reference_price_usd_micros must be >= 0" {})))
  {"units_consumed" units-consumed
   "unit_reference_price_usd_micros" unit-reference-price-usd-micros
   "total_value_usd_micros" (long (* units-consumed unit-reference-price-usd-micros))
   "accounting_only" true})

(defn solve
  "Cell entry — R0 is reference-only; a LIVE disbursement is Council+operator gated."
  [& _]
  (throw (ex-info (str "benefit-disburse R0: reference entitlement assessment only. Live "
                       "disbursement against a real benefit registry is Council+operator "
                       "gated (principal A: Council Lv7+; principal B: adopting state).")
                  {})))
