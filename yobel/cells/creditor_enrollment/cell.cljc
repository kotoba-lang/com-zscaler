(ns yobel.cells.creditor-enrollment.cell
  "CreditorEnrollmentCell — Pregel cell verifying creditor opt-in + historical-record-only invariant.

  (Clojure port of cell.py — langgraph-clj.)

  Per ADR-2605201800 §Decision + Charter Rider v2 §2(b) one-way enforcement.

  Trigger: enrollCreditor XRPC request scoped to a `status=active` rite
  Effect:
    - Validate input (debts[] cardinality, originationDate)
    - Verify creditor Council standing (SBT Lv1+ or non-aligned with warning)
    - Recover EIP-712 signedConsent → assert signer matches creditorDid ERC725 keystore
    - Historical-record-only gate: all debts[].originationDate < rite.effectiveDate
    - Instrument safety gate: reject liquidation / margin_call / seizure
    - XChaCha20-Poly1305-envelope sensitive fields (debts[].principalMicroUsdc, debtorDid)
    - Anchor creditorEnrollment MST record

  Murakumo node: gad (good fortune / treasury — Gen 49:19)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [yobel.ports :as ports]))

;; Both lists are schema-excluded too; cell-level set is defense-in-depth.
(def prohibited-instruments #{"margin_call" "liquidation" "seizure"})
;; Legal-person-only debt classes (yobel is natural-person-only — see ADR-2605201800).
(def legal-person-only-instruments #{"sovereign_bond" "corporate_bond"})

;; ─── Node functions ──────────────────────────────────────────────────

(defn- present? [s]
  (boolean (seq (or s ""))))

(defn validate-input [state]
  (let [debts (get state :debts [])
        errors (cond-> []
                 (empty? debts)
                 (conj "debts[] must have ≥ 1 entry")
                 (> (count debts) 1000)
                 (conj "debts[] capped at 1000 entries per enrollment call")
                 (not (present? (:creditor-did state)))
                 (conj "creditorDid required")
                 (not (present? (:signed-consent state)))
                 (conj "signedConsent required"))
        errors (into errors
                     (mapcat (fn [[i d]]
                               (cond-> []
                                 (neg? (get d :principal-micro-usdc -1))
                                 (conj (str "debts[" i "].principalMicroUsdc must be ≥ 0"))
                                 (not (present? (:origination-date d)))
                                 (conj (str "debts[" i "].originationDate required"))))
                             (map-indexed vector debts)))]
    {:input-valid (empty? errors) :input-errors errors}))

(defn load-rite-context [state rite-registry-port]
  (if (nil? rite-registry-port)
    {:rite-status "active" :rite-effective-date "2026-05-20T00:00:00Z"}
    (let [rite (ports/get-rite rite-registry-port (:rite-id state))]
      {:rite-status (if rite (:status rite) "unknown")
       :rite-effective-date (if rite (:effective-date rite) "")})))

(defn verify-creditor-standing [state council-sbt-port charter-compliance-port]
  (let [creditor (get state :creditor-did "")
        sbt (if council-sbt-port
              (ports/balance-of-level council-sbt-port creditor)
              0)
        aligned (boolean
                 (or (>= sbt 1)
                     (and charter-compliance-port
                          (ports/is-aligned charter-compliance-port creditor))))]
    {:creditor-sbt-level sbt :creditor-aligned aligned}))

(defn verify-signed-consent
  "ERC725 EIP-712 signature recover; assert signer == creditorDid keystore."
  [state erc725-port]
  (if (nil? erc725-port)
    {:consent-signature-valid false}
    (let [valid (ports/verify-eip712-signed-consent
                 erc725-port
                 (:creditor-did state)
                 {:rite-id (:rite-id state)
                  :creditor-did (:creditor-did state)
                  :debts (:debts state)}
                 (:signed-consent state))]
      {:consent-signature-valid (boolean valid)})))

(defn historical-record-gate
  "All debts[].originationDate must be < rite.effectiveDate (Charter Rider §2(b) one-way)."
  [state]
  (let [effective (get state :rite-effective-date "")
        violations (into []
                         (keep (fn [[i d]]
                                 (when (>= (compare (get d :origination-date "") effective) 0)
                                   (str "debts[" i "]: originationDate " (:origination-date d)
                                        " >= rite.effectiveDate " effective
                                        " — new debt origination not allowed"))))
                         (map-indexed vector (get state :debts [])))]
    {:historical-record-compliant (empty? violations)
     :historical-record-violations violations}))

(defn instrument-safety-gate
  "Reject (a) Charter Rider §2(b) prohibited instruments and (b) legal-person-only
  instruments (yobel is natural-person-only — see ADR-2605201800). Defense in depth,
  lexicon already excludes both classes from its enum."
  [state]
  (let [violations (into []
                         (mapcat (fn [[i d]]
                                   (let [inst (get d :instrument "")]
                                     (cond-> []
                                       (contains? prohibited-instruments inst)
                                       (conj (str "debts[" i "].instrument=" inst
                                                  " prohibited by Charter Rider §2(b)"))
                                       (contains? legal-person-only-instruments inst)
                                       (conj (str "debts[" i "].instrument=" inst
                                                  " is a legal-person-only instrument; "
                                                  "yobel is natural-person-only (ADR-2605201800)"))))))
                         (map-indexed vector (get state :debts [])))]
    {:instrument-safety-compliant (empty? violations)
     :instrument-violations violations}))

(defn encrypt-sensitive
  "XChaCha20-Poly1305-envelope debts[].principalMicroUsdc + debts[].debtorDid (ADR-2605181100)."
  [state envelope-crypto]
  (if (nil? envelope-crypto)
    {:encrypted-debts-cid "ipfs://stub-cipher"}
    (let [cipher (ports/envelope
                  envelope-crypto
                  (:debts state)
                  [(:creditor-did state)
                   ;; Council Lv6+ × 3 + asher (release_settlement leader) added by
                   ;; envelope-crypto add-council-recipients
                   ]
                  "yobel.creditor_enrollment")]
      {:encrypted-debts-cid (:cid cipher)})))

(defn anchor-enrollment [state anchor-bridge]
  (let [creditor (:creditor-did state)
        seg (last (str/split creditor #":"))
        enrollment-id (str (:rite-id state) "-cred-" (subs seg 0 (min 8 (count seg))))
        debt-count (count (get state :debts []))]
    (if (nil? anchor-bridge)
      {:enrollment-id enrollment-id
       :debt-count debt-count
       :enrollment-vertex-uri
       (str "at://" creditor
            "/com.etzhayyim.apps.etzhayyim.yobel.creditorEnrollment/" enrollment-id)}
      (let [sc (:signed-consent state)
            result (ports/write-and-anchor
                    anchor-bridge
                    "com.etzhayyim.apps.etzhayyim.yobel.creditorEnrollment"
                    enrollment-id
                    {:rite-id (:rite-id state)
                     :creditor-did creditor
                     :encrypted-debts-cid (:encrypted-debts-cid state)
                     :debt-count debt-count
                     :signed-consent-digest (subs sc 0 (min 32 (count sc)))}
                    true)]
        {:enrollment-id enrollment-id
         :debt-count debt-count
         :enrollment-vertex-uri (:vertex-uri result)}))))

(defn emit-rejection [_state]
  {:enrollment-id ""
   :debt-count 0
   :enrollment-vertex-uri ""})

;; ─── Graph ───────────────────────────────────────────────────────────

(defn gate-router [state]
  (cond
    (not (:input-valid state)) :emit-rejection
    (not= (:rite-status state) "active") :emit-rejection
    (not (:consent-signature-valid state)) :emit-rejection
    (not (:historical-record-compliant state)) :emit-rejection
    (not (:instrument-safety-compliant state)) :emit-rejection
    :else :encrypt-sensitive))

(defn build-graph
  "opts: {:checkpointer :rite-registry-port :council-sbt-port :charter-compliance-port
          :erc725-port :envelope-crypto :anchor-bridge}
  Returns the compiled graph."
  [{:keys [checkpointer rite-registry-port council-sbt-port charter-compliance-port
           erc725-port envelope-crypto anchor-bridge]}]
  (-> (g/state-graph)
      (g/add-node :validate-input validate-input)
      (g/add-node :load-rite-context
                  (fn [s] (load-rite-context s rite-registry-port)))
      (g/add-node :verify-creditor-standing
                  (fn [s] (verify-creditor-standing s council-sbt-port charter-compliance-port)))
      (g/add-node :verify-signed-consent
                  (fn [s] (verify-signed-consent s erc725-port)))
      (g/add-node :historical-record-gate historical-record-gate)
      (g/add-node :instrument-safety-gate instrument-safety-gate)
      (g/add-node :encrypt-sensitive
                  (fn [s] (encrypt-sensitive s envelope-crypto)))
      (g/add-node :anchor-enrollment
                  (fn [s] (anchor-enrollment s anchor-bridge)))
      (g/add-node :emit-rejection emit-rejection)
      (g/set-entry-point :validate-input)
      (g/add-edge :validate-input :load-rite-context)
      (g/add-edge :load-rite-context :verify-creditor-standing)
      (g/add-edge :verify-creditor-standing :verify-signed-consent)
      (g/add-edge :verify-signed-consent :historical-record-gate)
      (g/add-edge :historical-record-gate :instrument-safety-gate)
      (g/add-conditional-edges :instrument-safety-gate gate-router)
      (g/add-edge :encrypt-sensitive :anchor-enrollment)
      (g/add-edge :anchor-enrollment g/END)
      (g/add-edge :emit-rejection g/END)
      (g/compile-graph {:checkpointer checkpointer})))
