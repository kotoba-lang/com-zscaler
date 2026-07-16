(ns yobel.cells.rite-declaration.tests.test-cell
  "Tests for RiteDeclarationCell — input validation, Charter Rider §2 gate, Council DMN + ratification.

  Clojure port of tests/test_cell.py (pytest) onto clojure.test + langgraph-clj."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [langgraph.checkpoint :as cp]
            [langgraph.graph :as g]
            [yobel.cells.rite-declaration.cell :as cell]
            [yobel.ports :as ports]))

;; ─── Stub ports (conftest.py fakes) ──────────────────────────────────

(defn stub-council-sbt [levels]
  (reify ports/CouncilSbtPort
    (balance-of-level [_ did] (get levels did 0))
    (entity-type-of [_ _did] "unknown")))

(defn stub-charter-compliance [aligned]
  (reify ports/CharterCompliancePort
    (is-aligned [_ did] (contains? aligned did))
    (jurisdiction-of [_ _did] "ALL")))

(defn stub-council-ratification
  "decision = ProposalDecision-shaped map/record; proposals atom records submissions."
  [decision proposals]
  (reify ports/CouncilRatificationPort
    (submit-proposal [_ topic rite-id rite-type required-lv6-plus-count
                      required-lv9-chair-count required-quorum-pct
                      additional-gates doctrinal-basis scope]
      (swap! proposals conj {:topic topic
                             :rite-id rite-id
                             :rite-type rite-type
                             :required-lv6-plus-count required-lv6-plus-count
                             :required-lv9-chair-count required-lv9-chair-count
                             :required-quorum-pct required-quorum-pct
                             :additional-gates additional-gates
                             :doctrinal-basis doctrinal-basis
                             :scope scope})
      (str "at://fake/proposal/" rite-id))
    (await-decision [_ _proposal-uri _timeout-days] decision)))

(defn stub-land-registry [overlapping]
  (reify ports/LandRegistryPort
    (find-overlapping-tenures [_ _scope] overlapping)))

(defn stub-anchor-bridge
  "Records writes into the given atom; write maps shaped
  {:collection .. :rkey .. :payload {..} :anchor-to-base-l2 bool}."
  [writes]
  (reify ports/AnchorBridgePort
    (write-and-anchor [_ collection rkey payload anchor-to-base-l2]
      (swap! writes conj {:collection collection
                          :rkey rkey
                          :payload payload
                          :anchor-to-base-l2 anchor-to-base-l2})
      (ports/->AnchorResult (str "at://fake/" collection "/" rkey)
                            (if anchor-to-base-l2 "0xfake-anchor" "")))
    (batched-anchor [_ _contract cids]
      (ports/->BatchedAnchorResult (str "0xfake-batch-" (count cids))))))

;; ─── validate-input ──────────────────────────────────────────────────

(def ^:private valid-base
  {:rite-type "shmita_7yr"
   :doctrinal-basis "Lev 25:1-7"
   :scope "etzhayyim community"
   :effective-date "2026-09-26T00:00:00Z"})

(deftest test-validate-input-happy
  (is (= {:input-valid true :input-errors []}
         (cell/validate-input valid-base))))

(deftest test-validate-input-rejects-missing-or-bogus
  (doseq [[patch expected-error-substring]
          [[{:rite-type "bogus"} "invalid riteType"]
           [{:doctrinal-basis ""} "doctrinalBasis required"]
           [{:scope ""} "scope required"]
           [{:effective-date ""} "effectiveDate required"]]]
    (testing (str patch)
      (let [out (cell/validate-input (merge valid-base patch))]
        (is (false? (:input-valid out)))
        (is (some #(str/includes? % expected-error-substring)
                  (:input-errors out)))))))

;; ─── charter-rider-gate ──────────────────────────────────────────────

(deftest test-charter-rider-gate-accepts-normal-scope
  (let [out (cell/charter-rider-gate {:scope "etzhayyim community small loans"} nil)]
    (is (true? (:charter-rider-compliant out)))
    (is (= [] (:charter-rider-violations out)))))

(deftest test-charter-rider-gate-rejects-military-without-disclosure
  (let [out (cell/charter-rider-gate
             {:scope "military debt forgiveness for veterans"} nil)]
    (is (false? (:charter-rider-compliant out)))
    (is (some #(str/includes? % "§2(a) military")
              (:charter-rider-violations out)))))

(deftest test-charter-rider-gate-accepts-military-with-disclosure
  (let [out (cell/charter-rider-gate
             {:scope "military debt forgiveness with transparent-force-rd disclosure"} nil)]
    (is (true? (:charter-rider-compliant out)))))

