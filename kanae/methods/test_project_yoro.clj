#!/usr/bin/env bb
;; Clojure test for methods/project_yoro.cljc — kanae 鼎 yoro projection.
;; Feeds the assemble-flows output (same pipeline as python test_pipeline.py) and
;; checks the projected :yoro.fiscal/* + :yoro.profile/* datoms.
(ns kanae.methods.test-project-yoro
  "Guards project (9 fiscal datoms per edge + label→mirror-actor slug resolution:
  一般会計→kokko, 文部科学省→gov-jp-mext), stage tiers, and the minimal profile
  datoms emitted once per known endpoint."
  (:require [kanae.methods.project-yoro :as py]
            [kanae.methods.assemble-flows :as af]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def LEDGER
  {"groups"
   {"g1" {"fiscalYear" 2024 "jurisdiction" "jpn" "programCode" "PC-EDU"
          "appropriations" [{"recipientName" "文部科学省" "cid" "cidA1" "amountLocal" 1000
                             "currencyIso4217" "JPY" "stateAlignedFlag" true
                             "awardDateUtc" "2024-04-01T00:00:00.000Z" "sourceUrl" "https://example/a1"}]
          "outlays" [{"recipientName" "国立大学法人" "cid" "cidO1" "amountLocal" 500
                      "currencyIso4217" "JPY" "stateAlignedFlag" false
                      "awardDateUtc" "2024-06-01T00:00:00.000Z" "sourceUrl" "https://example/o1"}]}}})

(defn- datoms [] (py/project (af/assemble LEDGER)))
(defn- by-attr [ds a] (filter #(= (get % "a") a) ds))

(deftest projects-nine-fiscal-datoms-per-edge
  (let [ds (datoms)
        edges (af/assemble LEDGER)
        fiscal-from (by-attr ds ":yoro.fiscal/from")]
    ;; one :from per edge
    (is (= (count edges) (count fiscal-from)))
    ;; 9 fiscal attrs per fiscal entity
    (let [e (get (first fiscal-from) "e")
          for-e (filter #(and (= (get % "e") e) (str/starts-with? (get % "a") ":yoro.fiscal/")) ds)]
      (is (= 9 (count for-e))))))

(deftest label-resolves-to-mirror-actor-dids
  (let [ds (datoms)
        froms (set (map #(get % "v_edn") (by-attr ds ":yoro.fiscal/from")))
        tos   (set (map #(get % "v_edn") (by-attr ds ":yoro.fiscal/to")))]
    ;; treasury (一般会計) → kokko ; ministry (文部科学省) → gov-jp-mext
    (is (some #(str/includes? % "kokko") froms))
    (is (some #(str/includes? % "gov-jp-mext") tos))))

(deftest stage-tier-from-source-label
  (let [ds (datoms)
        stages (set (map #(get % "v_edn") (by-attr ds ":yoro.fiscal/stage")))]
    ;; treasury source → L7
    (is (contains? stages "\"L7\""))))

(deftest fiscal-year-and-amount-typed-as-numbers
  (let [ds (datoms)
        fy (first (by-attr ds ":yoro.fiscal/fiscalYear"))
        amt (first (by-attr ds ":yoro.fiscal/amountJpy"))]
    (is (= "2024" (get fy "v_edn")))       ; FY2024 → 2024 (number, no quotes)
    (is (= "1000" (get amt "v_edn")))))

(deftest profile-datoms-emitted-once-for-known-endpoint
  (let [ds (datoms)
        prof-dids (by-attr ds ":yoro.profile/did")]
    ;; gov-jp-mext has a defined profile; emitted exactly once even though it appears
    ;; as both an outlay 'from' and an appropriation 'to'
    (is (= 1 (count prof-dids)))
    (is (str/includes? (get (first prof-dids) "v_edn") "gov-jp-mext"))))

(deftest unknown-label-falls-back-to-deterministic-slug
  (let [ds (py/project [{"fromEndpoint" {"label" "Some Unlisted Agency"}
                         "toEndpoint" {"label" "Another One"}
                         "period" "FY2025" "flowClass" "outlay" "amount" "7"}])
        froms (map #(get % "v_edn") (by-attr ds ":yoro.fiscal/from"))]
    (is (some #(str/includes? % "gov-jp-some-unlisted-agency") froms))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'kanae.methods.test-project-yoro)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
