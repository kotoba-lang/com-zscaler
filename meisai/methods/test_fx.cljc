(ns meisai.methods.test-fx
  "test_fx.cljc — meisai 明細 report-time FX. ADR-2606122400 R1.

  Asserts: minor-unit→yen conversion (incl. JPY passthrough + unknown-rate → nil), and that
  enrich-handoff annotates only NON-JPY records with a JPY-equivalent + the rate used — never
  mutating the native amount/currency (FX stays a report-time view, never a Datom)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [meisai.methods.fx :as fx]
            [meisai.methods.recurring :as rec]
            [meisai.methods.ingest :as ingest]))

(def rates {":usd" 150.0 ":eur" 162.0})

(deftest test-to-jpy
  (is (= 1499 (fx/to-jpy 999 ":usd" rates)) "999 cents USD @150 → 1499 yen")
  (is (= 61875 (fx/to-jpy 41250 ":usd" rates)) "412.50 USD @150 → 61875 yen")
  (is (= 1490 (fx/to-jpy 1490 ":jpy" rates)) "JPY passes through (minor unit == yen)")
  (is (nil? (fx/to-jpy 1000 ":gbp" rates)) "unknown rate → nil (charge stays un-priced)"))

(deftest test-enrich-handoff
  (let [hs [{":handoff/currency" ":usd" ":handoff/typical-amount" 999 ":handoff/merchant" "X"}
            {":handoff/currency" ":jpy" ":handoff/typical-amount" 1490 ":handoff/merchant" "Y"}
            {":handoff/currency" ":gbp" ":handoff/typical-amount" 800 ":handoff/merchant" "Z"}]
        out (fx/enrich-handoff hs rates)]
    (is (= 1499 (get (nth out 0) ":handoff/jpy-equivalent")) "USD record gets JPY-equivalent")
    (is (= true (get (nth out 0) ":handoff/fx-advisory")) "and is marked advisory")
    (is (= 150.0 (get (nth out 0) ":handoff/fx-rate")) "rate used is recorded")
    (is (= 999 (get (nth out 0) ":handoff/typical-amount")) "native amount is UNCHANGED")
    (is (nil? (get (nth out 1) ":handoff/jpy-equivalent")) "JPY record left untouched")
    (is (nil? (get (nth out 2) ":handoff/jpy-equivalent")) "unknown-rate record left un-priced")))

(deftest test-recurring-handoff-with-rates
  ;; end-to-end: a USD recurring charge → handoff with :rates → JPY-equivalent annotated
  (let [datoms (concat
                (ingest/statement-datoms
                 {":source" ":amex-us" ":statement/month" "2026-04" ":statement/currency" ":usd"
                  ":statement/rows" [{":date" "2026-04-05" ":merchant" "USD SUB" ":amount" 999 ":currency" ":usd"}]}
                 "b1")
                (ingest/statement-datoms
                 {":source" ":amex-us" ":statement/month" "2026-05" ":statement/currency" ":usd"
                  ":statement/rows" [{":date" "2026-05-05" ":merchant" "USD SUB" ":amount" 999 ":currency" ":usd"}]}
                 "b2"))
        hs (rec/handoff datoms {:rates rates})
        usd (first hs)]
    (is (= ":usd" (get usd ":handoff/currency")) "native currency retained")
    (is (= 1499 (get usd ":handoff/jpy-equivalent")) "report-time JPY-equivalent present")
    (is (= true (get usd ":handoff/fx-advisory")))))

(deftest test-no-rates-leaves-handoff-native
  (let [datoms (ingest/statement-datoms
                {":source" ":amex-us" ":statement/month" "2026-04" ":statement/currency" ":usd"
                 ":statement/rows" [{":date" "2026-04-05" ":merchant" "USD SUB" ":amount" 999 ":currency" ":usd"}
                                    {":date" "2026-04-06" ":merchant" "USD SUB" ":amount" 999 ":currency" ":usd"}]}
                "b1")
        ;; same merchant twice in one month → 1 month only; needs ≥2 months. Build 2 months:
        more (ingest/statement-datoms
              {":source" ":amex-us" ":statement/month" "2026-05" ":statement/currency" ":usd"
               ":statement/rows" [{":date" "2026-05-05" ":merchant" "USD SUB" ":amount" 999 ":currency" ":usd"}]}
              "b2")
        hs (rec/handoff (concat datoms more))]
    (is (seq hs) "candidate found")
    (is (nil? (get (first hs) ":handoff/jpy-equivalent")) "no :rates → no FX annotation (native only)")))

#?(:clj (defn -main [& _] (run-tests 'meisai.methods.test-fx)))
