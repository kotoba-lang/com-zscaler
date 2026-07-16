(ns kamado.cells.feedstock-guard.test-state-machine
  "Tests for the kamado feedstock_guard state machine (ADR-2606051500 port) — the defining G1 cell.
  Drives init → screened → balanced → admitted; pins the G1 fossil-feedstock refusal (the third
  enforcement point), unknown-fate refusal, screen-before-balance ordering, the carbon-balance Δ
  (parity-pinned to Python across feedstock/energy/fate combos), and the G2/D3 admittance gate."
  (:require [clojure.test :refer [deftest is]]
            [kamado.cells.feedstock-guard.state-machine :as sm]))

(defn- balance [fs en ft]
  (-> {"cell_state" {} "feedstock" fs "energy" en "fate" ft}
      sm/transition-to-screened sm/transition-to-balanced
      (get-in ["cell_state" "net_delta"])))

(deftest test-full-progression-admits
  (let [s1 (sm/transition-to-screened {"cell_state" {} "feedstock" ":biogenic" "energy" ":hikari-renewable" "fate" ":combusted-fuel"})
        s2 (sm/transition-to-balanced s1)
        s3 (sm/transition-to-admitted s2)]
    (is (= "screened" (get-in s1 ["cell_state" "phase"])))
    (is (= "biogenic" (get-in s1 ["cell_state" "feedstock"])))     ; norm stripped ':'
    (is (= true (get-in s1 ["cell_state" "screened"])))
    (is (= 0.04 (get-in s2 ["cell_state" "net_delta"])))
    (is (= true (get-in s2 ["cell_state" "passes_d3"])))
    (is (= "admitted" (get-in s3 ["cell_state" "phase"])))
    (let [p (get-in s3 ["cell_state" "payload"])]
      (is (= "biogenic" (get p "feedstockClass")))
      (is (= 0.04 (get p "netDeltaTco2ePerT")))
      (is (= true (get p "passesD3"))))))

(deftest test-g1-fossil-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                        (sm/transition-to-screened {"cell_state" {} "feedstock" ":fossil-virgin-crude"})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                        (sm/transition-to-screened {"cell_state" {} "feedstock" "crude-oil"})))
  ;; all four closed-loop feedstocks screen cleanly
  (doseq [fs ["biogenic" "captured-co2" "recycled-carbon" "existing-inventory-decommission"]]
    (is (= "screened" (get-in (sm/transition-to-screened {"cell_state" {} "feedstock" fs}) ["cell_state" "phase"])) fs)))

(deftest test-unknown-fate-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown product fate"
                        (sm/transition-to-screened {"cell_state" {} "feedstock" "biogenic" "fate" "landfill"}))))

(deftest test-balance-requires-screen
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires a passed feedstock screen first"
                        (sm/transition-to-balanced {"cell_state" {}}))))

(deftest test-carbon-balance-parity
  ;; pinned to Python net_delta (BigDecimal HALF_EVEN round-3, verified byte-for-byte)
  (is (= 0.04 (balance "biogenic" "hikari-renewable" "combusted-fuel")))
  (is (= 0.685 (balance "recycled-carbon" "grid-mixed" "combusted-fuel")))
  (is (= -3.06 (balance "captured-co2" "hikari-renewable" "durable-material")))
  (is (= 3.32 (balance "existing-inventory-decommission" "grid-mixed" "combusted-fuel"))))

(deftest test-g2-d3-admittance-gate
  ;; recycled-carbon + grid-mixed + combusted → Δ 0.685 > 0.15 → not admissible
  (let [s2 (-> {"cell_state" {} "feedstock" "recycled-carbon" "energy" "grid-mixed" "fate" "combusted-fuel"}
               sm/transition-to-screened sm/transition-to-balanced)]
    (is (= false (get-in s2 ["cell_state" "passes_d3"])))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2 violation" (sm/transition-to-admitted s2)))))
