(ns watatsumi.cells.class-certification-binder.test-state-machine
  "watatsumi 綿津見 ClassCertificationBinderCell (terminal) state-machine cljc port
  + LIVE py↔clj deep parity."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [watatsumi.cells.class-certification-binder.state-machine :as sm]))

(def ^:private start {"craftId" "WATATSUMI-RESEARCH-0001"})

(deftest chain-reaches-record-at-100pct
  (let [out (sm/run-chain start)]
    (is (= "record_emitted" (get-in out ["certification_state" "phase"])))
    (is (= 100 (get-in out ["certification_state" "completionPct"])))
    (is (= "end" (get out "next_node")))))

(deftest record-invariants
  (let [rec (get (sm/run-chain start) "class_certification_record")]
    (is (= "etzhayyim:watatsumi:classCertificationRecord" (get rec "$type")))
    (is (= "DNV-RU-UWT" (get rec "classRegime")))           ;; default regime
    (is (true? (get rec "g2Compliant")))                    ;; G2 audit-log
    (is (= 8 (count (get rec "upstreamRecords"))))          ;; all L1–L5c + emissions
    (is (= "ISSUE_CLASS_CERTIFICATE" (get-in rec ["surveyorReview" "recommend"])))
    (is (true? (get-in rec ["kotoba-datomicAnchor" "g2Compliant"])))))

(deftest class-regime-threads-from-top-level
  ;; classRegime supplied at the top-level input overrides the default
  (let [rec (get (sm/run-chain (assoc start "classRegime" "ABS-UW")) "class_certification_record")]
    (is (= "ABS-UW" (get rec "classRegime")))
    (is (= "ABS-UW" (get-in rec ["surveyorReview" "regimeReference"])))))

(def ^:private py-dir "20-actors/watatsumi/cells/class_certification_binder")

(deftest live-parity
  (testing "cljc classCertificationRecord == python (deep)"
    (let [py (sh "python3" "-c"
                 (str "import json, state_machine as sm\n"
                      "st={'certification_state':{'phase':'init','craftId':'WATATSUMI-RESEARCH-0001','completionPct':0}}\n"
                      "for fn in [sm.transition_to_records_collected, sm.transition_to_surveyor_review, "
                      "sm.transition_to_kotoba_datomic_anchored, sm.transition_to_record_emitted]:\n"
                      "    out=fn(st); st={**st, **out}\n"
                      "print(json.dumps(out['class_certification_record']))")
                 :dir py-dir)]
      (if (not (zero? (:exit py)))
        (println "  [skip] python3 unavailable:" (:err py))
        (is (= (json/parse-string (clojure.string/trim (:out py)))
               (json/parse-string (json/generate-string (get (sm/run-chain start) "class_certification_record")))))))))
