(ns kabuto.methods.test-analyze
  "kabuto 兜 — analyzer tests (ADR-2606022000). 1:1 Clojure port of
  methods/test_analyze.py.

  Covers the concentration roll-ups (single-source, per-commodity HHI, jurisdiction
  load, diversification, intermediaries, tier depth) and their mathematical bounds,
  plus the load-bearing charter invariant: kabuto is a resilience/accountability
  map, NEVER a target-list — the report states it, and single-source findings exist
  to be diversified, not exploited (G2).

  NOTE on scope: the Python suite also ships test_autorun.py / test_social.py —
  those are autorun/social-dependent (unported modules), so they are deferred,
  matching the rasen/inochi precedent. All eight PURE analyze assertions from
  test_analyze.py are ported 1:1 below, plus an explicit G2 resilience-not-target-
  list enforcement test (the constitutional gate)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [kabuto.methods.kabuto-edn :as kedn]
            [kabuto.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-public-companies.kotoba.edn"))

(defn- load-all []
  (let [rows (kedn/load-edn seed)
        {:keys [companies addresses contacts edges processes]} (kedn/classify rows)]
    {:companies companies :addresses addresses :contacts contacts
     :edges edges :processes processes
     :a (analyze/analyze companies edges)}))

;; def test_classify_buckets_the_seed
(deftest test-classify-buckets-the-seed
  (let [{:keys [companies edges]} (load-all)]
    (is (>= (count companies) 100) "a substantial public-company seed")
    (is (>= (count edges) 50) "disclosed supply edges present")))

;; def test_single_source_findings_are_high_criticality
(deftest test-single-source-findings-are-high-criticality
  (let [{:keys [a]} (load-all)]
    (doseq [[_cust _commodity _sup crit] (get a "single_source")]
      (is (>= crit 0.7)))))

;; def test_commodity_hhi_is_bounded_zero_to_one
(deftest test-commodity-hhi-is-bounded-zero-to-one
  (let [{:keys [a]} (load-all)]
    (is (seq (get a "commodity_hhi")) "expected per-commodity HHI rows")
    (doseq [[_commodity n-sup hhi] (get a "commodity_hhi")]
      ;; HHI = Σ(share²) ∈ (0, 1]
      (is (and (< 0.0 hhi) (<= hhi 1.0)))
      ;; a single disclosed supplier ⇒ monopoly HHI of exactly 1.0
      (when (= n-sup 1)
        (is (= hhi 1.0))))))

;; def test_jurisdiction_load_is_non_negative
(deftest test-jurisdiction-load-is-non-negative
  (let [{:keys [a]} (load-all)
        jl (get a "jurisdiction_load")]
    (is (seq jl))
    (is (every? #(>= % 0) (map second jl)))))

;; def test_diversification_sorted_brittle_first
(deftest test-diversification-sorted-brittle-first
  (let [{:keys [a]} (load-all)
        idxs (map #(nth % 3) (get a "diversification"))]
    ;; lowest (most brittle) diversification index first
    (is (= idxs (sort idxs)))))

;; def test_intermediaries_score_is_in_times_out
(deftest test-intermediaries-score-is-in-times-out
  (let [{:keys [a]} (load-all)]
    (doseq [[_node ind outd score] (get a "intermediaries")]
      ;; betweenness proxy = in-degree × out-degree
      (is (= score (* ind outd))))))

;; def test_render_datoms_marked_derived
(deftest test-render-datoms-marked-derived
  (let [{:keys [companies a]} (load-all)
        edn (analyze/render-datoms companies a)]
    ;; never re-ingested as authoritative
    (is (or (str/includes? edn ":derived") (str/includes? edn "derived")))))

;; def test_g2_report_is_not_a_target_list
(deftest test-g2-report-is-not-a-target-list
  (let [{:keys [companies addresses contacts edges processes a]} (load-all)
        md (analyze/render-report companies addresses contacts edges processes a)]
    ;; the framing invariant is stated in the report
    (is (str/includes? md "target-list"))))

;; ── constitutional gate (kabuto G2): resilience map, NEVER a target-list ──────
;; The derived datoms carry ONLY resilience/accountability framing — no raid /
;; takeover / interdiction attr is representable. Enforced here, not just doc'd.
(deftest test-g2-no-target-list-vocab-in-datoms
  (let [{:keys [companies a]} (load-all)
        edn (analyze/render-datoms companies a)
        forbidden ["target" "raid" "takeover" "interdict" "strike" "attack" "destroy"]]
    (doseq [w forbidden]
      (is (not (str/includes? (str/lower-case edn) w))
          (str "datoms must not carry interdiction vocab: " w)))
    ;; and every derived datom is flagged :derived (never authoritative fact)
    (is (str/includes? edn ":supply/derived true"))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'kabuto.methods.test-analyze)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
