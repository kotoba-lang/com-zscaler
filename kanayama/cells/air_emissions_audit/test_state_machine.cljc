(ns kanayama.cells.air-emissions-audit.test-state-machine
  "Tests for the kanayama air_emissions_audit state machine (G8 cross-cutting). Drives pfc_scanned →
  so2_nox_scanned → particulate_dioxin_scanned → leachate_tested → record_emitted: phase/pct
  progression, the per-stage accept flags + the all-stages overallAccept fold (incl. a fail path
  where one stage is forced rejected), and the emitted airEmissionsAuditRecord."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.air-emissions-audit.state-machine :as sm]))

(defn- run-all [s0]
  (-> s0 sm/transition-to-pfc-scanned sm/transition-to-so2-nox-scanned
      sm/transition-to-particulate-dioxin-scanned sm/transition-to-leachate-tested
      sm/transition-to-record-emitted))

(deftest test-full-sequence-progresses-to-100-and-accepts
  (let [s0 {"emissions_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-pfc-scanned s0)
        s2 (sm/transition-to-so2-nox-scanned s1)
        s3 (sm/transition-to-particulate-dioxin-scanned s2)
        s4 (sm/transition-to-leachate-tested s3)
        s5 (sm/transition-to-record-emitted s4)]
    (is (= "pfc_scanned" (get-in s1 ["emissions_state" "phase"])))
    (is (= [25 50 75 90 100]
           (mapv #(get-in % ["emissions_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= 0.0 (get-in s1 ["emissions_state" "pfcFindings" "cf4_ppm"])))   ; Wave 1 Al = no PFC
    (is (= true (get-in s5 ["emissions_state" "overallAccept"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-overall-accept-fails-when-a-stage-rejects
  ;; Force one stage rejected before the record fold; overallAccept must be false.
  (let [s4 (-> {"emissions_state" {"phase" "init" "lotId" "lot-3" "completionPct" 0}}
               sm/transition-to-pfc-scanned sm/transition-to-so2-nox-scanned
               sm/transition-to-particulate-dioxin-scanned sm/transition-to-leachate-tested)
        tainted (update s4 "emissions_state" assoc-in ["leachateFindings" "accept"] false)
        s5 (sm/transition-to-record-emitted tainted)]
    (is (= false (get-in s5 ["emissions_state" "overallAccept"])))))

(deftest test-record-carries-fields
  (let [rec (get (run-all {"emissions_state" {"phase" "init" "lotId" "lot-7" "completionPct" 0}})
                 "air_emissions_audit_record")]
    (is (= "com.etzhayyim.kanayama.airEmissionsAuditRecord" (get rec "$type")))
    (is (= "lot-7" (get rec "lotId")))
    (is (= true (get rec "overallAccept")))
    (is (= ["EU IED 2010/75/EU" "日本 大気汚染防止法" "EN 12457-2"] (get rec "regulatoryBasis")))
    (is (= "EN 12457-2" (get-in rec ["leachateFindings" "method"])))))
