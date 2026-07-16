(ns kanayama.methods.agent
  "kanayama 金 — circular metallurgy (UBC Al recycling) cell. 1:1 port of py/agent.py. Constitutional
  gates: G2 mass-balance ≥98% closure, G12 KPI caps (recovery ≥95% / energy ≤6 kWh·kg), G4 witness
  quorum ≥2, G7 USDC + 10% tithe (stops at :intent). Pure compute; the Murakumo llm host binding is
  unused here (the omitted leg). (The 9 Pregel cells remain a separate python layer — this is the
  agent layer only.)"
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)
(def ^:private RECOVERY-RATE-MIN-PCT 95.0)
(def ^:private ENERGY-CAP-KWH-PER-KG 6.0)

(defn check-mass-balance
  "Verify ≥98% mass closure (input = output_metal + dross + emission)."
  [input-mass output-metal dross emissions-mass]
  (let [accounted (+ output-metal dross emissions-mass)]
    (if (<= input-mass 0)
      {"ok" false "reason" "input_mass must be positive"}
      (let [closure-pct (* (/ accounted input-mass) 100.0)]
        (if (>= closure-pct 98.0)
          {"ok" true "closure_pct" closure-pct}
          {"ok" false "closure_pct" closure-pct
           "reason" (str "mass closure " (format "%.1f" closure-pct) "% < 98% required (G2)")})))))

(defn check-kpi-caps
  "Verify recovery rate ≥95% and energy ≤6 kWh/kg for Wave 1 Al."
  [recovery-rate-pct energy-kwh-per-kg]
  (let [failures (cond-> []
                   (< recovery-rate-pct RECOVERY-RATE-MIN-PCT)
                   (conj (str "recovery " (format "%.1f" (double recovery-rate-pct)) "% < " RECOVERY-RATE-MIN-PCT "% (G12)"))
                   (> energy-kwh-per-kg ENERGY-CAP-KWH-PER-KG)
                   (conj (str "energy " (format "%.1f" (double energy-kwh-per-kg)) " kWh/kg > " ENERGY-CAP-KWH-PER-KG " (G12)")))]
    (if (seq failures)
      {"ok" false "reason" (str/join "; " failures)}
      {"ok" true})))

(defn check-witness-sigs
  "≥2 distinct robots must sign each pour (Ed25519, DID-bound)."
  [witness-sigs]
  (if (or (not witness-sigs) (< (count witness-sigs) 2))
    {"ok" false "reason" (str "witness quorum " (count (or witness-sigs [])) " < 2 required (G4)")}
    {"ok" true "witness_count" (count witness-sigs)}))

(defn intake-qa
  "L1 UBC bale QA thresholds (heuristic; wave 1 baseline)."
  [bale-weight cl-pct moisture-pct fe-ppm]
  (let [passed (and (<= cl-pct 2.0) (<= moisture-pct 5.0) (<= fe-ppm 500))]
    {":intake/id" (format "intake.%.0fkg" (double bale-weight))
     ":intake/baleWeight" bale-weight
     ":intake/clResiduePct" cl-pct
     ":intake/moisturePct" moisture-pct
     ":intake/magneticImpurityPpm" fe-ppm
     ":intake/qaPassed" passed
     ":intake/blocked" (not passed)}))

(defn build-settlement-intent
  "USDC settlement split. 10% tithe → Public Fund; operator gets the net. Stops at :intent —
  broadcast needs an operator signature (G15) + gate (G11)."
  ([gross-minor] (build-settlement-intent gross-minor nil))
  ([gross-minor operator-sig-ref]
   (let [tithe (quot (* gross-minor TITHE-BPS) 10000)]
     {"rail" "usdc-base-l2" "grossMinor" gross-minor "titheMinor" tithe
      "operatorPayoutMinor" (- gross-minor tithe) "titheRouter" "50-infra/etzhayyim-tithe-router"
      "state" (if operator-sig-ref "executed" "intent") "operatorSigRef" (or operator-sig-ref "")})))

(defn finalize-batch-settlement
  "Compute full batch record + settlement intent after all 9 cells (G2 + G12 gated)."
  [batch-id input-mass output-metal dross emissions-mass recovery-pct energy-per-kg]
  (let [mb (check-mass-balance input-mass output-metal dross emissions-mass)]
    (if-not (get mb "ok")
      {"error" (get mb "reason") "blocked" true}
      (let [kpi (check-kpi-caps recovery-pct energy-per-kg)]
        (if-not (get kpi "ok")
          {"error" (get kpi "reason") "blocked" true}
          (let [gross-minor (long (* input-mass 2500000))
                settlement (build-settlement-intent gross-minor)]
            {":batchSettlement/id" batch-id
             ":batchSettlement/inputMass" input-mass
             ":batchSettlement/outputMetal" output-metal
             ":batchSettlement/drossCollected" dross
             ":batchSettlement/closurePct" (get mb "closure_pct" 0)
             ":batchSettlement/recoveryRate" recovery-pct
             ":batchSettlement/energyKwhPerKg" energy-per-kg
             ":batchSettlement/settlement" settlement}))))))
