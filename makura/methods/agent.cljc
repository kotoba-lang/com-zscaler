(ns makura.methods.agent
  "makura 枕 — foam pillow manufacturing cell. 1:1 port of py/agent.py. Handlers over the foam-pillow
  schema with the constitutional gates enforced: G6 isocyanate exposure (MDI ≤5 ppb / TDI ≤2 ppb),
  G8 FR-chemistry exclusion (no PBDE/TDCPP/TCEP/Sb₂O₃), G11 KPI caps (mass/dims/combined), G14 no
  embedded sensors, G17/G18 USDC + 10% tithe settlement (stops at :intent). Pure compute; the
  Murakumo llm host binding is unused here (the omitted leg)."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)
(def ^:private MAX-FOAM-MASS-KG 2.0)
(def ^:private MAX-DIMS-CM [80.0 50.0 25.0])
(def ^:private MAX-COMBINED-KG 4.0)
(def ^:private MDI-CEILING-PPB 5.0)
(def ^:private TDI-CEILING-PPB 2.0)
(def ^:private PROHIBITED-FR #{"pbde" "tdcpp" "tcep" "sb2o3" "antimony trioxide"})
(def ^:private PROHIBITED-EMBEDDED #{"rfid" "nfc" "iot" "sensor" "bluetooth" "smart-chip"})

(defn- infer
  "Murakumo-only inference (G15). The llm host binding is the omitted leg → constant local fallback."
  [_prompt]
  "LLM_NOT_AVAILABLE")

(defn kpi-caps-ok [foam-mass-kg dims-cm combined-kg]
  (cond
    (> foam-mass-kg MAX-FOAM-MASS-KG)
    {"ok" false "reason" (str "foam mass " foam-mass-kg " > " MAX-FOAM-MASS-KG " kg (G11)")}
    (some (fn [[d m]] (> d m)) (map vector dims-cm MAX-DIMS-CM))
    {"ok" false "reason" (str "dims " dims-cm " exceed " MAX-DIMS-CM " cm (G11)")}
    (> combined-kg MAX-COMBINED-KG)
    {"ok" false "reason" (str "combined " combined-kg " > " MAX-COMBINED-KG " kg (G11)")}
    :else {"ok" true "reason" "within KPI caps"}))

(defn exposure-ok [mdi-ppb tdi-ppb]
  (cond
    (> mdi-ppb MDI-CEILING-PPB) {"ok" false "reason" (str "MDI " mdi-ppb " > " MDI-CEILING-PPB " ppb 8h TWA (G6)")}
    (> tdi-ppb TDI-CEILING-PPB) {"ok" false "reason" (str "TDI " tdi-ppb " > " TDI-CEILING-PPB " ppb 8h TWA (G6)")}
    :else {"ok" true "reason" "exposure within ceilings"}))

(defn fr-chemistry-ok [chemistry-terms]
  (let [hits (filterv #(contains? PROHIBITED-FR (str/lower-case (str/trim %))) chemistry-terms)]
    (if (seq hits)
      {"ok" false "reason" (str "prohibited FR chemistry: " hits " (G8)")}
      {"ok" true "reason" "FR-free"})))

(defn bom-no-embedded [bom-items]
  (let [hits (filterv (fn [i] (some #(str/includes? (str/lower-case (str/trim i)) %) PROHIBITED-EMBEDDED)) bom-items)]
    (if (seq hits)
      {"ok" false "reason" (str "embedded electronics prohibited: " hits " (G14)")}
      {"ok" true "reason" "no embedded sensors"})))

(defn record-foam-batch
  "Append one foam-batch attestation. Enforces FR-chemistry (G8) + isocyanate exposure (G6) gates."
  ([batch-id chemistry-terms mdi-ppb tdi-ppb] (record-foam-batch batch-id chemistry-terms mdi-ppb tdi-ppb "" ""))
  ([batch-id chemistry-terms mdi-ppb tdi-ppb density voc-test]
   (let [fr (fr-chemistry-ok chemistry-terms)]
     (if-not (get fr "ok")
       {"error" (get fr "reason") "blocked" true}
       (let [exp (exposure-ok mdi-ppb tdi-ppb)]
         (if-not (get exp "ok")
           {"error" (get exp "reason") "blocked" true}
           {":foamBatchAttestation/id" batch-id
            ":foamBatchAttestation/chemistry" (str/join "," chemistry-terms)
            ":foamBatchAttestation/density" density
            ":foamBatchAttestation/vocTest" voc-test}))))))

(defn record-qc
  ([lot-id result] (record-qc lot-id result "0"))
  ([lot-id result reject-pct]
   {":qcRecord/id" (str "qc." lot-id) ":qcRecord/visual" result ":qcRecord/rejectPercentage" reject-pct}))

(defn finalize-pillow-lot
  "Finalize a pillow lot. Enforces KPI caps (G11) + no-embedded-sensors (G14)."
  [lot-id foam-mass-kg dims-cm combined-kg bom-items]
  (let [caps (kpi-caps-ok foam-mass-kg dims-cm combined-kg)]
    (if-not (get caps "ok")
      {"error" (get caps "reason") "blocked" true}
      (let [bom (bom-no-embedded bom-items)]
        (if-not (get bom "ok")
          {"error" (get bom "reason") "blocked" true}
          {":pillowLotAttestation/id" lot-id
           ":pillowLotAttestation/fillWeight" (str foam-mass-kg "kg")
           ":pillowLotAttestation/lotDid" (str "did:web:makura.etzhayyim.com:lot:" lot-id)})))))

(defn build-settlement-intent
  "USDC settlement split. 10% tithe → Public Fund. Stops at :intent — broadcast needs a member
  signature (G18)."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor buyer-sig-ref]
   (let [tithe (quot (* gross-minor TITHE-BPS) 10000)]
     {"rail" "usdc-base-l2" "grossMinor" gross-minor "titheMinor" tithe
      "makerPayoutMinor" (- gross-minor tithe) "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if buyer-sig-ref "executed" "intent") "buyerSigRef" (or buyer-sig-ref "")})))
