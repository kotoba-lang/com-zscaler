(ns silicon.methods.test-agent
  "silicon 珪 — agent gate tests. 1:1 port of py/test_agent.py (custom harness → clojure.test).
  Offline: §2(a)(c) force-review gate (G1), append-only lot traceability (G8), chip inalienability
  (G2)."
  (:require [clojure.test :refer [deftest is]]
            [silicon.methods.agent :as agent]))

(deftest test-litho-requires-force-review
  (is (= false (get (agent/force-review-gate "litho" nil) "allowed"))))

(deftest test-implant-denied-verdict-blocks
  (is (= false (get (agent/force-review-gate "implant" {"verdict" "deny"}) "allowed"))))

(deftest test-litho-approve-clears
  (is (= true (get (agent/force-review-gate "litho" {"verdict" "approve-with-conditions"}) "allowed"))))

(deftest test-nongated-step-runs
  (is (= true (get (agent/force-review-gate "etch" nil) "allowed"))))

(deftest test-record-step-blocked-without-review
  (let [out (agent/record-process-step {"id" "L" "history" []} "implant" "equip/x" "2026-06-02T00:00:00Z" nil)]
    (is (= true (get out "blocked")))))

(deftest test-record-step-monotonic-index
  (let [rev {"id" "fr.l" "verdict" "approve"}
        lot (-> (agent/record-process-step {"id" "L" "history" []} "litho" "e1" "t0" rev)
                (agent/record-process-step "deposition" "e2" "t1")
                (agent/record-process-step "etch" "e3" "t2"))]
    (is (= [0 1 2] (mapv #(get % "stepIndex") (get lot "history"))))
    (is (agent/lot-traceable lot))))

(deftest test-packaging-marks-verified
  (let [lot (agent/record-process-step {"id" "L" "history" []} "packaging" "e" "t" nil "ok")]
    (is (= "verified" (get lot "state")))))

(deftest test-scrap-outcome-sets-state
  (let [lot (agent/record-process-step {"id" "L" "history" []} "etch" "e" "t" nil "scrapped")]
    (is (= "scrapped" (get lot "state")))))

(deftest test-lease-requires-force-review
  (is (= true (get (agent/lease-chip {"id" "c"} "did:web:lessee" nil) "blocked"))))

(deftest test-lease-sets-lessee-not-owner
  (let [out (agent/lease-chip {"id" "c"} "did:web:lessee" "fr.x")]
    (is (= "did:web:lessee" (get out "leasedToDid")))
    (is (not (contains? out "owner")))))

(deftest test-sale-is-rejected
  (is (every? #(= false (get (agent/assert-no-transfer %) "allowed"))
              ["sell" "transfer" "burn" "set-owner" "gift"])))

(deftest test-lease-is-permitted
  (is (= true (get (agent/assert-no-transfer "lease") "allowed"))))
