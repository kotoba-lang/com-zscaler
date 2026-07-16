(ns tsutae.methods.agent
  "1:1 port of py/agent.py (ADR-2605261300, R0) — tsutae 伝え handheld-device manufacturing actor.
  Handlers manage the device manufacturing lifecycle: handle-device-order (SBT-gated intake),
  handle-production-progress (8-stage assembly + attestation), handle-quality (QC/RF/functional),
  handle-device-attestation (serial → per-device DID + BoM lineage, ≥2 robot sig). Settlement is
  USDC + ERC-4337 + TitheRouter 10% only (G18), stops at :intent (member signs, G15). Open SoC only
  (G9). TypedDict states → string-keyed maps; record attrs are \":ns/name\" strings (kotoba datoms).

  OMITTED legs: the `__main__` demo. The Murakumo LLM host binding is the `*llm*` dynamic var
  (nil → \"LLM_NOT_AVAILABLE\" fallback, like the kotoba `llm` import-fallback); no handler calls it."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)   ; 10% TitheRouter auto-split (G18), basis points

(def device-order-flow
  ["draft" "placed" "in-production" "qc" "ready" "shipped" "cancelled"])

(def production-stages
  ["pcb-smt" "chassis-assembly" "display-attachment" "firmware-load"
   "final-qc" "packaging" "device-attestation" "recycling-intake"])

(def open-soc-allowlist ["StarFive-JH7110" "SiFive-HiFive-Unmatched" "Allwinner-D1" "iwakura"])
(def proprietary-soc ["Snapdragon" "Apple-A" "Exynos" "Helio" "Dimensity"])

(def ^:dynamic *llm* "Murakumo LLM host binding (nil in local dev, like the kotoba `llm` import)." nil)

(defn- now* [] "2026-06-02T00:00:00Z")

(defn infer-llm
  "Murakumo-only LLM inference (G16). Port of _infer_llm — fallback when the host binding is absent."
  [prompt]
  (if *llm*
    (try (str (*llm* {:model "gemma3:4b" :prompt prompt}))
         (catch Exception _ "LLM_INFERENCE_FAILED"))
    "LLM_NOT_AVAILABLE"))

(defn is-open-soc
  "G9 enforcement: open RISC-V only; proprietary SoC rejected (N1). Port of is_open_soc."
  [soc]
  (if (some #(str/starts-with? soc %) proprietary-soc)
    false
    (boolean (some #(str/starts-with? soc %) open-soc-allowlist))))

(defn handle-device-order
  "SBT-gated member order intake. Port of handle_device_order."
  [state]
  (let [order-id (get state "order_id")
        buyer-did (get state "buyer_did")
        specs (get state "specs")
        soc (get state "soc" "StarFive-JH7110")
        initial-state (get state "initial_state" "draft")
        sbt-active (get state "sbt_active" false)]
    (cond
      (or (not buyer-did) (not sbt-active))
      {"error" "Buyer DID missing or SBT not active (N9 SBT↔SBT internal)" "state" "cancelled"}
      (not (is-open-soc soc))
      {"error" (str "SoC " soc " rejected — open RISC-V only (G9/N1)") "state" "cancelled"}
      :else
      (let [order-id (or order-id (str "do.new.order." (mod (hash (or specs "")) 10000)))]
        {"device_order" {":device-order/id" order-id
                         ":device-order/buyer-did" buyer-did
                         ":device-order/specs" specs
                         ":device-order/soc" soc
                         ":device-order/state" initial-state}}))))

(defn handle-production-progress
  "8-stage assembly + per-stage attestation. Port of handle_production_progress."
  [state]
  (let [order-id (get state "order_id")
        stage (get state "stage")
        cid (get state "cid")
        details (get state "details" "")
        timestamp (get state "timestamp" (now*))]
    (cond
      (or (not order-id) (not stage)) {"error" "Order ID or stage missing"}
      (not (some #(= % stage) production-stages))
      {"error" (str "unknown stage " stage " (not one of the 8 tsutae cells)")}
      :else
      {"production_progress" {":production-progress/id" (str "pp." order-id "." stage)
                              ":production-progress/order" order-id
                              ":production-progress/stage" stage
                              ":production-progress/timestamp" timestamp
                              ":production-progress/note" (str "Stage " stage " completed."
                                                              (when (seq details) (str " Details: " details)))}
       "attestation" (when (seq (str (or cid "")))
                        {":attestation/id" (str "attest." order-id "." stage)
                         ":attestation/order" order-id
                         ":attestation/type" stage
                         ":attestation/cid" cid
                         ":attestation/timestamp" timestamp
                         ":attestation/details" details})})))

(defn handle-quality
  "QC / RF / functional result + order-state transition. Port of handle_quality."
  [state]
  (let [order-id (get state "order_id")
        result (get state "result")
        defects (get state "defects" [])
        inspector-did (get state "inspector_did")
        timestamp (get state "timestamp" (now*))
        current-order-state (get state "current_order_state" "in-production")]
    (if (or (not order-id) (not result) (not inspector-did))
      {"error" "Order ID, result, or inspector DID missing"}
      {"quality_record" {":quality/id" (str "qc." order-id "." timestamp)
                         ":quality/order" order-id
                         ":quality/result" result
                         ":quality/defects" defects
                         ":quality/inspector-did" inspector-did
                         ":quality/timestamp" timestamp}
       "new_order_state" (case result
                           "pass" "ready"
                           "fail" "cancelled"
                           "rework" "in-production"
                           current-order-state)})))

(defn handle-device-attestation
  "serial → per-device DID + BoM lineage (G4 ≥2 distinct robot signers / G14). Port of
  handle_device_attestation."
  [state]
  (let [order-id (get state "order_id")
        serial (get state "serial")
        bom-lineage-cids (get state "bom_lineage_cids" [])
        robot-signers (get state "robot_signers" [])]
    (cond
      (or (not order-id) (not serial)) {"error" "Order ID or serial missing"}
      (< (count (set robot-signers)) 2) {"error" "G4: fewer than 2 distinct robot signers" "accept" false}
      :else
      {"device_record" {":device/serial" serial
                        ":device/order" order-id
                        ":device/did" (str "did:web:etzhayyim.com:tsutae:device:" serial)
                        ":device/bom-lineage" bom-lineage-cids
                        ":device/signers" (vec (set robot-signers))
                        ":device/repair-event-ready" true}
       "accept" true})))

(defn build-settlement-intent
  "USDC + TitheRouter intent (NOT broadcast; G18/G15). Port of build_settlement_intent — 10% tithe →
  Public Fund; stops at :intent unless a member signature ref is supplied."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [tithe (quot (* gross-minor TITHE-BPS) 10000)]
     {"rail" "usdc-base-l2"
      "grossMinor" gross-minor
      "titheMinor" tithe
      "factoryPayoutMinor" (- gross-minor tithe)
      "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if buyer-sig-ref "executed" "intent")
      "buyerSigRef" (or buyer-sig-ref "")})))
