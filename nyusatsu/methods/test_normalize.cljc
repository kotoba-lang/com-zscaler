(ns nyusatsu.methods.test-normalize
  "test_normalize.cljc — gates + OCDS mapping + dedup. ADR-2606271700."
  (:require [clojure.test :refer [deftest is testing]]
            [nyusatsu.methods.normalize :as norm]))

(def good-bid
  {":bid/ocid" "ocds-ua-2026-000001"
   ":bid/jurisdiction" "UA"
   ":bid/issuer-did" "did:web:gov.etzhayyim.com:country:ukr:health"
   ":bid/issuer-name" "Rep Agency"
   ":bid/tender-id" "UA-1"
   ":bid/title" "rep goods"
   ":bid/method" ":open"
   ":bid/status" ":active"
   ":bid/category" ":goods"
   ":bid/value-amount" 4200000
   ":bid/value-currency" "UAH"
   ":bid/tender-end" "2026-07-01"
   ":bid/source-url" "https://public.api.openprocurement.org/x"
   ":bid/source-lang" "uk"
   ":bid/sources" ["https://public.api.openprocurement.org/x"]
   ":bid/sourcing" ":representative"})

(deftest valid-bid-passes
  (is (= good-bid (norm/validate-bid good-bid))))

(deftest g1-mirror-not-author
  (testing "issuer must be present"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1"
          (norm/validate-bid (assoc good-bid ":bid/issuer-did" "")))))
  (testing "etzhayyim is never the issuer"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1"
          (norm/validate-bid (assoc good-bid ":bid/issuer-did" "did:web:etzhayyim.com:actor:nyusatsu"))))))

(deftest g2-non-adjudicating-keys-unrepresentable
  (doseq [k [":bid/winner-prediction" ":bid/bidder-score" ":bid/corruption-verdict" ":bid/risk-score"]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2"
          (norm/validate-bid (assoc good-bid k "anything")))
        (str "forbidden key " k " must be refused"))))

(deftest g3-primary-source-only
  (testing "≥1 source required"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3"
          (norm/validate-bid (assoc good-bid ":bid/sources" [])))))
  (testing "award needs ≥2 sources"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3"
          (norm/validate-bid (assoc good-bid ":bid/awarded-supplier" "X"
                                    ":bid/sources" ["https://one.example/"])))))
  (testing "paid aggregator is a prohibited citation"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3"
          (norm/validate-bid (assoc good-bid ":bid/sources" ["https://njss.info/tender/1"]))))))

(deftest g4-status-enum
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4"
        (norm/validate-bid (assoc good-bid ":bid/status" ":archived")))))

(deftest g5-no-pii
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G5"
        (norm/validate-bid (assoc good-bid ":bid/bidder-personal-address" "1 Main St"))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G5"
        (norm/validate-bid (assoc good-bid ":pii/name" "Jane")))))

(deftest g6-wellformed
  (testing "method enum"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G6"
          (norm/validate-bid (assoc good-bid ":bid/method" ":auction")))))
  (testing "currency must be ISO-4217"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G6"
          (norm/validate-bid (assoc good-bid ":bid/value-currency" "dollars")))))
  (testing "value non-negative"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G6"
          (norm/validate-bid (assoc good-bid ":bid/value-amount" -5))))))

(deftest g10-sourcing-honesty
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G10"
        (norm/validate-bid (assoc good-bid ":bid/sourcing" ":rumor")))))

(deftest ocid-mandatory
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"ocid"
        (norm/validate-bid (assoc good-bid ":bid/ocid" "")))))

;; ── OCDS release → bid ───────────────────────────────────────────────────────
(def ocds-release
  {"ocid" "ocds-xx-2026-1"
   "tender" {"id" "T-1" "title" "rep services"
             "procurementMethod" "selective" "status" "active"
             "mainProcurementCategory" "services"
             "value" {"amount" 1000 "currency" "EUR"}
             "tenderPeriod" {"startDate" "2026-06-01" "endDate" "2026-06-30"}}
   "buyer" {"name" "Rep Buyer"}
   "language" "en"})

(deftest release->bid-maps-ocds
  (let [b (norm/release->bid ocds-release
                             {:jurisdiction "EU" :issuer-did "did:web:gov.etzhayyim.com:country:eu:x"
                              :source-url "https://ted.europa.eu/n/1" :sourcing ":representative"})]
    (is (= "ocds-xx-2026-1" (get b ":bid/ocid")))
    (is (= ":selective" (get b ":bid/method")))
    (is (= ":active" (get b ":bid/status")))
    (is (= ":services" (get b ":bid/category")))
    (is (= "EUR" (get b ":bid/value-currency")))
    (is (= "EU" (get b ":bid/jurisdiction")))
    (is (some #{"https://ted.europa.eu/n/1"} (get b ":bid/sources")))))

(deftest release->bid-with-award
  (let [rel (assoc ocds-release
                   ;; an award release cites its own award-notice URL (G3: award needs ≥2 sources —
                   ;; the tender-notice (ctx) + the award-notice corroborate)
                   "sources" ["https://ted.europa.eu/award/1"]
                   "awards" [{"suppliers" [{"name" "Rep Winner"}]
                              "value" {"amount" 950 "currency" "EUR"}
                              "date" "2026-07-05"}])
        b (norm/release->bid rel
                             {:jurisdiction "EU" :issuer-did "did:web:gov.etzhayyim.com:country:eu:x"
                              :source-url "https://ted.europa.eu/n/1"
                              :sourcing ":representative"})]
    (is (= "Rep Winner" (get b ":bid/awarded-supplier")))
    (is (>= (count (get b ":bid/sources")) 2))))

(deftest dedupe-merges-by-ocid
  (let [tender {":bid/ocid" "ocds-z-1" ":bid/status" ":active" ":bid/value-amount" 100}
        award  {":bid/ocid" "ocds-z-1" ":bid/status" ":complete" ":bid/awarded-supplier" "W"}
        other  {":bid/ocid" "ocds-z-2" ":bid/status" ":active"}
        out (norm/dedupe-bids [tender award other])]
    (is (= 2 (count out)))
    (let [merged (first (filter #(= "ocds-z-1" (get % ":bid/ocid")) out))]
      (is (= ":complete" (get merged ":bid/status")) "award release supersedes tender")
      (is (= "W" (get merged ":bid/awarded-supplier"))))))
