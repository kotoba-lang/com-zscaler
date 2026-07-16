(ns kamado.methods.test-decommission-robot
  "Tests for kamado decommission-robot hot-zone purge + entry loop (ADR-2606051500).
  1:1 Clojure port of methods/test_decommission_robot.py (clojure.test). Every assertion
  is ported — especially the H₂S/benzene purge-to-entry gate + the entry-refused
  SafetyError crux + the civilian / no-server-key / witness-safety gates.

  Run from $W:
    bb --classpath 20-actors -e \"(require 'kamado.methods.test-decommission-robot 'clojure.test) \\
       (clojure.test/run-tests 'kamado.methods.test-decommission-robot)\""
  (:require [clojure.test :refer [deftest is run-tests]]
            [kamado.methods.substrate :as sub]
            [kamado.methods.decommission-robot :as dr
             :refer [BENZENE-ENTRY-PPM H2S-ENTRY-PPM plan-cut-entry purge-to-entry to-datoms]]))

(def WITNESS
  ["did:web:etzhayyim.com:kuniumi:robot:kamado-arm-01"
   "did:web:etzhayyim.com:kuniumi:robot:kamado-mimi-01"])

;; ── purge loop: drives hazardous gas below the entry limit ───────────────────

(deftest test-purge-converges-below-h2s-entry-limit
  (let [res (purge-to-entry :gas "H2S" :target-ppm H2S-ENTRY-PPM)]
    (is (true? (:entry-permitted res)))
    (is (<= (:final-ppm res) (+ H2S-ENTRY-PPM 1e-6)))
    (is (< (:final-ppm res) (:initial-ppm res)))
    (is (> (:settling-seconds res) 0))
    (is (true? (:representative res)))))

(deftest test-purge-converges-below-benzene-entry-limit
  (let [res (purge-to-entry :gas "benzene" :target-ppm BENZENE-ENTRY-PPM :initial-ppm 80.0)]
    (is (true? (:entry-permitted res)))
    (is (<= (:final-ppm res) (+ BENZENE-ENTRY-PPM 1e-6)))))

(deftest test-purge-too-weak-to-beat-leak-does-not-permit-entry
  ;; A purge flow too small to overcome the residual ingress can never hold the
  ;; zone below the limit — entry must NOT be permitted.
  (let [res (purge-to-entry :gas "H2S" :target-ppm H2S-ENTRY-PPM
                            :max-flow 1.0 :leak 2.0 :k-purge 0.2)]
    (is (false? (:entry-permitted res)))
    (is (> (:final-ppm res) H2S-ENTRY-PPM))))

;; ── entry gate: the safety crux ──────────────────────────────────────────────

(deftest test-entry-refused-when-concentration-above-limit
  ;; Robot must NOT enter an un-purged zone — structural SafetyError, not a warning.
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"ENTRY REFUSED"
                        (plan-cut-entry :target-xy [1.5 0.4]
                                        :final-ppm 50.0
                                        :gas-limit H2S-ENTRY-PPM
                                        :member-sig "m:ed25519:demo"
                                        :witness-sigs WITNESS)))
  ;; and it is specifically a SafetyError (matches Python `except SafetyError`).
  (is (sub/safety-error?
       (try (plan-cut-entry :target-xy [1.5 0.4] :final-ppm 50.0 :gas-limit H2S-ENTRY-PPM
                            :member-sig "m:ed25519:demo" :witness-sigs WITNESS)
            nil
            (catch clojure.lang.ExceptionInfo e e)))))

