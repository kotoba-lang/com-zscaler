(ns nyusatsu.methods.test-ingest
  "test_ingest.cljc — offline OCDS package ingest + --live refusal. ADR-2606271700."
  (:require [clojure.test :refer [deftest is testing]]
            [nyusatsu.methods.ingest :as ingest]))

(def package-json
  (str "{\"releases\":["
       "{\"ocid\":\"ocds-a-1\",\"tender\":{\"id\":\"T1\",\"title\":\"rep goods a\","
       "\"procurementMethod\":\"open\",\"status\":\"active\",\"mainProcurementCategory\":\"goods\","
       "\"value\":{\"amount\":1000,\"currency\":\"UAH\"},"
       "\"tenderPeriod\":{\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-30\"}},"
       "\"buyer\":{\"name\":\"Buyer A\"},\"language\":\"uk\"},"
       ;; same ocid again with an award → must dedupe-merge to one
       "{\"ocid\":\"ocds-a-1\",\"sources\":[\"https://public.api.openprocurement.org/api/2.5/tenders/rep/award\"],"
       "\"tender\":{\"id\":\"T1\",\"title\":\"rep goods a\","
       "\"procurementMethod\":\"open\",\"status\":\"complete\",\"mainProcurementCategory\":\"goods\","
       "\"value\":{\"amount\":1000,\"currency\":\"UAH\"}},"
       "\"awards\":[{\"suppliers\":[{\"name\":\"Winner A\"}],\"value\":{\"amount\":980,\"currency\":\"UAH\"},\"date\":\"2026-07-10\"}],"
       "\"buyer\":{\"name\":\"Buyer A\"},\"language\":\"uk\"},"
       "{\"ocid\":\"ocds-a-2\",\"tender\":{\"id\":\"T2\",\"title\":\"rep works b\","
       "\"procurementMethod\":\"selective\",\"status\":\"active\",\"mainProcurementCategory\":\"works\","
       "\"value\":{\"amount\":5000,\"currency\":\"UAH\"}},"
       "\"buyer\":{\"name\":\"Buyer B\"},\"language\":\"uk\"}"
       "]}"))

(def ctx
  {:jurisdiction "UA"
   :issuer-did "did:web:gov.etzhayyim.com:country:ukr:rep"
   :source-url "https://public.api.openprocurement.org/api/2.5/tenders/rep"
   :source-lang "uk"
   :sourcing ":representative"})

(deftest parse-json-roundtrips
  (let [m (ingest/parse-json "{\"a\":1,\"b\":[true,false,null,\"x\"]}")]
    (is (= 1 (get m "a")))
    (is (= [true false nil "x"] (get m "b")))))

(deftest package->bids-normalizes-and-dedupes
  (let [bids (ingest/package->bids (ingest/parse-json package-json) ctx)]
    (testing "two distinct ocids after dedupe-merge"
      (is (= 2 (count bids)))
      (is (= ["ocds-a-1" "ocds-a-2"] (map #(get % ":bid/ocid") bids))))
    (testing "the merged ocds-a-1 carries the award + complete status"
      (let [b1 (first bids)]
        (is (= ":complete" (get b1 ":bid/status")))
        (is (= "Winner A" (get b1 ":bid/awarded-supplier")))))
    (testing "ctx-supplied jurisdiction + source-url applied"
      (is (every? #(= "UA" (get % ":bid/jurisdiction")) bids))
      (is (every? #(some #{(:source-url ctx)} (get % ":bid/sources")) bids)))))

(deftest live-is-refused
  (testing "-main with --live returns the G8 refusal code 2 (no fetch, no write)"
    (is (= 2 (apply ingest/-main ["--live"])))))
