;; test_ingest.clj — standalone suite for the danjo revenue-corpus ingest (G3 passive-only).
;; Run: bb test_ingest.clj   (or: clojure -M test_ingest.clj)   from methods/.
(ns root.danjo.methods.test-ingest)

(load-file "revenue_ledger.clj")
(load-file "ingest.clj")
(alias 'rl 'root.danjo.methods.revenue-ledger)
(alias 'in 'root.danjo.methods.ingest)

(def corpus-path "../data/gov-revenue-corpus.jp.edn")
(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))
(defn throws? [f] (try (f) false (catch Exception _ true)))

(let [corpus (in/load-corpus corpus-path)
      model  (in/ingest-corpus corpus)]

  ;; ── projection shape (FY2023 + FY2024) ──
  (check "ingest yields 4 revenue-lines" (= 4 (count (:revenue-lines model))))
  (check "ingest yields 2 transfers"     (= 2 (count (:transfers model))))
  (check "ingest yields 6 outlays"       (= 6 (count (:outlays model))))
  (check "account-EARMARK is law (constant), not ingested"
         (= in/account-law (:accounts model)))

  ;; ── G5: every projected entry carries ≥2 source CIDs (own record + dataset manifest) ──
  (check "G5: revenue-lines ≥2 source CIDs"
         (every? #(>= (count (:source-record-cids %)) 2) (:revenue-lines model)))
  (check "G5: outlays ≥2 source CIDs"
         (every? #(>= (count (:source-record-cids %)) 2) (:outlays model)))
  (check "record CIDs are gov.dataset locators"
         (every? #(clojure.string/starts-with? (first (:source-record-cids %)) "gov.dataset.")
                 (:revenue-lines model)))
  (check "2nd CID is the dataset manifest"
         (= (:dataset-cid model) (second (:source-record-cids (first (:revenue-lines model))))))

  ;; ── determinism: same corpus → same CIDs ──
  (check "record-cid deterministic"
         (= (in/record-cid (first (:records corpus))) (in/record-cid (first (:records corpus)))))

  ;; ── amounts: exact integers, 1円 precision ──
  (check "amounts are exact integers"
         (every? integer? (map :amount-jpy (:revenue-lines model))))
  (check "negative amount-local RAISES"
         (throws? #(in/ingest-corpus (update corpus :records conj
                     {:record-id "bad" :record-kind :revenue :tax-kind :x :account :general
                      :fiscal-year 2024 :amount-local -1 :source-sensor "s"}))))

  ;; ── the ingested model drives trace identically to the hand seed ──
  (let [r (rl/trace model :reconstruction-surtax 2024)
        w (rl/trace model :withholding-income 2024)]
    (check "ingested 復興 traceable, residual 0" (and (:traceable? r) (zero? (:residual r))))
    (check "ingested 源泉 non-traceable"          (false? (:traceable? w))))

  ;; ── all-datoms over the ingested model passes G4/G5 ──
  (let [ds (rl/all-datoms model)]
    (check "ingested model emits datoms"  (pos? (count ds)))
    (check "all :db/add"                   (every? #(= :db/add (first %)) ds))))

;; ── JSON budget ingest (the existing danjo corpus, gov-fiscal-seed.jp.json) ──
(check "parse-json round-trips a nested doc"
       (= {"a" 1 "b" [true false nil] "c" {"d" "x"}}
          (in/parse-json "{\"a\":1,\"b\":[true,false,null],\"c\":{\"d\":\"x\"}}")))
(check "parse-json keeps big integers exact (1円)"
       (= 5464300000000 (get (in/parse-json "{\"n\":5464300000000}") "n")))
(let [b (in/ingest-budget "../data/gov-fiscal-seed.jp.json")]
  (check "ingest-budget: 2 appropriations + 3 outlays"
         (and (= 2 (count (:appropriations b))) (= 3 (count (:outlays b)))))
  (check "budget records carry budgetRecord CIDs (G5 ≥2)"
         (every? #(>= (count (:source-record-cids %)) 2) (:appropriations b)))
  ;; with-budget merges the budget side into a revenue model + emits :gov.appropriation/* datoms
  (let [merged (in/with-budget (in/ingest corpus-path) b)
        ds (rl/all-datoms merged)]
    (check "with-budget adds appropriations to the model" (= 2 (count (:appropriations merged))))
    (check "appropriation-datoms emitted"
           (some #(= :gov.appropriation/amount-jpy (nth % 2)) ds))
    (check "merged model still G4-clean" (every? #(= :db/add (first %)) ds))))

(println (format "── ingest: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