(deftest test-cut-plan-reachable-and-safe-when-purged
  (let [plan (plan-cut-entry :target-xy [1.5 0.4]
                             :final-ppm 8.0 ; below the 10 ppm entry limit
                             :gas-limit H2S-ENTRY-PPM
                             :member-sig "m:ed25519:demo"
                             :witness-sigs WITNESS)]
    (is (true? (get plan ":entryPermitted")))
    (is (true? (get plan ":reachable")))
    (is (true? (get plan ":envelopeOk")))
    (is (true? (get plan ":witnessOk")))
    (is (false? (get plan ":serverHeldKey")))
    (is (true? (get plan ":dryRun")))
    ;; G9: freed hot-zone worker routes to the Basic-High-Income cohort.
    (is (clojure.string/starts-with? (get plan ":displacementCohortRef") "bhi:"))))

;; ── structural gates ─────────────────────────────────────────────────────────

(deftest test-non-civilian-use-refused-on-purge
  (doseq [use ["weapon" "fire-control" "mining"]]
    (is (sub/safety-error?
         (try (purge-to-entry :gas "H2S" :target-ppm H2S-ENTRY-PPM :use use)
              nil
              (catch clojure.lang.ExceptionInfo e e)))
        (str "use " use " must be refused"))))

(deftest test-fossil-life-extension-use-refused
  ;; G3: a fossil life-extension verb is not in the civilian allowlist; closed-world
  ;; refusal rejects it before any motion is planned.
  (doseq [use ["restart-fossil" "expand" "throughput-revamp"]]
    (is (sub/safety-error?
         (try (plan-cut-entry :target-xy [1.5 0.4] :final-ppm 8.0 :gas-limit H2S-ENTRY-PPM
                              :member-sig "m:sig" :witness-sigs WITNESS :use use)
              nil
              (catch clojure.lang.ExceptionInfo e e)))
        (str "use " use " must be refused"))))

(deftest test-server-signature-refused
  ;; G5/G7: a platform-held signature is a structural violation. The zone is purged
  ;; (final-ppm below limit) so the entry gate passes and we reach the key gate.
  (is (sub/safety-error?
       (try (plan-cut-entry :target-xy [1.5 0.4] :final-ppm 8.0 :gas-limit H2S-ENTRY-PPM
                            :member-sig "m:sig" :witness-sigs WITNESS :server-sig "s:sig")
            nil
            (catch clojure.lang.ExceptionInfo e e)))))

(deftest test-missing-member-signature-refused
  (is (sub/safety-error?
       (try (plan-cut-entry :target-xy [1.5 0.4] :final-ppm 8.0 :gas-limit H2S-ENTRY-PPM
                            :member-sig "" :witness-sigs WITNESS)
            nil
            (catch clojure.lang.ExceptionInfo e e)))))

(deftest test-witness-below-quorum-recorded-not-raised
  ;; G8 quorum miss is a Council-escalation record, not a raise (mirrors hikari).
  (let [plan (plan-cut-entry :target-xy [1.5 0.4] :final-ppm 8.0 :gas-limit H2S-ENTRY-PPM
                             :member-sig "m:sig" :witness-sigs ["did:r:only-one"])]
    (is (false? (get plan ":witnessOk")))
    (is (true? (get plan ":escalateCouncilLv6")))))

;; ── datoms projection ────────────────────────────────────────────────────────

(deftest test-datoms-are-aggregate-and-dry-run
  (let [purge (purge-to-entry :gas "H2S" :target-ppm H2S-ENTRY-PPM)
        plan (plan-cut-entry :target-xy [1.5 0.4] :final-ppm (:final-ppm purge)
                             :gas-limit H2S-ENTRY-PPM :member-sig "m:sig" :witness-sigs WITNESS)
        d (to-datoms purge plan :job-id "decom-001" :refinery "rf.jp.muroran")]
    (is (true? (get d ":decommission/dry-run")))
    (is (false? (get d ":decommission/server-held-key")))
    (is (true? (get d ":decommission/entry-permitted")))
    (is (true? (get d ":decommission/representative")))
    (is (clojure.string/starts-with? (get d ":decommission/displacement-cohort-ref") "bhi:"))))

#?(:clj (defn -main [& _] (run-tests 'kamado.methods.test-decommission-robot)))
