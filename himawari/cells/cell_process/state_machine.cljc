(ns himawari.cells.cell-process.state-machine
  "1:1 port of cells/cell_process/cell.py — solar PV cell process line (ADR-2606021200).

  Cell process line: texture → junction (diffusion/PECVD) → metallization → flash IV
  → gas abatement (G3 gate) → witness → emit_record.

  Deterministic process model enforcing G3 (high-GWP fluorinated-gas abatement ≥99% DRE
  or substitution) + G6 (Ag→Cu low-toxicity metallization roadmap). Composes kuni-umi
  Otete (cell handling) + Mimi (flash IV + EL metrology) by reference only — this cell
  does not re-implement their solvers.

  7-node LangGraph-shaped DAG (fallback sequential driver when LangGraph unavailable):
  init → texture → junction → metallization → flash_iv → gas_abatement
    (conditional) → witness → emit_record OR halt."
  (:require [clojure.string :as str]))

;; G3: high-GWP etch/clean gases (AR5 100-yr GWP values, industry-standard).
(def ^:private HIGH_GWP_GASES
  {"NF3" 16100
   "SF6" 23500
   "CF4" 6630
   "C2F6" 11100
   "C3F8" 8900})

(def ^:private MIN_DRE 0.99)  ;; G3: ≥99% destruction-removal efficiency floor

