(ns kanayama.cells.air-emissions-audit.state-machine
  "1:1 port of cells/air_emissions_audit/state_machine.py (ADR-2605252400 G8 cross-cutting).
  Continuous PFC / SO₂ / NOx / particulate / dioxin / VOC stack monitoring vs EU IED 2010/75/EU +
  日本大気汚染防止法 + EN 12457 leachate: pfc_scanned → so2_nox_scanned → particulate_dioxin_scanned →
  leachate_tested → record_emitted, with overallAccept = (every stage accept is true). EmissionsState
  dataclass → string-keyed map under \"emissions_state\"."
  (:require [clojure.string]))

(defn- s* [state] (get state "emissions_state" {}))

(defn transition-to-pfc-scanned [state]
  {"emissions_state" (assoc (s* state)
                            "pfcFindings" {"cf4_ppm" 0.0 "c2f6_ppm" 0.0 "gwp_co2eq_kg" 0.0 "accept" true}
                            "phase" "pfc_scanned" "completionPct" 25)
   "next_node" "so2_nox"})

(defn transition-to-so2-nox-scanned [state]
  {"emissions_state" (assoc (s* state)
                            "so2NoxFindings" {"so2_mg_per_nm3" 18 "so2_limit_mg_per_nm3" 50
                                              "nox_mg_per_nm3" 95 "nox_limit_mg_per_nm3" 200 "accept" true}
                            "phase" "so2_nox_scanned" "completionPct" 50)
   "next_node" "particulate"})

(defn transition-to-particulate-dioxin-scanned [state]
  {"emissions_state" (assoc (s* state)
                            "particulateDioxinFindings" {"pm10_mg_per_nm3" 4.2 "pm10_limit_mg_per_nm3" 10
                                                         "dioxin_ng_TEQ_per_nm3" 0.04 "dioxin_limit_ng_TEQ_per_nm3" 0.1
                                                         "voc_mg_per_nm3" 11 "voc_limit_mg_per_nm3" 20 "accept" true}
                            "phase" "particulate_dioxin_scanned" "completionPct" 75)
   "next_node" "leachate"})

(defn transition-to-leachate-tested [state]
  {"emissions_state" (assoc (s* state)
                            "leachateFindings" {"method" "EN 12457-2"
                                                "pb_mg_per_l" 0.02 "pb_limit_mg_per_l" 0.5
                                                "cd_mg_per_l" 0.001 "cd_limit_mg_per_l" 0.04 "accept" true}
                            "phase" "leachate_tested" "completionPct" 90)
   "next_node" "record"})

(defn transition-to-record-emitted [state]
  (let [s0 (s* state)
        overall-accept (every? #(= true (get (or (get s0 %) {}) "accept"))
                               ["pfcFindings" "so2NoxFindings" "particulateDioxinFindings" "leachateFindings"])
        s (assoc s0 "overallAccept" overall-accept "phase" "record_emitted" "completionPct" 100)]
    {"emissions_state" s
     "air_emissions_audit_record" {"$type" "com.etzhayyim.kanayama.airEmissionsAuditRecord"
                                   "lotId" (get s "lotId")
                                   "pfcFindings" (get s "pfcFindings")
                                   "so2NoxFindings" (get s "so2NoxFindings")
                                   "particulateDioxinFindings" (get s "particulateDioxinFindings")
                                   "leachateFindings" (get s "leachateFindings")
                                   "overallAccept" overall-accept
                                   "regulatoryBasis" ["EU IED 2010/75/EU" "日本 大気汚染防止法" "EN 12457-2"]
                                   "recordedAt" "2026-05-26T17:00:00Z"}
     "next_node" "end"}))
