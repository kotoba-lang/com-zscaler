(ns futawa.methods.agent
  "futawa 二輪 — small-displacement motorcycle manufacturing cell. 1:1 port of py/agent.py. Handlers
  over the motorcycle schema with constitutional gates: G7 ABS-mandatory (≥125cc/≥6kW), G8 anti-
  surveillance (no GPS/telematics/V2X/DRM), G11 capacity caps (≤250cc/≤15kW/≤200kg), G6 sound ≤80dB,
  G16/G17 USDC + 10% tithe settlement (stops at :intent). Pure compute; the Murakumo llm host binding
  is the omitted leg (unused here)."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)
(def ^:private MAX-DISPLACEMENT-CC 250)
(def ^:private MAX-POWER-KW 15.0)
(def ^:private MAX-DRY-WEIGHT-KG 200.0)
(def ^:private SOUND-LIMIT-DB 80.0)
(def ^:private PROHIBITED-SURVEILLANCE #{"gps" "telematics" "connected-app" "v2x" "diagnostic-drm"})

(defn- infer
  "Murakumo-only inference (G9). The llm host binding is the omitted leg → constant local fallback."
  [_prompt]
  "LLM_NOT_AVAILABLE")

(defn g7-abs-mandatory
  "Check if ABS is mandatory (G7)."
  [displacement-cc power-kw]
  (let [mandatory (and (>= displacement-cc 125) (>= power-kw 6.0))]
    {"abs_mandatory" mandatory
     "reason" (if mandatory "ABS-mandatory ≥125cc / ≥6kW electric" "ABS optional")}))

(defn g8-surveillance-check
  "Check for prohibited surveillance components (G8)."
  [bom-items]
  (let [hits (filterv (fn [i] (some #(str/includes? (str/lower-case (str/trim i)) %) PROHIBITED-SURVEILLANCE)) bom-items)]
    (if (seq hits)
      {"ok" false "reason" (str "prohibited surveillance: " hits " (G8)")}
      {"ok" true "reason" "no surveillance components"})))

(defn g11-capacity-caps
  "Validate G11 capacity caps."
  [displacement-cc power-kw dry-weight-kg]
  (cond
    (> displacement-cc MAX-DISPLACEMENT-CC) {"ok" false "reason" (str "displacement " displacement-cc " > " MAX-DISPLACEMENT-CC "cc (G11)")}
    (> power-kw MAX-POWER-KW) {"ok" false "reason" (str "power " power-kw " > " MAX-POWER-KW "kW (G11)")}
    (> dry-weight-kg MAX-DRY-WEIGHT-KG) {"ok" false "reason" (str "dry weight " dry-weight-kg " > " MAX-DRY-WEIGHT-KG "kg (G11)")}
    :else {"ok" true "reason" "within G11 capacity caps"}))

(defn g6-sound-check
  "Check sound emission limit (G6)."
  [sound-db]
  (if (> sound-db SOUND-LIMIT-DB)
    {"ok" false "reason" (str "sound " sound-db " > " SOUND-LIMIT-DB " dB(A) (G6)")}
    {"ok" true "reason" (str "sound ≤" SOUND-LIMIT-DB " dB(A)")}))

(defn handle-frame-welding
  "L1 frame welding attestation (R0: returns intent, does not execute)."
  [state]
  (merge state {"frame_attestation"
                {"id" (get state "frame_id" "frame-pending")
                 "material_lot" (get state "material_lot" "")
                 "weld_quality" (if (= (get state "weld_type") "tig") "vision-guided-tig" "vision-guided-mig")
                 "roundness_um" 0 "status" "intent"}}))

(defn handle-engine-assembly
  "L2a engine assembly with G11 cap check."
  [state]
  (let [displacement-cc (get state "displacement_cc" 0)
        power-kw (get state "power_kw" 0.0)
        caps (g11-capacity-caps displacement-cc power-kw (get state "dry_weight_kg" 0.0))]
    (if-not (get caps "ok")
      (merge state {"engine_attestation" {"error" (get caps "reason") "blocked" true}})
      (merge state {"engine_attestation"
                    {"id" (get state "engine_id" "engine-pending")
                     "displacement_cc" displacement-cc "power_kw" power-kw
                     "torque_nm" (get state "torque_nm" 0.0) "g11_cap_verified" true "status" "intent"}}))))

(defn handle-electrical-harness
  "L3a electrical harness + G8 surveillance check."
  [state]
  (let [bom (g8-surveillance-check (get state "bom_items" []))]
    (if-not (get bom "ok")
      (merge state {"electrical_attestation" {"error" (get bom "reason") "blocked" true}})
      (merge state {"electrical_attestation"
                    {"id" (get state "electrical_id" "electrical-pending")
                     "g8_surveillance_clear" true "status" "intent"}}))))

(defn handle-suspension-brake
  "L3b suspension + brake with G7 ABS-mandatory check."
  [state]
  (let [abs-check (g7-abs-mandatory (get state "displacement_cc" 0) (get state "power_kw" 0.0))]
    (merge state {"suspension_brake_attestation"
                  {"id" (get state "suspension_brake_id" "suspension-brake-pending")
                   "abs_mandatory_certified" (get abs-check "abs_mandatory") "status" "intent"}})))

(defn handle-body-paint
  "L4 body paint (G5 Charter scan assumed passed)."
  [state]
  (merge state {"paint_attestation"
                {"id" (get state "paint_id" "paint-pending") "g5_charter_scan_pass" true "status" "intent"}}))

(defn handle-final-assembly
  "L5a final assembly + G12 parts catalog + G13 hodoki pre-reg."
  [state]
  (merge state {"vehicle_lot_attestation"
                {"id" (get state "vehicle_lot_id" "vehicle-pending") "vin" (get state "vin" "")
                 "parts_catalog_cid" (get state "parts_catalog_cid" "") "hodoki_preregister_ok" true "status" "intent"}}))

(defn handle-test-dyno-road
  "L5b dyno testing + G6 sound check."
  [state]
  (let [sound-db (get state "sound_db" 0.0)
        sound-ok (g6-sound-check sound-db)]
    (if-not (get sound-ok "ok")
      (merge state {"test_record" {"error" (get sound-ok "reason") "blocked" true}})
      (merge state {"test_record"
                    {"id" (get state "test_id" "test-pending") "power_kw" (get state "dyno_power_kw" 0.0)
                     "torque_nm" (get state "dyno_torque_nm" 0.0) "sound_db" sound-db
                     "abs_pass" (get state "abs_pass" false)
                     "result" (if (get sound-ok "ok") "pass" "fail") "status" "intent"}}))))

(defn handle-provenance-binder
  "L5c terminal provenance binder (Council gate, R0 stops at intent)."
  [state]
  (merge state {"silen_mobility_review"
                {"id" (get state "review_id" "review-pending")
                 "provenance_chain_cid" (get state "provenance_cid" "")
                 "council_approval" "pending" "status" "intent"}}))

(defn build-settlement-intent
  "USDC settlement split. 10% tithe → Public Fund (G16). Stops at :intent."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [tithe (quot (* gross-minor TITHE-BPS) 10000)]
     {"rail" "usdc-base-l2" "grossMinor" gross-minor "titheMinor" tithe
      "makerPayoutMinor" (- gross-minor tithe) "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if buyer-sig-ref "executed" "intent") "buyerSigRef" (or buyer-sig-ref "")})))
