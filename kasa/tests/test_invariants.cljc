(ns kasa.tests.test-invariants
  "kasa 嵩 — charter-invariant + gate tests. 1:1 Clojure port of tests/test_invariants.py.

  The LOAD-BEARING structural invariants: kasa is NON-ADJUDICATING (G2), gives NO FORECAST
  (G4 — measured/estimated actuals only; future projection is mitooshi 見通し), is a PLANNING lens
  not a targeting list (G9), sources are public-only (G1), seed obs are sourcing-honest (G5), and
  every derived number is flagged :synthesized. ADR-2606072000."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set]
            [kasa.methods.kasa-edn :as kasa-edn]
            [kasa.methods.sources :as sources]
            [kasa.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-compute-capacity.kotoba.edn"))
(def valid-sourcing #{":authoritative" ":representative" ":estimated" ":synthesized"})

(defn- report* []
  (let [{:keys [series obs sources]} (analyze/load-file* seed)
        sy (analyze/by-series-year obs)
        growth (analyze/derive-growth sy)
        aggs (analyze/aggregates series sy)]
    {:md (analyze/report series obs sources sy growth aggs) :growth growth :aggs aggs}))

(deftest test-report-is-non-adjudicating-no-forecast
  (let [{:keys [md]} (report*)]
    (is (str/includes? (str/lower-case md) "non-adjudicating"))
    (is (str/includes? (str/lower-case md) "no forecast"))))

(deftest test-report-states-no-forecast-no-targeting
  ;; G4/G9: must EXPLICITLY disclaim forecasting + targeting, and must not leak an adjudication artifact.
  (let [{:keys [md]} (report*)
        lc (str/lower-case md)]
    (is (str/includes? lc "does not forecast"))
    (is (str/includes? lc "targeting list"))
    (doseq [verdict ["目標株価" "buy/sell" "export-control list:" "target list:"]]
      (is (not (str/includes? lc (str/lower-case verdict)))
          (str "adjudication/targeting artifact leaked: " (pr-str verdict))))))

(deftest test-every-seed-obs-sourcing-is-valid-and-honest
  ;; G5: every observation carries a valid sourcing; NONE is :authoritative in the R0 seed.
  (let [rows (kasa-edn/read-file seed)
        obs (filter #(contains? % ":compute.obs/id") rows)]
    (is (seq obs) "seed must contain observations")
    (doseq [o obs]
      (let [s (get o ":compute.obs/sourcing")]
        (is (contains? valid-sourcing s) (str (get o ":compute.obs/id") " has invalid sourcing " (pr-str s)))
        (is (not= s ":authoritative") "R0 seed must NOT claim :authoritative (it is headline/estimated)")))))

(deftest test-estimated-obs-carry-a-method
  ;; G5: an :estimated observation MUST document HOW (method non-empty); :representative needs none.
  (let [rows (kasa-edn/read-file seed)]
    (doseq [o (filter #(= (get % ":compute.obs/sourcing") ":estimated") rows)]
      (is (seq (str/trim (str (get o ":compute.obs/method" ""))))
          (str (get o ":compute.obs/id") " :estimated but no method")))))

(deftest test-no-future-year-observations
  ;; G4: kasa records PAST/PRESENT actuals only — no observation may be dated in the future.
  (let [rows (kasa-edn/read-file seed)
        current-year 2026]            ; the actor's 'now' per repo currentDate
    (doseq [o (filter #(contains? % ":compute.obs/id") rows)]
      (is (<= (long (get o ":compute.obs/year")) current-year)
          (str (get o ":compute.obs/id") " is a future-dated obs")))))

(deftest test-all-seed-sources-are-admissible
  ;; G1: every source referenced by the seed must pass the public-source admissibility gate.
  (let [rows (kasa-edn/read-file seed)
        srcs (filter #(contains? % ":compute.source/id") rows)]
    (doseq [s srcs]
      (let [pub (get s ":compute.source/publisher")
            access (get s ":compute.source/access")]
        (is (sources/admissible? pub access)
            (str "seed source " (get s ":compute.source/id") " publisher " pub " not admissible (G1)"))))))

(deftest test-derived-values-are-synthesized
  ;; G5: growth + aggregates are :synthesized — never presented as a reported observation.
  (let [{:keys [growth aggs]} (report*)]
    (doseq [g growth] (is (= ":synthesized" (get g ":compute.growth/sourcing"))))
    (doseq [a aggs] (is (= ":synthesized" (get a ":compute.agg/sourcing"))))))

(deftest test-aggregates-never-mix-scale-or-double-count
  ;; G12: each aggregate is single-(domain,metric,unit,scale); memory stays out of semiconductor.
  (let [{:keys [aggs]} (report*)
        keys- (map (fn [a] [(get a ":compute.agg/key") (get a ":compute.agg/metric")
                            (get a ":compute.agg/unit") (get a ":compute.agg/scale")
                            (get a ":compute.agg/year")]) aggs)]
    (is (= (count keys-) (count (set keys-))) "aggregate keys must be unique per (domain,metric,unit,scale,year)")
    (let [domains (set (map #(get % ":compute.agg/key") aggs))]
      (is (clojure.set/subset? #{":semiconductor" ":dram" ":nand"} domains)))))

#?(:clj (defn -main [& _] (run-tests 'kasa.tests.test-invariants)))
