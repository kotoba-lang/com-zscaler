;; test_coverage.clj — honest coverage report + scorecard.
;; Run: bb test_coverage.clj   (or: clojure -M test_coverage.clj)   from methods/.
(ns root.danjo.methods.test-coverage
  (:require [clojure.string :as str]))

(load-file "coverage.clj")
(alias 'cov 'root.danjo.methods.coverage)
(alias 'in  'root.danjo.methods.ingest)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))

(let [model (in/with-budget (in/ingest "../data/gov-revenue-corpus.jp.edn")
                            (in/ingest-budget "../data/gov-fiscal-seed.jp.json"))
      rep   (cov/report model)]

  ;; ── multi-year scope ──
  (check "covers FY2023 + FY2024"        (= [2023 2024] (vec (:fiscal-years rep))))
  (check "two tax kinds"                 (= 2 (count (:tax-kinds rep))))

  ;; ── honest traceability split ──
  (check "2 traceable tax-years (復興 ×2)"     (= 2 (:traceable-tax-years rep)))
  (check "2 non-traceable tax-years (源泉 ×2)" (= 2 (:nontraceable-tax-years rep)))
  (check "every 復興 trace residual 0"
         (every? #(or (not= :reconstruction-surtax (:tax %)) (zero? (:residual %))) (:traces rep)))
  (check "every 源泉 trace non-traceable"
         (every? #(or (not= :withholding-income (:tax %)) (false? (:traceable? %))) (:traces rep)))

  ;; ── reconciliation coverage ──
  (let [r (:reconciliations rep)]
    (check "8 program-years reconciled"  (= 8 (:total r)))
    (check "MEXT within-budget ×2"       (= 2 (:within r)))
    (check "no false outlay-exceeds"     (= 0 (:outlay-exceeds-appropriation r))))

  ;; ── counts grow with multi-year corpus ──
  (check "4 revenue-lines"               (= 4 (:revenue-lines (:counts rep))))
  (check "datoms > 100"                  (> (:datoms (:counts rep)) 100))

  ;; ── scorecard markdown is honest ──
  (let [md (cov/coverage-md rep)]
    (check "scorecard marks :representative" (str/includes? md "representative"))
    (check "scorecard shows ❌ for fungible 源泉" (str/includes? md "❌"))
    (check "scorecard states danjo finds-never-judges" (str/includes? md "never judges"))
    (check "scorecard names the partial-corpus FP mode" (str/includes? md "partial-corpus"))))

(println (format "── coverage: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
