(ns hakoniwa.tests.test-distribution
  "hakoniwa 箱庭 — distribution + forecast-record + Datom-emit tests (ADR-2606111500).
  1:1 Clojure port of tests/test_distribution.py. Pure stdlib, network-free, deterministic.

  Verifies:
    - quantiles are monotone and the histogram sums to the replica count
    - G2: the forecast record is distribution-only — :forecast/point-asserted is false and there
      is NO :forecast/point key anywhere
    - G3: a non-resilience :forecast/use (:trade / :wager / :position / :target / :manipulate /
      :campaign) is REFUSED (forecast-record raises)
    - the forecast EDN round-trips distribution-only (no bare :forecast/point assertion)
    - the Datom log emits ground :add datoms, marks every persona synthetic, flags the
      distribution transient, and emits no point datom
    - determinism (two emits byte-identical)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [hakoniwa.methods.world :as world]
            [hakoniwa.methods.simulate :as simulate]
            [hakoniwa.methods.distribution :as distribution]
            [hakoniwa.methods.datom-emit :as datom-emit]))

(def scenario
  (-> (io/file *file*) .getParentFile .getParentFile
      (io/file "data" "seed-scenario.kotoba.edn") str))

(defn- dist-fixture []
  (let [{:keys [nodes edges]} (world/load-file* scenario)
        [results meta] (simulate/ensemble nodes edges {:steps 12 :replicas 64 :seed 7})]
    {:nodes nodes :edges edges :dist (distribution/distribution results) :meta meta}))

(deftest test-quantiles-monotone-and-histogram-total
  (let [{:keys [dist meta]} (dist-fixture)
        q (get dist "quantiles")
        order [(get q ":p10") (get q ":p25") (get q ":p50") (get q ":p75") (get q ":p90")]]
    (is (= order (sort order)) (str "quantiles not monotone: " order))
    (is (= (reduce + (get dist "histogram")) (get meta "replicas")) "histogram does not sum to replica count")
    (is (and (<= (get dist "min") (get dist "mean")) (<= (get dist "mean") (get dist "max"))))))

(deftest test-forecast-record-is-distribution-only
  (let [{:keys [nodes dist meta]} (dist-fixture)
        rec (distribution/forecast-record nodes dist meta "2026-06-11T00:00:00Z")]
    (is (= (get rec ":forecast/kind") ":distribution"))
    (is (false? (get rec ":forecast/point-asserted")) "G2: point-asserted must be false")
    ;; G2: there is NO point field of any kind
    (is (not (some (fn [k] (and (str/includes? k "point") (not= k ":forecast/point-asserted")))
                   (world/ordered-keys rec)))
        (str "a point field leaked into the forecast record: " (vec (world/ordered-keys rec))))
    (is (and (contains? rec ":forecast/quantiles") (contains? rec ":forecast/histogram")))))

(deftest test-g3-non-resilience-use-refused
  (let [{:keys [nodes dist meta]} (dist-fixture)]
    (doseq [bad [":trade" ":wager" ":position" ":target" ":manipulate" ":campaign"]]
      (let [raised (try (distribution/forecast-record nodes dist meta "t" bad) false
                        (catch clojure.lang.ExceptionInfo _ true))]
        (is raised (str "G3 breach: forecast accepted non-resilience use " bad))))))

(deftest test-forecast-edn-roundtrips-distribution-only
  (let [{:keys [nodes dist meta]} (dist-fixture)
        rec (distribution/forecast-record nodes dist meta "2026-06-11T00:00:00Z")
        edn (distribution/forecast-edn rec)]
    (is (str/includes? edn ":forecast/point-asserted false"))
    (is (str/includes? edn ":forecast/kind :distribution"))
    ;; no bare point assertion (ignore ;; comment lines)
    (let [payload (str/join "\n" (remove #(str/starts-with? (str/triml %) ";;") (str/split-lines edn)))]
      (is (not (str/includes? payload ":forecast/point "))))))

(deftest test-datom-emit-ground-synthetic-and-transient-distribution
  (let [{:keys [nodes edges dist meta]} (dist-fixture)
        out (datom-emit/emit nodes edges dist meta 5)]
    (is (str/includes? out ":add]") "no ground :add datoms")
    (is (str/includes? out ":persona/synthetic true") "persona synthetic marker missing from ground datoms (G1)")
    (is (str/includes? out ":en/kind :influences") "influence 縁 datoms missing")
    (is (str/includes? out " 5 :add]") "tx not threaded into ground datoms")
    ;; distribution must be transient, and there must be NO point datom
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/distribution-p50"))
    (is (str/includes? out ":bond/point-asserted false"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/distribution"))
        (is (str/includes? line ":derived]") (str "distribution readout not flagged transient: " line))))))

(deftest test-determinism
  (let [{:keys [nodes edges dist meta]} (dist-fixture)
        a (datom-emit/emit nodes edges dist meta 1)
        {n2 :nodes e2 :edges d2 :dist m2 :meta} (dist-fixture)
        b (datom-emit/emit n2 e2 d2 m2 1)]
    (is (= a b) "Datom emit is not deterministic")))

#?(:clj (defn -main [& _] (run-tests 'hakoniwa.tests.test-distribution)))