;; G6: Ag→Cu low-toxicity metallization roadmap.
(def ^:private METALLIZATION_KNOWN
  #{"silver" "ag-cu-hybrid" "copper"})

(def ^:private METALLIZATION_ON_ROADMAP
  #{"ag-cu-hybrid" "copper"})

(def ^:private CELL_ARCH_KNOWN
  #{"PERC" "TOPCon" "HJT"})

;; Robot DIDs (composed from kuni-umi, not re-implemented).
(def ^:private OTETE_DID "did:web:etzhayyim.com:kuni-umi:otete")
(def ^:private MIMI_DID "did:web:etzhayyim.com:kuni-umi:mimi")

(defn- default-cell-state
  "CellState defaults merged with any existing \"cell_state\" map."
  [state]
  (merge {"phase" "init"
          "batchId" "unknown"
          "waferBatchId" "unknown"
          "cellArchitecture" "TOPCon"
          "metallization" "ag-cu-hybrid"
          "waferCount" 1000
          "completionPct" 0
          "recordedAt" ""
          "textureMetrics" nil
          "junctionMetrics" nil
          "metallizationMetrics" nil
          "flashIvMetrics" nil
          "gasAbatement" nil
          "binDistribution" nil
          "flashIvMedianMilliwp" nil
          "gasAbatementCid" nil
          "binDistributionCid" nil
          "processParametersCid" nil
          "metallizationFlags" []
          "anomalyFlags" []
          "robotSignatures" []
          "errorMsg" nil}
         (get state "cell_state" {})))

(defn- content-ref
  "Deterministic content reference for an attestation payload.
  R0/R1 stand-in for IPFS CID: a stable digest over the canonical key set."
  [prefix payload]
  (let [keysig (str/join "|" (map #(str % "=" (get payload %))
                                   (sort (keys payload))))
        hash-val (bit-and (Math/abs (hash keysig)) 0xFFFFFFFFFFFF)]
    (str prefix ":" (format "%012x" hash-val))))

(defn transition-init
  "INIT: seed cell state from the inbound waferBatchRecord handoff."
  [state]
  (let [;; Use input values, fall back to defaults only if absent
        wafer-batch-id (str (get state "waferBatchId" "wafer-unknown"))
        batch-id (str (or (get state "batchId") (str "cell-" wafer-batch-id)))
        arch (let [a (str (get state "cellArchitecture" "TOPCon"))]
               (if (contains? CELL_ARCH_KNOWN a) a "TOPCon"))
        metallization (let [m (str (get state "metallization" "ag-cu-hybrid"))]
                        (if (contains? METALLIZATION_KNOWN m) m "ag-cu-hybrid"))
        wafer-count (max 0 (int (get state "waferCount" 1000)))
        recorded-at (str (get state "recordedAt" ""))
        cs {"phase" "textured"
            "batchId" batch-id
            "waferBatchId" wafer-batch-id
            "cellArchitecture" arch
            "metallization" metallization
            "waferCount" wafer-count
            "completionPct" 15
            "recordedAt" recorded-at
            "textureMetrics" nil
            "junctionMetrics" nil
            "metallizationMetrics" nil
            "flashIvMetrics" nil
            "gasAbatement" nil
            "binDistribution" nil
            "flashIvMedianMilliwp" nil
            "gasAbatementCid" nil
            "binDistributionCid" nil
            "processParametersCid" nil
            "metallizationFlags" []
            "anomalyFlags" []
            "robotSignatures" []
            "errorMsg" nil}]
    {"cell_state" cs
     "next_node" "texture"}))

(defn transition-texture
  "INIT → TEXTURED: alkaline/acid texture etch + clean."
  [state]
  (let [cs (default-cell-state state)
        arch (get cs "cellArchitecture")
        etch-chem (if (= arch "PERC") "HF-HNO3-acid" "KOH-IPA-alkaline")
        metrics {"etch_chemistry" etch-chem
                 "target_reflectance_pct" 11.0
                 "achieved_reflectance_pct" 10.4
                 "saw_damage_removed_um" 4.0}
        cs (assoc cs
                  "textureMetrics" metrics
                  "phase" "textured"
                  "completionPct" 15)]
    {"cell_state" cs "next_node" "junction"}))

(defn transition-junction
  "TEXTURED → JUNCTION: emitter diffusion + PECVD passivation/ARC."
  [state]
  (let [cs (default-cell-state state)
        arch (get cs "cellArchitecture")
        recipe (case arch
                 "PERC" {"emitter" "POCl3-diffusion" "passivation" "AlOx/SiNx-PECVD"}
                 "TOPCon" {"emitter" "LPCVD-poly-Si" "passivation" "SiOx-tunnel/SiNx-PECVD"}
                 "HJT" {"emitter" "a-Si:H-PECVD" "passivation" "i-a-Si:H-PECVD"}
                 {})
        metrics (merge recipe
                       {"sheet_resistance_ohm_sq" 130
                        "implied_voc_mv" 720
                        "chamber_clean_gas" "NF3"})
        cs (assoc cs
                  "junctionMetrics" metrics
                  "phase" "junction"
                  "completionPct" 40)]
    {"cell_state" cs "next_node" "metallization"}))

(defn transition-metallization
  "JUNCTION → METALLIZED: screen-print / plate contacts (G6 Ag→Cu roadmap)."
  [state]
  (let [cs (default-cell-state state)
        metallization (get cs "metallization")
        metrics {"paste_or_plating" metallization
                 "fingers" 110
                 "busbars" (if (= metallization "silver") "9BB" "busbarless-multiwire")
                 "no_lead_solder" true}
        flags (if (not (contains? METALLIZATION_ON_ROADMAP metallization))
                [(str "G6:silver-only-off-roadmap:" metallization
                      "; Ag→Cu (ag-cu-hybrid|copper) substitution required for R2+")]
                [])
        cs (assoc cs
                  "metallizationMetrics" metrics
                  "metallizationFlags" flags
                  "phase" "metallized"
                  "completionPct" 60)]
    {"cell_state" cs "next_node" "flash_iv"}))

(defn transition-flash-iv
  "METALLIZED → FLASH_TESTED: Mimi flash IV (AAA simulator) + power binning."
  [state]
  (let [cs (default-cell-state state)
        arch (get cs "cellArchitecture")
        median-mwp (case arch
                     "PERC" 6850   ;; ~23.0 %
                     "TOPCon" 7180 ;; ~24.1 %
                     "HJT" 7330    ;; ~24.6 %
                     7180)
        n (get cs "waferCount")
        bin-dist {"binA_plus_high" (quot n 4)
                  "binA_nominal" (- n (quot n 4) (quot n 5) (quot n 20))
                  "binB_low" (quot n 5)
                  "binC_reject" (quot n 20)}
        metrics {"simulator_class" "AAA-IEC60904-9"
                 "median_pmax_milliwp" median-mwp
                 "median_voc_mv" 695
                 "median_isc_ma" 13500
                 "median_fill_factor_pct" 81.5}
        cs (assoc cs
                  "flashIvMedianMilliwp" median-mwp
                  "flashIvMetrics" metrics
                  "binDistribution" bin-dist
                  "phase" "flash_tested"
                  "completionPct" 75)]
    {"cell_state" cs "next_node" "gas_abatement"}))

(defn transition-gas-abatement
  "FLASH_TESTED → ABATEMENT_VERIFIED or HALT: G3 high-GWP gas abatement."
  [state]
  (let [cs (default-cell-state state)
        arch (get cs "cellArchitecture")
        used-gases (cond-> ["NF3"]  ;; PECVD chamber clean (all architectures)
                     (= arch "PERC") (conj "CF4"))
        substitutions {}
        gas-lines (for [gas used-gases]
                    (let [substituted (contains? substitutions gas)
                          dre (if substituted 1.0 0.995)]
                      {"gas" gas
                       "gwp100" (get HIGH_GWP_GASES gas 0)
                       "substituted" substituted
                       "substituteWith" (get substitutions gas)
                       "destructionRemovalEfficiency" dre
                       "meetsG3Floor" (or substituted (>= dre MIN_DRE))}))
        below-floor (filter #(not (get % "meetsG3Floor")) gas-lines)
        abatement {"minDreFloor" MIN_DRE
                   "gases" gas-lines
                   "uncontrolledVenting" false
                   "allMeetG3" (empty? below-floor)}
        abate-cid (content-ref "ipfs/himawari-g3-abatement" abatement)
        proc-cid (content-ref "ipfs/himawari-process-params"
                              {"texture" (get cs "textureMetrics")
                               "junction" (get cs "junctionMetrics")
                               "metallization" (get cs "metallizationMetrics")})
        bin-cid (content-ref "ipfs/himawari-bin-dist" (get cs "binDistribution" {}))
        cs (assoc cs
                  "gasAbatement" abatement
                  "gasAbatementCid" abate-cid
                  "processParametersCid" proc-cid
                  "binDistributionCid" bin-cid)]
    (if (empty? below-floor)
      (let [cs (assoc cs "phase" "abatement_verified" "completionPct" 85)]
        {"cell_state" cs "next_node" "witness"})
      (let [gases-str (str/join "," (map #(get % "gas") below-floor))
            msg (str "G3 violation: " gases-str " below " MIN_DRE " DRE with no substitution")
            cs (assoc cs
                      "phase" "anomaly_halt"
                      "anomalyFlags" [(str "G3:gas-abatement-below-99pct:" gases-str
                                           "; abate to ≥99% DRE or substitute before release")]
                      "errorMsg" msg)]
        {"cell_state" cs "next_node" "halt"}))))

(defn transition-witness
  "ABATEMENT_VERIFIED → WITNESS_WAIT: collect ≥2 robot Ed25519 signatures."
  [state]
  (let [cs (default-cell-state state)
        sigs [{"robotDid" OTETE_DID
               "role" "cell_line_executor"
               "timestamp" "2026-06-02T00:00:00Z"
               "signature" "ed25519:otete-cell-process-sig"}
              {"robotDid" MIMI_DID
               "role" "flash_iv_metrology_witness"
               "timestamp" "2026-06-02T00:00:05Z"
               "signature" "ed25519:mimi-flash-iv-sig"}]
        cs (assoc cs
                  "robotSignatures" sigs
                  "phase" "witness_wait"
                  "completionPct" 95)]
    {"cell_state" cs "next_node" "emit_record"}))

(defn transition-emit-record
  "WITNESS_WAIT → COMPLETE: emit com.etzhayyim.himawari.cellBatchRecord."
  [state]
  (let [cs (default-cell-state state)
        record {"$type" "com.etzhayyim.himawari.cellBatchRecord"
                "batchId" (get cs "batchId")
                "waferBatchId" (get cs "waferBatchId")
                "recordedAt" (get cs "recordedAt")
                "cellArchitecture" (get cs "cellArchitecture")
                "metallization" (get cs "metallization")
                "gasAbatementCid" (get cs "gasAbatementCid")
                "binDistributionCid" (get cs "binDistributionCid")
                "processParametersCid" (get cs "processParametersCid")
                "attestingRobots" (mapv #(get % "robotDid") (get cs "robotSignatures"))
                "robotSignatures" (get cs "robotSignatures")
                "flashIvMedianMilliwp" (get cs "flashIvMedianMilliwp")}
        cs (assoc cs "phase" "complete" "completionPct" 100)]
    {"cell_state" cs
     "cell_batch_record" record
     "metallizationFlags" (get cs "metallizationFlags")
     "next_node" "end"}))

(defn transition-halt
  "ANOMALY_HALT: halt the batch, escalate to a human PV-process engineer."
  [state]
  (let [cs (default-cell-state state)
        alert {"$type" "com.etzhayyim.himawari.cellBatchRecord.halt"
               "batchId" (get cs "batchId")
               "waferBatchId" (get cs "waferBatchId")
               "event" "cell_process_halt"
               "reason" "g3_gas_abatement_anomaly"
               "anomalies" (get cs "anomalyFlags")
               "gasAbatementCid" (get cs "gasAbatementCid")
               "escalation" "human_pv_process_engineer_review_required"
               "correctiveAction" "Raise fluorinated-gas DRE to ≥99% or substitute the gas, then re-run."}]
    {"cell_state" cs
     "alert_record" alert
     "metallizationFlags" (get cs "metallizationFlags")
     "next_node" "end"}))

(defn- run-sequential
  "Fallback super-step driver (when LangGraph unavailable).
  Each node returns its successor in next_node; follow until terminal node."
  [state]
  (let [table {"init" transition-init
               "texture" transition-texture
               "junction" transition-junction
               "metallization" transition-metallization
               "flash_iv" transition-flash-iv
               "gas_abatement" transition-gas-abatement
               "witness" transition-witness
               "emit_record" transition-emit-record
               "halt" transition-halt}]
    (loop [merged state node "init" steps 0]
      (if (>= steps 11)
        merged
        (let [result ((get table node) merged)
              next-node (get result "next_node" "end")]
          (if (= next-node "end")
            result
            (recur (merge merged result) next-node (inc steps))))))))

(defn solve
  "Execute the cell super-step loop and return the final state."
  [state]
  (run-sequential (assoc state "cell_state" (default-cell-state state))))
