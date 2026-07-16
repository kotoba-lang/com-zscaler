(ns maps.tests.test-chunk
  "maps — get-chunk method tests (ADR-2606064500). stdlib; network-free.
  1:1 Clojure port of `methods/test_chunk.py`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [maps.methods.chunk     :as chunk]
            [maps.methods.ingest    :as ingest]
            [maps.tests.kotoba-local :as kl]))

;; ── build an in-memory store from the test seed ──────────────────────────────

(defn- make-seed-store
  "A KotobaLocal store populated from a small synthetic feature set
  (mirrors the Python test_chunk.py setup)."
  []
  (let [features {"bldg1" {":feature/id"        "bldg1"
                            ":feature/label"     ":building"
                            ":feature/name"      "Tokyo Station South"
                            ":feature/lat"       35.6812
                            ":feature/lon"       139.7671
                            ":feature/height-m"  31.0
                            ":feature.cell/r10"  "8a276524ddfffff"
                            ":feature/sourcing"  ":representative"}
                  "st1"   {":feature/id"       "st1"
                           ":feature/label"    ":station"
                           ":feature/name"     "Tokyo Station"
                           ":feature/lat"      35.6812
                           ":feature/lon"      139.7671
                           ":feature.cell/r10" "8a276524ddfffff"
                           ":feature/sourcing" ":representative"}
                  "rd1"   {":feature/id"       "rd1"
                           ":feature/label"    ":road"
                           ":feature/name"     "Eitai-dori"
                           ":feature/lat"      35.6805
                           ":feature/lon"      139.7700
                           ":feature.cell/r10" "8a276524ddfffff"
                           ":feature/sourcing" ":representative"}}]
    (kl/build-store-from-features features)))

(deftest test-fold-label
  (is (= ":building"  (chunk/fold-label "Building")))
  (is (= ":station"   (chunk/fold-label "Station")))
  (is (= ":road"      (chunk/fold-label "Road")))
  ;; already a keyword — pass through
  (is (= ":building"  (chunk/fold-label ":building")))
  ;; unknown — kebab-cased
  (is (= ":weirdlabel" (chunk/fold-label "WeirdLabel"))))

(deftest test-get-chunk-returns-features
  (let [store (make-seed-store)
        qfn   (kl/make-query-fn store)
        result (chunk/get-chunk qfn ["8a276524ddfffff"] 10)]
    (is (map? result))
    (is (contains? result :chunks))
    (is (pos? (:total result)))))

(deftest test-get-chunk-grouped-by-label
  (let [store (make-seed-store)
        qfn   (kl/make-query-fn store)
        result (chunk/get-chunk qfn ["8a276524ddfffff"] 10)
        cell-map (get-in result [:chunks "8a276524ddfffff"])]
    (is (map? cell-map))
    ;; at least one label group is non-empty
    (is (some #(pos? (count %)) (vals cell-map)))))

(deftest test-get-chunk-label-filter
  (let [store  (make-seed-store)
        qfn    (kl/make-query-fn store)
        result (chunk/get-chunk qfn ["8a276524ddfffff"] 10 :labels [":building"])]
    (is (pos? (:total result)))
    ;; no non-building features in result
    (doseq [[_cell label-map] (:chunks result)
            [label _feats] label-map]
      (is (= ":building" label)))))

(deftest test-get-chunk-missing-cell-returns-empty
  (let [store  (make-seed-store)
        qfn    (kl/make-query-fn store)
        result (chunk/get-chunk qfn ["88276524ddffffff"] 10)]  ; different cell
    (is (zero? (:total result)))))

(deftest test-get-chunk-features-are-geojson
  (let [store  (make-seed-store)
        qfn    (kl/make-query-fn store)
        result (chunk/get-chunk qfn ["8a276524ddfffff"] 10)
        feats  (mapcat vals (vals (:chunks result)))
        feat   (first (filter seq feats))]
    (when (seq feat)
      (doseq [f feat]
        (is (= "Feature" (get f "type")) "GeoJSON Feature type")
        (is (contains? f "properties") "GeoJSON properties present")))))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-chunk))
     (run-tests)))
