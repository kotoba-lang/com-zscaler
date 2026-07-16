(ns kasa.tests.test-kasa
  "kasa 嵩 — unit tests. 1:1 Clojure port of tests/test_kasa.py (ADR-2606072000).

  Ports the EDN-reader, sources-admissibility, analyze YoY/CAGR/aggregate, and report-render
  assertions. The three ingest-cell tests (test_ingest_refuses_prohibited_publisher /
  test_ingest_accepts_public_rows / test_merge_authoritative_beats_representative) depend on the
  unported `ingest` module (a separate cell; analyze.py imports only kasa_edn) and are
  intentionally DEFERRED here, mirroring the inochi precedent of omitting sibling-module tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kasa.methods.kasa-edn :as kasa-edn]
            [kasa.methods.sources :as sources]
            [kasa.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-compute-capacity.kotoba.edn"))

;; ── edn reader: scientific notation (frontier-training FLOP) ──────────────────
(deftest test-edn-reads-scientific-notation
  (let [rows (kasa-edn/read-file seed)
        o (first (filter #(= (get % ":compute.obs/id")
                             "obs.cap.flops.frontier-training.world.2024") rows))]
    (is (< (Math/abs (- (double (get o ":compute.obs/value")) 5.0e25)) 1e20))))

(deftest test-seed-loads-series-obs-sources
  (let [{:keys [series obs sources]} (analyze/load-file* seed)]
    (is (= 11 (count series)))
    (is (= 52 (count obs)))
    (is (= 8 (count sources)))))

;; ── sources: G1 admissibility ────────────────────────────────────────────────
(deftest test-public-sources-admissible
  (is (sources/admissible? "sia"))
  (is (sources/admissible? ":epoch-ai" ":open-dataset"))
  (is (sources/admissible? "top500" ":public-list")))

(deftest test-paid-terminals-prohibited
  (is (not (sources/admissible? "bloomberg-terminal")))
  (is (not (sources/admissible? "gartner-report")))
  ;; IDC headline press-release is fine, the paid terminal/report is not (the §2(e) split)
  (is (sources/admissible? "idc" ":press-release"))
  (is (not (sources/admissible? "idc" ":paid-terminal"))))

;; ── analyze: YoY + CAGR arithmetic ───────────────────────────────────────────
(deftest test-yoy-matches-arithmetic
  ;; Semiconductor 2024 YoY = 628/527-1 = +19.2%.
  (let [{:keys [obs]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        g (reduce (fn [m x]
                    (assoc m [(get x ":compute.growth/series") (get x ":compute.growth/kind")
                              (get x ":compute.growth/to-year")]
                           (get x ":compute.growth/value")))
                  {} (analyze/derive-growth sy))]
    (is (< (Math/abs (- (get g ["cap.semi.revenue.world" ":yoy" 2024]) (- (/ 628.0 527.0) 1)))
           1e-3))))

(deftest test-cagr-matches-arithmetic
  ;; SSD CAGR 2020→2024 = (650/207)^(1/4)-1.
  (let [{:keys [obs]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        g (reduce (fn [m x]
                    (assoc m [(get x ":compute.growth/series") (get x ":compute.growth/kind")]
                           (get x ":compute.growth/value")))
                  {} (analyze/derive-growth sy))
        expected (- (Math/pow (/ 650.0 207.0) (/ 1.0 4)) 1)]
    (is (< (Math/abs (- (get g ["cap.flops.frontier-training.world" ":cagr"])
                        (- (Math/pow (/ 5.0e25 3.0e23) (/ 1.0 4)) 1)))
           1e-2))
    (is (< (Math/abs (- (get g ["cap.storage.ssd-capacity.world" ":cagr"]) expected)) 1e-3))))

(deftest test-yoy-skips-non-consecutive-years
  ;; A gap in years must NOT produce a YoY (only consecutive pairs).
  (let [sy (analyze/by-series-year [{":compute.obs/series" "s" ":compute.obs/year" 2020 ":compute.obs/value" 100.0}
                                    {":compute.obs/series" "s" ":compute.obs/year" 2022 ":compute.obs/value" 200.0}])
        g (analyze/derive-growth sy)
        yoys (filter #(= (get % ":compute.growth/kind") ":yoy") g)
        cagrs (filter #(= (get % ":compute.growth/kind") ":cagr") g)]
    (is (= [] (vec yoys)))                ; no consecutive pair → no YoY
    (is (= 1 (count cagrs)))))            ; span CAGR still computed

;; ── analyze: coverage-honest aggregates, no double-count ──────────────────────
(deftest test-storage-aggregate-sums-hdd-plus-ssd
  ;; storage exabytes 2024 = HDD 1010 + SSD 650 = 1660, n=2.
  (let [{:keys [series obs]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        aggs (analyze/aggregates series sy)
        a (first (filter #(and (= (get % ":compute.agg/key") ":storage")
                               (= (get % ":compute.agg/year") 2024)) aggs))]
    (is (< (Math/abs (- (get a ":compute.agg/sum") 1660.0)) 1e-6))
    (is (= 2 (get a ":compute.agg/n")))))

(deftest test-memory-never-summed-into-semiconductor
  (let [{:keys [series obs]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        aggs (analyze/aggregates series sy)
        semi (first (filter #(and (= (get % ":compute.agg/key") ":semiconductor")
                                  (= (get % ":compute.agg/year") 2024)) aggs))]
    (is (= 1 (get semi ":compute.agg/n")))      ; only the semi series, not + dram + nand
    (is (< (Math/abs (- (get semi ":compute.agg/sum") 628.0)) 1e-6))
    (is (some #(= (get % ":compute.agg/key") ":dram") aggs))
    (is (some #(= (get % ":compute.agg/key") ":nand") aggs))))

(deftest test-flops-petaflops-not-summed-with-raw-flop
  ;; TOP500 (:petaflops) and frontier-training (:ones) share unit :flops but DIFFERENT scale.
  (let [{:keys [series obs]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        aggs (analyze/aggregates series sy)
        flops (filter #(and (= (get % ":compute.agg/key") ":flops")
                            (= (get % ":compute.agg/year") 2024)) aggs)]
    (is (= 2 (count flops)))                     ; flops-installed + flops-training, kept apart
    (is (every? #(= 1 (get % ":compute.agg/n")) flops))))

;; ── analyze: derived growth + aggregates are :synthesized ─────────────────────
(deftest test-growth-and-aggregates-are-synthesized
  (let [{:keys [series obs]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)]
    (doseq [g (analyze/derive-growth sy)]
      (is (= ":synthesized" (get g ":compute.growth/sourcing"))))
    (doseq [a (analyze/aggregates series sy)]
      (is (= ":synthesized" (get a ":compute.agg/sourcing"))))))

;; ── report renders ───────────────────────────────────────────────────────────
(deftest test-report-renders-growth-and-snapshot
  (let [{:keys [series obs sources]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        md (analyze/report series obs sources sy (analyze/derive-growth sy) (analyze/aggregates series sy))]
    (is (str/includes? md "年間増加量"))
    (is (str/includes? md "World compute snapshot"))
    (is (str/includes? md "1,660 EB"))))         ; storage aggregate rendered

#?(:clj (defn -main [& _] (run-tests 'kasa.tests.test-kasa)))
