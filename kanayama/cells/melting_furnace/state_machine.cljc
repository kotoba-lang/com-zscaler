(ns kanayama.cells.melting-furnace.state-machine
  "1:1 port of cells/melting_furnace/state_machine.py (ADR-2605252400 L3). Twin-chamber Al furnace
  ~720°C with N₂/Cl₂ degas + salt-flux refining + alloy adjust to 3xxx/5xxx; witness quorum ≥2 robots
  per pour (G4). MeltingState dataclass → string-keyed map under \"melting_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "melting_state" {}))

(defn transition-to-charged [state]
  {"melting_state" (assoc (s* state) "chargeMassKg" 470.0 "phase" "charged" "completionPct" 15)
   "next_node" "hold"})

(defn transition-to-melt-held [state]
  {"melting_state" (assoc (s* state) "furnaceTempC" 720 "phase" "melt_held" "completionPct" 40)
   "next_node" "degas"})

(defn transition-to-degas-complete [state]
  {"melting_state" (assoc (s* state) "degasGas" "N2" "saltFluxKg" 6.4 "phase" "degas_complete" "completionPct" 60)
   "next_node" "alloy"})

(defn transition-to-alloy-adjusted [state]
  {"melting_state" (assoc (s* state)
                          "alloyComposition" {"designation" "AA3104 (can body)"
                                              "Mn_pct" 0.95 "Mg_pct" 1.10 "Fe_pct" 0.40 "Si_pct" 0.25
                                              "Cu_pct" 0.15 "Zn_pct" 0.10 "Al_pct" "balance"}
                          "phase" "alloy_adjusted" "completionPct" 80)
   "next_node" "pour"})

(defn transition-to-pour-witnessed [state]
  {"melting_state" (assoc (s* state)
                          "pourMassKg" 462.0
                          "robotSignatures" [{"robotDid" "did:web:etzhayyim.com:kamado-unit-1" "role" "furnace_tender"
                                              "timestamp" "2026-05-26T11:00:00Z" "signature" "..."}
                                             {"robotDid" "did:web:etzhayyim.com:yokin-unit-1" "role" "pour_manipulator"
                                              "timestamp" "2026-05-26T11:00:05Z" "signature" "..."}]
                          "phase" "pour_witnessed" "completionPct" 92)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s (assoc (s* state) "phase" "record_emitted" "completionPct" 100)]
    {"melting_state" s
     "melting_attestation" {"$type" "com.etzhayyim.kanayama.meltingAttestation"
                            "lotId" (get s "lotId")
                            "chargeMassKg" (get s "chargeMassKg")
                            "furnaceTempC" (get s "furnaceTempC")
                            "degasGas" (get s "degasGas")
                            "saltFluxKg" (get s "saltFluxKg")
                            "alloyComposition" (get s "alloyComposition")
                            "pourMassKg" (get s "pourMassKg")
                            "attestingRobots" (get s "robotSignatures")
                            "recordedAt" "2026-05-26T11:00:10Z"}
     "next_node" "end"}))