(deftest test-charter-rider-gate-rejects-speculative-keywords
  (let [out (cell/charter-rider-gate {:scope "margin trading loss release"} nil)]
    (is (false? (:charter-rider-compliant out)))
    (is (some #(str/includes? % "§2(b)")
              (:charter-rider-violations out)))))

;; ─── council-ratification-dmn ────────────────────────────────────────

(deftest test-council-dmn-baseline
  (let [out (cell/council-ratification-dmn {:rite-type "shmita_7yr"})]
    (is (= 3 (:required-lv6-plus-count out)))
    (is (= 1 (:required-lv9-chair-count out)))
    (is (= 50 (:required-quorum-pct out)))))

(deftest test-council-dmn-yobel-50yr-adds-land-gate
  (let [out (cell/council-ratification-dmn {:rite-type "yobel_50yr"})]
    (is (= 5 (:required-lv6-plus-count out)))
    (is (= 60 (:required-quorum-pct out)))
    (is (some #{"land-sovereignty-coordination"} (:additional-gates out)))))

(deftest test-council-dmn-political-amnesty-aggregates-all-gates
  (let [out (cell/council-ratification-dmn {:rite-type "political_amnesty"})]
    (is (= 6 (:required-lv6-plus-count out)))   ;; B1 3 + R5 +3
    (is (= 2 (:required-lv9-chair-count out)))  ;; B1 1 + R5 +1
    (is (= 70 (:required-quorum-pct out)))      ;; B1 50 + R5 +20
    (is (some #{"transparent-force-rd-disclosure"} (:additional-gates out)))
    (is (some #{"council-five-bootstrap-consultation"} (:additional-gates out)))))

(deftest test-council-dmn-tokusei-adds-jurisdiction-gate
  (let [out (cell/council-ratification-dmn {:rite-type "tokusei_rei"})]
    (is (= 4 (:required-lv6-plus-count out)))
    (is (= 2 (:required-lv9-chair-count out)))
    (is (some #{"jurisdiction-claim-coordination"} (:additional-gates out)))))

(deftest test-council-dmn-quorum-cap-100pct
  ;; Multiple rules cannot push quorum > 100%.
  (let [out (cell/council-ratification-dmn {:rite-type "political_amnesty"})]
    (is (<= 0 (:required-quorum-pct out) 100))))

;; ─── End-to-end via build-graph ──────────────────────────────────────

(deftest test-e2e-happy-path-shmita
  (let [writes (atom [])
        proposals (atom [])
        graph (cell/build-graph
               {:checkpointer (cp/mem-checkpointer)
                :charter-compliance-port (stub-charter-compliance #{})
                :council-sbt-port (stub-council-sbt
                                   {"did:web:etzhayyim.com:steward-001" 6}) ; SBT Lv6
                :council-ratification-port
                (stub-council-ratification
                 (ports/->ProposalDecision true ["did:web:council/lv9-chair"
                                                 "did:web:council/lv6-a"
                                                 "did:web:council/lv6-b"
                                                 "did:web:council/lv6-c"])
                 proposals)
                :land-registry-port (stub-land-registry [])
                :anchor-bridge (stub-anchor-bridge writes)})
        initial {:rite-id "shmita-5786"
                 :rite-type "shmita_7yr"
                 :scope "etzhayyim community small monetary debts"
                 :effective-date "2026-09-26T00:00:00Z"
                 :doctrinal-basis "Lev 25:1-7 / Deut 15:1-2"
                 :issuer-did "did:web:etzhayyim.com:steward-001"}
        result (g/invoke graph initial {:thread-id "test-shmita"})]
    (is (= "active" (:rite-status result)))
    (is (true? (:council-ratified result)))
    (is (= 1 (count @writes)))
    (is (= "com.etzhayyim.apps.etzhayyim.yobel.rite"
           (:collection (first @writes))))))

(deftest test-e2e-council-rejection-cancels-rite
  (let [writes (atom [])
        proposals (atom [])
        graph (cell/build-graph
               {:checkpointer (cp/mem-checkpointer)
                :charter-compliance-port (stub-charter-compliance #{})
                :council-sbt-port (stub-council-sbt
                                   {"did:web:etzhayyim.com:steward-001" 6})
                :council-ratification-port
                (stub-council-ratification (ports/->ProposalDecision false []) proposals)
                :land-registry-port (stub-land-registry [])
                :anchor-bridge (stub-anchor-bridge writes)})
        result (g/invoke graph
                         {:rite-id "shmita-5786"
                          :rite-type "shmita_7yr"
                          :scope "etzhayyim community"
                          :effective-date "2026-09-26T00:00:00Z"
                          :doctrinal-basis "Lev 25"
                          :issuer-did "did:web:etzhayyim.com:steward-001"}
                         {:thread-id "test-reject"})]
    (is (= "cancelled" (:rite-status result)))
    ;; No anchor write should have happened
    (is (= 0 (count @writes)))))
