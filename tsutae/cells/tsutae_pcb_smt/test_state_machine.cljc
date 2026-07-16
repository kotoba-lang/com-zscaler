(ns tsutae.cells.tsutae-pcb-smt.test-state-machine
  "Tests for the tsutae pcb_smt state machine (ADR-2605261300 port). Drives components_sourced →
  soc_guard_checked → smt_placed → aoi_passed → attestation_emitted: phase/pct progression, the
  G9 open-SoC guard (open RISC-V accepted, proprietary rejected by construction), and the emitted
  pcbAttestation accept flag (socGuard ∧ aoi)."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-pcb-smt.state-machine :as sm]))

(defn- run-all [soc]
  ;; re-supply the top-level "soc" input across steps (the langgraph framework persists graph
  ;; state across nodes; standalone threading must carry it to the soc-guard step)
  (-> {"pcb_state" {"phase" "init" "boardId" "board-1" "completionPct" 0} "soc" soc}
      sm/transition-to-components-sourced
      (assoc "soc" soc)
      sm/transition-to-soc-guard-checked
      sm/transition-to-smt-placed
      sm/transition-to-aoi-passed
      sm/transition-to-attestation-emitted))

(deftest test-full-progression-open-soc
  (let [s0 {"pcb_state" {"phase" "init" "boardId" "board-1" "completionPct" 0} "soc" "StarFive-JH7110"}
        s1 (sm/transition-to-components-sourced s0)
        s2 (sm/transition-to-soc-guard-checked (assoc s1 "soc" "StarFive-JH7110"))
        s3 (sm/transition-to-smt-placed s2)
        s4 (sm/transition-to-aoi-passed s3)
        s5 (sm/transition-to-attestation-emitted s4)]
    (is (= "components_sourced" (get-in s1 ["pcb_state" "phase"])))
    (is (= [15 30 60 85 100] (mapv #(get-in % ["pcb_state" "completionPct"]) [s1 s2 s3 s4 s5])))
    (is (= 4 (count (get-in s1 ["pcb_state" "components"]))))
    (is (= true (get-in s2 ["pcb_state" "socGuard" "accept"])))
    (is (= "open RISC-V SoC verified" (get-in s2 ["pcb_state" "socGuard" "reason"])))
    (is (= "end" (get s5 "next_node")))))

(deftest test-g9-open-soc-variants-accepted
  (doseq [soc ["StarFive-JH7110" "SiFive-HiFive-Unmatched" "Allwinner-D1" "iwakura"]]
    (is (= true (get-in (sm/transition-to-soc-guard-checked
                         {"pcb_state" {"phase" "init" "boardId" "b" "completionPct" 0} "soc" soc})
                        ["pcb_state" "socGuard" "accept"])) soc)))

(deftest test-g9-proprietary-soc-rejected
  (doseq [soc ["Snapdragon-8" "Apple-A17" "Exynos-2400" "Helio-G99" "Dimensity-9000"]]
    (let [sg (get-in (sm/transition-to-soc-guard-checked
                      {"pcb_state" {"phase" "init" "boardId" "b" "completionPct" 0} "soc" soc})
                     ["pcb_state" "socGuard"])]
      (is (= false (get sg "accept")) soc)
      (is (= "proprietary/closed SoC rejected (§2(b) N1 invariant)" (get sg "reason")) soc))))

(deftest test-attestation-accept-reflects-guards
  ;; open SoC + AOI pass → accept true
  (is (= true (get-in (run-all "StarFive-JH7110") ["pcb_attestation" "accept"])))
  ;; proprietary SoC → socGuard rejects → attestation accept false (even though AOI passes)
  (let [rec (get (run-all "Snapdragon-8") "pcb_attestation")]
    (is (= false (get rec "accept")))
    (is (= "com.etzhayyim.tsutae.pcbAttestation" (get rec "$type")))
    (is (= "board-1" (get rec "boardId")))))

(deftest test-unknown-soc-rejected
  ;; not on either list → not open → rejected (open allow-list is closed-world)
  (is (= false (get-in (sm/transition-to-soc-guard-checked
                        {"pcb_state" {"phase" "init" "boardId" "b" "completionPct" 0} "soc" "MysteryChip-1"})
                       ["pcb_state" "socGuard" "accept"]))))
