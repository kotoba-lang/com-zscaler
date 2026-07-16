(ns kanayama.cells.mass-balance-binder.state-machine
  "1:1 port of cells/mass_balance_binder/state_machine.py (ADR-2605252400 terminal cell, G2 + G14).
  Aggregates L1–L5b + cross-cutting records into one mass-balance audit:
  input_mass = output_metal + dross + emission_mass, ≥98% closure (G12 KPI), kotoba-datomic-anchored
  (G2 audit log). records_collected → mass_balance_computed → kotoba_datomic_anchored → record_emitted.
  BalanceState dataclass → string-keyed map under \"balance_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "balance_state" {}))

(defn- round2
  "Port of Python round(x, 2) — half-to-even rounding to 2 decimal places."
  [x]
  (-> (java.math.BigDecimal/valueOf (double x))
      (.setScale 2 java.math.RoundingMode/HALF_EVEN)
      .doubleValue))

(defn transition-to-records-collected [state]
  {"balance_state" (assoc (s* state)
                          "upstreamRecords" {"intakeRecord" "bafkreiintake..."
                                             "decoatingAttestation" "bafkreidecoat..."
                                             "meltingAttestation" "bafkreimelt..."
                                             "drossRecoveryRecord" "bafkreidross..."
                                             "dcCastingAttestation" "bafkreicast..."
                                             "rollingAttestation" "bafkreiroll..."
                                             "coilQualificationRecord" "bafkreicoil..."
                                             "airEmissionsAuditRecord" "bafkreiemis..."}
                          "phase" "records_collected" "completionPct" 25)
   "next_node" "compute"})

(defn transition-to-mass-balance-computed [state]
  (let [input-mass 480.5
        output-metal (+ 462.0 3.6)            ; main pour + dross-recovered secondary
        dross-mass (- 8.0 3.6)                 ; gross dross minus recovered = net waste
        emission-mass 4.7                      ; off-gas captured + scrubbed mass
        total-out (+ output-metal dross-mass emission-mass)
        closure-pct (round2 (* (/ total-out input-mass) 100))]
    {"balance_state" (assoc (s* state)
                            "inputMassKg" input-mass
                            "outputMetalKg" output-metal
                            "drossMassKg" dross-mass
                            "emissionMassKg" emission-mass
                            "closurePct" closure-pct
                            "accept" (>= closure-pct 98.0)
                            "phase" "mass_balance_computed" "completionPct" 60)
     "next_node" "anchor"}))

(defn transition-to-kotoba-datomic-anchored [state]
  {"balance_state" (assoc (s* state)
                          "kotoba_datomicAnchor" {"membraneNamespace" "com.etzhayyim.kanayama"
                                                  "anchorTxHash" "0xKANAYAMABALANCE..."
                                                  "l2Chain" "Base Sepolia (R0 dry-run)"
                                                  "anchorBlockNumber" 0
                                                  "g2Compliant" true}
                          "phase" "kotoba-datomic_anchored" "completionPct" 90)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"balance_state" s
     "mass_balance_binder_record" {"$type" "etzhayyim:kanayama:massBalanceBinderRecord"
                                   "lotId" (get s "lotId")
                                   "upstreamRecords" (get s "upstreamRecords")
                                   "inputMassKg" (get s "inputMassKg")
                                   "outputMetalKg" (get s "outputMetalKg")
                                   "drossMassKg" (get s "drossMassKg")
                                   "emissionMassKg" (get s "emissionMassKg")
                                   "closurePct" (get s "closurePct")
                                   "g2Limit" 98.0
                                   "accept" (get s "accept")
                                   "kotoba-datomicAnchor" (get s "kotoba_datomicAnchor")
                                   "recordedAt" "2026-05-26T18:00:00Z"}
     "next_node" "end"}))
