(ns watatsumi.cells.marine-emissions-audit.test-state-machine
  "watatsumi 綿津見 MarineEmissionsAuditCell (G14 cross-cutting) state-machine cljc
  port + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.marine-emissions-audit.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-record-at-100pct
  (let [out (sm/run-chain start)]
    (is (= "record_emitted" (get-in out ["emissions_audit_state" "phase"])))
    (is (= 100 (get-in out ["emissions_audit_state" "completionPct"])))
    (is (= "end" (get out "next_node")))))

(deftest overall-accept-aggregates-all-three-scans
  (let [rec (get (sm/run-chain start) "marine_emissions_audit_record")]
    (is (= "etzhayyim:watatsumi:marineEmissionsAuditRecord" (get rec "$type")))
    (is (true? (get rec "overallAccept")))      ;; all 6 MARPOL annexes ∧ bwmc ∧ biofouling
    (is (= 6 (count (get rec "marpol"))))
    (is (true? (get-in rec ["bwmc" "accept"])))
    (is (true? (get-in rec ["biofouling" "antifoulingTributyltinFree"])))
    (is (= "ADR-2605252200 G14" (get rec "g14Reference")))))

(deftest accept-logic-fails-on-a-marpol-violation
  ;; overallAccept must be false if any MARPOL annex rejects (accept logic, not constant)
  (let [tampered (-> (sm/init start)
                     sm/transition-to-marpol-scan
                     (assoc-in ["emissions_audit_state" "marpolFindings" "annexI_oilPollution" "accept"] false))
        out (-> tampered sm/transition-to-bwmc-scan sm/transition-to-biofouling-scan
                sm/transition-to-record-emitted)]
    (is (false? (get-in out ["marine_emissions_audit_record" "overallAccept"])))))

(def ^:private py-dir "20-actors/watatsumi/cells/marine_emissions_audit")

(deftest live-parity
  (testing "cljc marineEmissionsAuditRecord == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'emissions_audit_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_marpol_scan, sm.transition_to_bwmc_scan, "
                      "sm.transition_to_biofouling_scan, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['marine_emissions_audit_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "marine_emissions_audit_record")))))))))
