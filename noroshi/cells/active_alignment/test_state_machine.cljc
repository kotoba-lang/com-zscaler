(ns noroshi.cells.active-alignment.test-state-machine
  "Tests for the noroshi active_alignment state machine (ADR-2606051600 port). Drives the safety-
  critical phase progression init → laser_safe → aligned → job_committed and pins every gate:
  N1 forbidden/unknown use refused, G5 hazardous-class interlock + attestation required (and the
  Class-1 happy path), non-negative coupling loss, G7 server-sig refused + member-sig required, and
  the emitted dry-run job payload (serverHeldKey false)."
  (:require [clojure.test :refer [deftest is]]
            [noroshi.cells.active-alignment.state-machine :as sm]))

(deftest test-full-happy-path-class1
  (let [s0 {"cell_state" {} "use" "alignment" "laser_class" "1"}
        s1 (sm/transition-verify-laser-safety s0)
        s2 (sm/transition-run-alignment (merge s1 {"coupling_loss_db" 0.8}))
        s3 (sm/transition-commit-job (merge s2 {"member_sig" "mem-sig-1"}))]
    (is (= "laser_safe" (get-in s1 ["cell_state" "phase"])))
    (is (= "run_alignment" (get s1 "next_node")))
    (is (= "aligned" (get-in s2 ["cell_state" "phase"])))
    (is (= 0.8 (get-in s2 ["cell_state" "coupling_loss_db"])))
    (is (= {"couplingLossDb" 0.8} (get-in s2 ["cell_state" "payload" "alignment"])))
    (is (= "job_committed" (get-in s3 ["cell_state" "phase"])))
    (is (= "end" (get s3 "next_node")))
    (let [job (get-in s3 ["cell_state" "payload" "job"])]
      (is (= "noroshi-aligner-01" (get job "robotId")))
      (is (= "mem-sig-1" (get job "memberSig")))
      (is (= false (get job "serverHeldKey")))
      (is (= true (get job "dryRun"))))))

(deftest test-forbidden-and-unknown-use-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"N1 violation"
                        (sm/transition-verify-laser-safety {"cell_state" {} "use" "weapon"})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"N1 violation"
                        (sm/transition-verify-laser-safety {"cell_state" {} "use" "directed-energy"})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"N1 violation"
                        (sm/transition-verify-laser-safety {"cell_state" {} "use" "mystery"}))))  ; unknown

(deftest test-hazardous-class-requires-interlock-and-attestation
  ;; Class-3B with no interlock → G5
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires a physical enclosure interlock"
                        (sm/transition-verify-laser-safety
                         {"cell_state" {} "use" "alignment" "laser_class" "3B" "interlock" false})))
  ;; interlock but no attestation → G5
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires an operator safety attestation"
                        (sm/transition-verify-laser-safety
                         {"cell_state" {} "use" "alignment" "laser_class" "4" "interlock" true "attestation_ref" ""})))
  ;; interlock + attestation → passes
  (let [s1 (sm/transition-verify-laser-safety
            {"cell_state" {} "use" "soldering" "laser_class" "3R" "interlock" true "attestation_ref" "att-9"})]
    (is (= "laser_safe" (get-in s1 ["cell_state" "phase"])))))

(deftest test-negative-coupling-loss-refused
  (let [s1 (sm/transition-verify-laser-safety {"cell_state" {} "use" "alignment"})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"coupling loss must be"
                          (sm/transition-run-alignment (merge s1 {"coupling_loss_db" -0.5}))))))

(deftest test-g7-server-sig-refused-and-member-sig-required
  (let [aligned (-> {"cell_state" {} "use" "alignment"}
                    sm/transition-verify-laser-safety
                    (as-> s (sm/transition-run-alignment (merge s {"coupling_loss_db" 0.5}))))]
    ;; server signature present → refused
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"server signature refused"
                          (sm/transition-commit-job (merge aligned {"server_sig" "srv" "member_sig" "mem"}))))
    ;; no member signature → refused
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"member/operator signature is required"
                          (sm/transition-commit-job aligned)))))
