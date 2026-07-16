(ns kanayama.cells.dross-recovery.test-state-machine
  "Tests for the kanayama dross_recovery state machine (L3 cross-cutting + G14). Drives dross_collected
  → salt_cake_processed → secondary_al_recovered → k_salt_recycled → record_emitted: phase/pct
  progression, the G14 closed-loop accept flag (landfill residue < 0.5), and the emitted record."
  (:require [clojure.test :refer [deftest is]]
            [kanayama.cells.dross-recovery.state-machine :as sm]))

(deftest test-full-sequence-progresses-to-100
  (let [s0 {"dross_state" {"phase" "init" "lotId" "lot-1" "completionPct" 0}}
        s1 (sm/transition-to-dross-collected s0)
        s2 (sm/transition-to-salt-cake-processed s1)
        s3 (sm/transition-to-secondary-al-recovered s2)
        s4 (sm/transition-to-k-salt-recycled s3)
        s5 (sm/transition-to-record-emitted s4)]
    (is (= "dross_collected" (get-in s1 ["dross_state" "phase"])))
    (is (= [20 45 70 90 100]
           (mapv #(get-in % ["dross_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= 0.2 (get-in s4 ["dross_state" "landfillResidueKg"])))
    (is (= "record_emitted" (get-in s5 ["dross_state" "phase"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-record-g14-circular-accept
  (let [rec (get (-> {"dross_state" {"phase" "init" "lotId" "lot-3" "completionPct" 0}}
                     sm/transition-to-dross-collected sm/transition-to-salt-cake-processed
                     sm/transition-to-secondary-al-recovered sm/transition-to-k-salt-recycled
                     sm/transition-to-record-emitted)
                 "dross_recovery_record")]
    (is (= "etzhayyim:kanayama:drossRecoveryRecord" (get rec "$type")))
    (is (= "lot-3" (get rec "lotId")))
    (is (= true (get rec "g14CircularAccept")))     ; landfill 0.2 < 0.5
    (is (= 3.6 (get rec "secondaryAlRecoveredKg")))))
