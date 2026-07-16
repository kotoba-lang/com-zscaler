(ns meisai.methods.test-recurring
  "test_recurring.cljc — meisai 明細 recurring-charge detection → kaiyaku handoff. ADR-2606122400 R1.

  Builds a SYNTHETIC multi-month Datom log (via ingest, no real statement) and asserts:
    - rows reconstruct with month/source joined from their statement;
    - a merchant billed across ≥N months is flagged :recurring?, a one-off is not;
    - amount stability is computed (a varying charge stays recurring but :amount-stable? false);
    - multi-currency recurring lands with its currency;
    - the kaiyaku handoff is ADVISORY :review and NEVER :sever (meisai surfaces, kaiyaku decides)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [meisai.methods.recurring :as rec]
            [meisai.methods.ingest :as ingest]))

(defn- jpy-stmt [month rows]
  (ingest/statement-datoms {":source" ":sumitclub" ":statement/month" month ":statement/rows" rows}
                           (str "bcid-" month)))

(defn- usd-stmt [month rows]
  (ingest/statement-datoms {":source" ":amex-us" ":statement/month" month ":statement/currency" ":usd"
                            ":statement/rows" rows}
                           (str "bcid-usd-" month)))

;; NETFLIX every month (recurring, stable); AMAZON once (one-off); GYM every month but rising
;; (recurring, NOT amount-stable); SPOTIFY in USD every month (recurring, foreign currency).
(def datoms
  (concat
   (jpy-stmt "2026-03" [{":date" "2026-03-02" ":merchant" "NETFLIX" ":amount_jpy" 1490}
                        {":date" "2026-03-10" ":merchant" "GYM TOKYO" ":amount_jpy" 5000}])
   (jpy-stmt "2026-04" [{":date" "2026-04-02" ":merchant" "NETFLIX" ":amount_jpy" 1490}
                        {":date" "2026-04-09" ":merchant" "AMAZON.CO.JP" ":amount_jpy" 3980}
                        {":date" "2026-04-10" ":merchant" "GYM TOKYO" ":amount_jpy" 5000}])
   (jpy-stmt "2026-05" [{":date" "2026-05-02" ":merchant" "NETFLIX" ":amount_jpy" 1490}
                        {":date" "2026-05-10" ":merchant" "GYM TOKYO" ":amount_jpy" 8000}])
   (usd-stmt "2026-04" [{":date" "2026-04-05" ":merchant" "SPOTIFY USA" ":amount" 999 ":currency" ":usd"}])
   (usd-stmt "2026-05" [{":date" "2026-05-05" ":merchant" "SPOTIFY USA" ":amount" 999 ":currency" ":usd"}])))

(deftest test-rows
  (let [rs (rec/rows datoms)]
    (is (= 9 (count rs)) "all rows reconstruct")
    (is (every? #(contains? % :month) rs) "each row joins its statement month")
    (is (some #(and (= "NETFLIX" (:merchant %)) (= "2026-03" (:month %)) (= 1490 (:amount %))) rs)
        "JPY row month/amount joined")
    (is (some #(and (= "SPOTIFY USA" (:merchant %)) (= ":usd" (:currency %))) rs)
        "USD row carries its currency")))

(deftest test-recurring-detection
  (let [cands (rec/recurring datoms)
        by (into {} (map (juxt :merchant identity) cands))]
    (is (contains? by "NETFLIX") "NETFLIX billed 3 months → recurring")
    (is (not (contains? by "AMAZON.CO.JP")) "AMAZON billed once → NOT recurring")
    (let [nf (by "NETFLIX")]
      (is (= 3 (:occurrences nf)) "3 occurrences")
      (is (= ["2026-03" "2026-04" "2026-05"] (:months nf)) "distinct months sorted")
      (is (= 1490 (:typical-amount nf)) "typical amount = median")
      (is (:amount-stable? nf) "stable charge"))
    (is (:recurring? (by "GYM TOKYO")) "GYM recurring by month count")
    (is (false? (:amount-stable? (by "GYM TOKYO"))) "GYM rising charge → not amount-stable")))

(deftest test-multi-currency-recurring
  (let [by (into {} (map (juxt :merchant identity) (rec/recurring datoms)))]
    (is (= ":usd" (:currency (by "SPOTIFY USA"))) "foreign-currency recurring carries currency")
    (is (= 999 (:typical-amount (by "SPOTIFY USA"))) "minor-unit amount preserved")))

(deftest test-min-months-threshold
  (is (empty? (rec/recurring datoms {:min-months 4}))
      "raising the threshold above the data → no candidates")
  (is (seq (rec/recurring datoms {:min-months 2})) "default threshold finds candidates"))

(deftest test-handoff-advisory-never-sever
  (let [hs (rec/handoff datoms)
        edn (rec/handoff->edn hs)]
    (is (every? #(= ":review" (get % ":handoff/action")) hs) "meisai proposes :review only")
    (is (every? #(true? (get % ":handoff/advisory")) hs) "every handoff is advisory")
    (is (every? #(= (get % ":handoff/svc") (get % ":handoff/merchant")) hs) "svc candidate = merchant")
    (is (not (str/includes? edn ":sever")) "meisai NEVER emits :sever (kaiyaku + member decide)")
    (is (str/includes? edn ":handoff/source :meisai") "handoff is attributed to meisai")))

#?(:clj (defn -main [& _] (run-tests 'meisai.methods.test-recurring)))
