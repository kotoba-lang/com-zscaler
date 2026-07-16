(ns mizuho.cells.test-state-machines
  "Tests for mizuho gated cell state machines.
  1:1 port of cells/test_state_machines.py (ADR-2605263100).

  water_supply: supply commissioning + dosing + member-signed dry-run record, with
  N1 civilian-use / G3 community-scale cap / G6 fluoride-consent / G7 no-server-key /
  G8 witness quorum refusals (the cljc safety refusals throw ex-info, not a SafetyError
  class). The test_cell_solve_stays_gated test is dropped (cell.py is not ported)."
  (:require [clojure.test :refer [deftest is]]
            [mizuho.cells.water-supply.state-machine :as sm]))

(def WITNESS
  ["did:web:etzhayyim.com:kuniumi:robot:tsutsu-01"
   "did:web:etzhayyim.com:kuniumi:robot:shizuku-01"])

;; ─── water_supply ────────────────────────────────────────────────────

(deftest test-supply-happy-path-commits-dry-run-record
  (let [s1 (sm/transition-commission {"demand_step_lps" 20.0 "service_population" 200})]
    (is (= sm/phase-commissioned (get-in s1 ["cell_state" "phase"])))
    (is (= true (get-in s1 ["cell_state" "level_restored"])))
    (is (= true (get-in s1 ["cell_state" "ceiling_respected"])))
    (let [s1 (assoc s1 "member_sig" "m:ed25519:demo" "witness_sigs" WITNESS)
          s2 (sm/transition-commit-supply s1)
          rec (get-in s2 ["cell_state" "payload" "supply_record"])]
      (is (= sm/phase-supply-committed (get-in s2 ["cell_state" "phase"])))
      (is (= false (get rec "serverHeldKey")))
      (is (= true (get rec "dryRun")))
      (is (= true (get rec "witnessOk"))))))

(deftest test-supply-non-civilian-use-raises
  (is (thrown? clojure.lang.ExceptionInfo
               (sm/transition-commission {"use" "weapon" "demand_step_lps" 20.0}))))

(deftest test-supply-over-cap-raises-g3
  (is (thrown? clojure.lang.ExceptionInfo
               (sm/transition-commission {"demand_step_lps" 20.0 "service_population" 9999}))))

(deftest test-supply-fluoride-without-consent-raises-g6
  (is (thrown? clojure.lang.ExceptionInfo
               (sm/transition-commission {"demand_step_lps" 20.0 "dosing_agent" "fluoridate"}))))

(deftest test-supply-fluoride-with-consent-commissions
  (let [s1 (sm/transition-commission
            {"demand_step_lps" 20.0 "dosing_agent" "fluoridate" "per_member_consent" true})]
    (is (= sm/phase-commissioned (get-in s1 ["cell_state" "phase"])))
    (is (= true (get-in s1 ["cell_state" "residual_held"])))))

(deftest test-supply-server-signature-refused
  (let [s1 (sm/transition-commission {"demand_step_lps" 20.0})
        s1 (assoc s1 "member_sig" "m:sig" "server_sig" "s:sig" "witness_sigs" WITNESS)]
    (is (thrown? clojure.lang.ExceptionInfo (sm/transition-commit-supply s1)))))

(deftest test-supply-witness-below-quorum-blocks-commit
  (let [s1 (sm/transition-commission {"demand_step_lps" 20.0})
        s1 (assoc s1 "member_sig" "m:sig" "witness_sigs" ["did:r:a"])]
    (is (thrown? clojure.lang.ExceptionInfo (sm/transition-commit-supply s1)))))
