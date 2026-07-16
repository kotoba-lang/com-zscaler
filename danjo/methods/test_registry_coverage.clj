;; test_registry_coverage.clj — honest fiscal-source registry coverage scorecard.
;; Run: bb test_registry_coverage.clj   from methods/.
(ns root.danjo.methods.test-registry-coverage
  (:require [clojure.string :as str]))

(load-file "registry_coverage.clj")
(alias 'rc 'root.danjo.methods.registry-coverage)

(def checks (atom 0)) (def fails (atom 0))
(defn check [l p] (swap! checks inc) (if p (println "  ok  " l) (do (swap! fails inc) (println "  FAIL" l))))

(let [registry (rc/load-registry "../registry/sources.seed.json")
      rep      (rc/report registry)]

  ;; ── worldwide guard (mirrors test_danjo_registry_seed.py invariant #5) ──
  (check "≥12 distinct jurisdictions"     (>= (:jurisdiction-count rep) 12))
  (check "total-sources matches registry" (= (:total-sources rep) (count (:sources registry))))
  (check "jurisdiction-count matches distinct sources"
         (= (:jurisdiction-count rep) (count (:jurisdictions rep))))

  ;; ── G14 honesty: nothing is verified at R0 ──
  (check "0 verified sources at R0" (zero? (:verified-count rep)))
  (check "every source is unverified-seed"
         (= (:total-sources rep) (get (:by-verification-status rep) "unverified-seed" 0)))

  ;; ── breakdown sums back to the total ──
  (check "by-source-kind sums to total"
         (= (:total-sources rep) (reduce + (vals (:by-source-kind rep)))))
  (check "by-verification-status sums to total"
         (= (:total-sources rep) (reduce + (vals (:by-verification-status rep)))))

  ;; ── scorecard markdown is honest ──
  (let [md (rc/coverage-md rep)]
    (check "scorecard states G14 honest tiers"    (str/includes? md "honest answer, G14"))
    (check "scorecard shows verified count"       (str/includes? md (str (:verified-count rep) "/" (:total-sources rep))))
    (check "scorecard states danjo finds-never-judges" (str/includes? md "never judges"))
    (check "scorecard is not a fraud report"       (str/includes? md "not a fraud/discrepancy report"))))

(println (format "── registry-coverage: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
