#!/usr/bin/env bb
;; Clojure test for methods/assemble_flows.cljc — kanae 鼎 fundFlowEdge assembler.
;; (The python test_pipeline.py exercises the same assemble/validate over a danjo
;; ledger; this tests the kanae functions directly with a synthetic ledger so the
;; clj port needs no cross-actor danjo dependency.)
(ns kanae.methods.test-assemble-flows
  "Guards assemble (appropriation+outlay chain), validate-edge (required fields /
  enum / ≥2 CIDs = G5), and assert-non-adjudicating (G4 verdict-token refusal)."
  (:require [kanae.methods.assemble-flows :as af]
            [clojure.test :refer [deftest is run-tests]]))

(defn- raises? [f] (try (f) false (catch Exception _ true)))

(def LEDGER
  {"groups"
   {"g1" {"fiscalYear" 2024 "jurisdiction" "jpn" "programCode" "PC-EDU"
          "appropriations" [{"recipientName" "文部科学省" "cid" "cidA1" "amountLocal" 1000
                             "currencyIso4217" "JPY" "stateAlignedFlag" true
                             "awardDateUtc" "2024-04-01T00:00:00.000Z" "sourceUrl" "https://example/a1"}]
          "outlays" [{"recipientName" "国立大学法人" "cid" "cidO1" "amountLocal" 500
                      "currencyIso4217" "JPY" "stateAlignedFlag" false
                      "awardDateUtc" "2024-06-01T00:00:00.000Z" "sourceUrl" "https://example/o1"}]}}})

(deftest assembles-appropriation-then-outlay
  (let [edges (af/assemble LEDGER)]
    (is (= 2 (count edges)))
    (is (= "appropriation" (get (first edges) "flowClass")))
    (is (= "outlay" (get (second edges) "flowClass")))
    ;; G5: every edge grounded by ≥2 CIDs
    (is (every? #(>= (count (get % "sourceRecordCids")) 2) edges))
    ;; appropriation: treasury → ministry ; outlay grounded by its appropriation cid
    (is (= "FY2024" (get (first edges) "period")))
    (is (= ["cidO1" "cidA1"] (get (second edges) "sourceRecordCids")))))

(deftest group-without-appropriation-is-skipped
  (let [ledger {"groups" {"g0" {"fiscalYear" 2024 "jurisdiction" "jpn" "programCode" "X"
                                "appropriations" [] "outlays" []}}}]
    (is (= [] (af/assemble ledger)))))

(deftest validate-edge-requires-two-cids-g5
  (let [edges (af/assemble LEDGER)
        one-cid (assoc (first edges) "sourceRecordCids" ["only-one"])]
    (is (raises? #(af/validate-edge one-cid)))))

(deftest validate-edge-rejects-unknown-flowclass
  (let [bad (assoc (first (af/assemble LEDGER)) "flowClass" "bribe")]
    (is (raises? #(af/validate-edge bad)))))

(deftest validate-edge-requires-fields
  (let [bad (assoc (first (af/assemble LEDGER)) "currency" "")]
    (is (raises? #(af/validate-edge bad)))))

(deftest assert-non-adjudicating-refuses-verdict-token-g4
  (is (raises? #(af/assert-non-adjudicating
                 {"flowClass" "outlay"
                  "fromEndpoint" {"label" "省庁"}
                  "toEndpoint" {"label" "fraud scheme payout"}})))
  ;; 違法 (Japanese verdict token) also refused
  (is (raises? #(af/assert-non-adjudicating
                 {"flowClass" "outlay" "fromEndpoint" {"label" "違法な支出"} "toEndpoint" {"label" "x"}}))))

(deftest clean-edge-passes-non-adjudicating
  (is (nil? (af/assert-non-adjudicating (first (af/assemble LEDGER))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'kanae.methods.test-assemble-flows)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
