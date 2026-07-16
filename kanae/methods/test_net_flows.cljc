#!/usr/bin/env bb
;; kanae 鼎 — tests for the per-endpoint fiscal net-flow aggregate.
;; Run:  bb --classpath 20-actors 20-actors/kanae/methods/test_net_flows.cljc
(ns kanae.methods.test-net-flows
  "Tests for net-flows — the per-endpoint fiscal flow balance (inflow − outflow) over the assembled
  fundFlowEdges. A non-adjudicating aggregate (G4 — a sum of disclosed amounts, no verdict) over
  fiscal-authority endpoints (G10 aggregate-first); verifies the treasury reads as a net SOURCE and
  the recipients as net RECEIVERS, ranked biggest-receiver first."
  (:require [kanae.methods.assemble-flows :as af]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private edges
  [{"fromEndpoint" {"label" "Treasury"}  "toEndpoint" {"label" "MinistryA"} "amount" "100"}
   {"fromEndpoint" {"label" "Treasury"}  "toEndpoint" {"label" "MinistryB"} "amount" "50"}
   {"fromEndpoint" {"label" "MinistryA"} "toEndpoint" {"label" "ProgramX"}  "amount" "30"}])

(deftest computes-inflow-outflow-and-net-per-endpoint
  (let [by-ep (into {} (map (juxt :endpoint identity) (af/net-flows edges)))]
    (is (= {:inflow 100.0 :outflow 30.0 :net 70.0} (select-keys (by-ep "MinistryA") [:inflow :outflow :net]))
        "MinistryA receives 100, passes on 30 → net +70")
    (is (= {:inflow 0.0 :outflow 150.0 :net -150.0} (select-keys (by-ep "Treasury") [:inflow :outflow :net]))
        "the Treasury disburses 150, receives 0 → net SOURCE −150")
    (is (= {:inflow 30.0 :outflow 0.0 :net 30.0} (select-keys (by-ep "ProgramX") [:inflow :outflow :net])))))

(deftest ranked-by-net-biggest-receiver-first
  (is (= ["MinistryA" "MinistryB" "ProgramX" "Treasury"] (mapv :endpoint (af/net-flows edges)))
      "sorted by net descending — the biggest net receiver leads, the net source last"))

(deftest empty-edges-yield-empty
  (is (= [] (af/net-flows []))))

(deftest a-non-numeric-amount-is-treated-as-zero-never-throws
  (let [out (af/net-flows [{"fromEndpoint" {"label" "A"} "toEndpoint" {"label" "B"} "amount" "bad"}])]
    (is (every? #(= 0.0 (:net %)) out) "an unparseable amount contributes 0, never throws")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kanae.methods.test-net-flows)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
