(ns sarutahiko.methods.agent
  "sarutahiko 猿田彦 — Heavy Class-8 truck manufacturing handlers (kotoba WASM cell).
   ADR-2605252500, R0 scaffold. Faithful port of py/agent.py.

   Handlers manage the vehicle manufacturing lifecycle:
     handle-vehicle-order       Create and manage vehicle orders
     handle-production-progress Update production stages and record attestations
     handle-quality             Record quality inspection results
     handle-vin-attestation     Bind VIN to kotoba-datomic with attestations
     build-settlement-intent    USDC + TitheRouter intent (G18/G15)

   LLM access is Murakumo-only via KotobaLLM (127.0.0.1:4000, gemma3:4b; G16).
   State is written back to the kotoba Datom log (G17). Settlement is USDC on Base L2 +
   ERC-4337 + TitheRouter 10% only — no fiat, no Stripe (G18). The platform holds
   no key; the member signs each settlement with their own passkey/smart-account (G15).
   Every stage is recorded as a Datom — no silent truncation.

   This R0 build computes and returns plans/records; it does not dispatch real factory
   work and does not broadcast settlements (both G11-gated; settlement stops at :intent).")

;; 10% TitheRouter auto-split (G18), basis points
(def tithe-bps 1000)

;; Vehicle order states
(def vehicle-order-flow
  ["draft" "placed" "in-production" "qc" "ready" "shipped" "cancelled"])

;; Production progress stages
(def production-stages
  ["frame-fabrication" "powertrain-assembly" "cab-body-forming" "final-marriage"
   "paint-finishing" "electrical-integration" "quality-road-test"
   "emissions-audit" "vin-attestation-binder"])

;; ---------------------------------------------------------------------------
;; Helper functions
;; ---------------------------------------------------------------------------

(defn get-current-timestamp
  "Fixed timestamp for R0 scaffold and testing."
  []
  "2026-06-02T00:00:00Z")

;; ---------------------------------------------------------------------------
;; handle-vehicle-order — Create and manage vehicle orders
;; ---------------------------------------------------------------------------

(defn handle-vehicle-order
  "Create and manage a vehicle order.
   State keys: :order-id, :buyer-did, :specs, :initial-state, :sbt-active
   Returns map with :vehicle-order or :error/:state."
  [{:keys [order-id buyer-did specs initial-state sbt-active]
    :or {initial-state "draft"
         sbt-active false}}]
  (if (or (nil? buyer-did) (not sbt-active))
    {:error "Buyer DID missing or SBT not active (G14 equivalent)"
     :state "cancelled"}
    (let [oid (or order-id
                  (str "vo.new.order." (mod (hash (or specs "")) 10000)))
          order-record {":vehicle-order/id"        oid
                        ":vehicle-order/buyer-did" buyer-did
                        ":vehicle-order/specs"     specs
                        ":vehicle-order/state"     initial-state}]
      {:vehicle-order order-record})))

;; ---------------------------------------------------------------------------
;; handle-production-progress — Update production stages and record attestations
;; ---------------------------------------------------------------------------

(defn handle-production-progress
  "Update a production stage and optionally record an IPFS-pinned attestation (G3).
   State keys: :order-id, :stage, :cid, :details, :timestamp
   Returns map with :production-progress and :attestation (nil when no cid)."
  [{:keys [order-id stage cid details timestamp]
    :or {details ""
         timestamp (get-current-timestamp)}}]
  (if (or (nil? order-id) (nil? stage))
    {:error "Order ID or stage missing"}
    (let [note (str "Stage " stage " completed."
                    (when (seq details) (str " Details: " details)))
          progress-record {":production-progress/id"        (str "pp." order-id "." stage)
                           ":production-progress/order"     order-id
                           ":production-progress/stage"     stage
                           ":production-progress/timestamp" timestamp
                           ":production-progress/note"      note}
          attestation-record (when (seq cid)
                               {":attestation/id"        (str "attest." order-id "." stage)
                                ":attestation/order"     order-id
                                ":attestation/type"      stage
                                ":attestation/cid"       cid
                                ":attestation/timestamp" timestamp
                                ":attestation/details"   details})]
      {:production-progress progress-record
       :attestation          attestation-record})))

;; ---------------------------------------------------------------------------
;; handle-quality — Record quality inspection results
;; ---------------------------------------------------------------------------

(defn handle-quality
  "Record quality inspection results and derive the new order state.
   State keys: :order-id, :result, :defects, :inspector-did, :timestamp, :current-order-state
   Returns map with :quality-record and :new-order-state."
  [{:keys [order-id result defects inspector-did timestamp current-order-state]
    :or {defects []
         timestamp (get-current-timestamp)
         current-order-state "in-production"}}]
  (if (or (nil? order-id) (nil? result) (nil? inspector-did))
    {:error "Order ID, result, or inspector DID missing"}
    (let [quality-record {":quality/id"           (str "qc." order-id "." timestamp)
                          ":quality/order"        order-id
                          ":quality/result"       result
                          ":quality/defects"      defects
                          ":quality/inspector-did" inspector-did
                          ":quality/timestamp"    timestamp}
          new-order-state (cond
                            (= result "pass")   "ready"
                            (= result "fail")   "cancelled"
                            (= result "rework") "in-production"
                            :else               current-order-state)]
      {:quality-record   quality-record
       :new-order-state  new-order-state})))

;; ---------------------------------------------------------------------------
;; handle-vin-attestation — Bind VIN to kotoba-datomic with attestations
;; ---------------------------------------------------------------------------

(defn handle-vin-attestation
  "Bind a VIN to the kotoba Datom log with all collected attestations.
   State keys: :order-id, :vin, :emissions-audit-id, :silen-vehicle-review-id,
               :attestation-ids, :timestamp
   Returns map with :vehicle-record."
  [{:keys [order-id vin emissions-audit-id silen-vehicle-review-id attestation-ids timestamp]
    :or {attestation-ids []
         timestamp (get-current-timestamp)}}]
  (if (or (nil? order-id) (nil? vin))
    {:error "Order ID or VIN missing"}
    (let [vehicle-did    (str "did:web:etzhayyim.com:sarutahiko:vehicle:" vin) ;; G13
          vehicle-record {":vehicle/vin"          vin
                          ":vehicle/order"        order-id
                          ":vehicle/did"          vehicle-did
                          ":vehicle/attestations" attestation-ids
                          ":vehicle/emissions-audit" emissions-audit-id
                          ":vehicle/final-review" silen-vehicle-review-id}]
      {:vehicle-record vehicle-record})))

;; ---------------------------------------------------------------------------
;; build-settlement-intent — USDC + TitheRouter intent (NOT broadcast; G18/G15)
;; ---------------------------------------------------------------------------

(defn build-settlement-intent
  "Compute the USDC settlement split. 10% tithe → Public Fund.
   Stops at :intent — broadcast needs a member signature (G15).
   Args: gross-minor (integer, USDC minor units), buyer-sig-ref (optional string)."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [tithe (quot (* gross-minor tithe-bps) 10000)]
     {:rail               "usdc-base-l2"
      :grossMinor         gross-minor
      :titheMinor         tithe
      :factoryPayoutMinor (- gross-minor tithe)
      :titheRouter        "50-infra/etzhayyim-tithe-router"
      :state              (if buyer-sig-ref "executed" "intent")
      :buyerSigRef        (or buyer-sig-ref "")})))
