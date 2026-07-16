;; matsurigoto 政 — tax-collect / Datom emit + module facade の conformance test。ADR-2606062300。
(ns matsurigoto.tax-collect.test-datom-emit
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [matsurigoto.tax-collect.payment :as p]
            [matsurigoto.tax-collect.datom-emit :as d]
            [matsurigoto.tax-collect.tax-collect :as tc]))

(def ^:private SLIP
  (p/build-remittance-slip
   {:slip-type :salary-retirement-general :pay-year 2026 :pay-month 1
    :operated-by ":etzhayyim-council"
    :lines [{:区分 "俸給・給料等" :人員 3 :支給額 900000 :税額 21132}
            {:区分 "税理士等の報酬" :人員 1 :支給額 300000 :税額 30630}]}))

(deftest datoms-are-eavt-append-only
  (let [ds (d/remittance-datoms SLIP 7)]
    (is (every? #(= 5 (count %)) ds) "全 datom は [e a v tx op]")
    (is (every? #(= :add (nth % 4)) ds) "G5: append-only, op=:add のみ")
    (is (every? #(= 7 (nth % 3)) ds) "tx が伝播")
    (testing "ヘッダ datom"
      (let [by-attr (into {} (map (fn [[_ a v]] [a v]) ds))]
        (is (= :salary-retirement-general (by-attr :gensen.remit/slip-type)))
        (is (= 51762 (by-attr :gensen.remit/total-tax)) "21132+30630")
        (is (= "2026-02-10" (by-attr :gensen.remit/due-date)))
        (is (false? (by-attr :gensen.remit/server-held-authority)) "G1")))))

(deftest line-datoms-reference-parent
  (let [ds (d/remittance-datoms SLIP 1)
        of-edges (filter #(= :gensen.line/of (nth % 1)) ds)
        parent (nth (first ds) 0)]
    (is (= 2 (count of-edges)) "2 内訳行")
    (is (every? #(= parent (nth % 2)) of-edges) "各行は納付書 entity を親参照")))

(deftest serialize-edn-roundtrips
  (let [edn (d/serialize (d/remittance-datoms SLIP 1))]
    (is (str/starts-with? edn ";; matsurigoto"))
    (is (str/includes? edn "gensen.remit.2026-01.salary-retirement-general"))
    (is (str/includes? edn ":gensen.remit/total-tax 51762"))))

(deftest ingest-is-gated
  (is (thrown? Exception (d/ingest!)) "G8: canonical Datom log ingest は raise"))

;; ── module facade ──
(deftest facade-process-period
  (let [r (tc/process-period
           {:slip-type :salary-retirement-general :pay-year 2026 :pay-month 1
            :operated-by ":etzhayyim-council" :tx 3
            :lines [{:区分 "俸給・給料等" :人員 2 :支給額 600000 :税額 14088}]})]
    (is (= 14088 (get-in r [:slip :total-tax])))
    (is (seq (:datoms r)))
    (is (string? (:datom-edn r)))
    (is (= "prepared-unsigned" (get-in r [:slip :status])))))

(deftest module-metadata-and-invariants
  (is (= "tax-collect" (:id tc/MODULE)))
  (is (= :reference-impl (:maturity tc/MODULE)))
  (is (= ["tax.withholding.remit"] (:backs tc/MODULE)))
  (is (false? (get-in tc/MODULE [:invariants :server-held-authority])) "G1")
  (is (false? tc/SERVER-HELD-AUTHORITY))
  (is (thrown? Exception (tc/solve)) "G8: live remit は solve() で raise"))

(defn -main [& _]
  (let [r (run-tests 'matsurigoto.tax-collect.test-datom-emit)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))
