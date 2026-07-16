(ns maps.methods.test-avet-roundtrip
  "test_avet_roundtrip.py — AVET H3-cell round-trip parity test (ADR-2606064500 §2).
  unittest → clojure.test. Ports the deterministic index-contract layer (TestAvetIndexContract),
  which always runs with no deps.

  The TestAvetRealH3 layer is @unittest.skipUnless(_HAS_H3) and `h3` is unavailable on this host,
  so — exactly as the Python suite does without h3 — it is omitted here. The Python __main__
  runner is omitted."
  (:require [clojure.test :refer [deftest is]]
            [clojure.set :as set]
            [maps.methods.kotoba-local :as kl]))

(def ^:private cell-res [2 4 6 8 10 12])

;; Tokyo-anchor features (subset of the seed)
(def ^:private features
  [["feature.station.tokyo" ":station" "Tokyo Station" 35.6812 139.7671]
   ["feature.building.marunouchi-bldg" ":building" "Marunouchi Building" 35.6809 139.7644]
   ["feature.building.shin-marunouchi" ":building" "Shin-Marunouchi Building" 35.6820 139.7639]
   ["feature.road.eitai-dori" ":road" "Eitai-dori" 35.6805 139.7700]
   ["feature.airport.haneda" ":airport" "Tokyo Haneda" 35.5494 139.7798]])

(defn- mock-cell
  "Deterministic mock H3-like cell: finer res → finer grid (nested by construction)."
  [lat lon res]
  (let [size (/ 10.0 (Math/pow 3 res))]
    (str "r" res "/" (long (Math/floor (/ lat size))) "/" (long (Math/floor (/ lon size))))))

(defn- batch
  "Build a kg.ingest_batch with cell claims stamped by cellfn (mirrors buildIngestBatch)."
  [features cellfn]
  {"entities"
   (vec
    (for [[fid label name lat lon] features]
      (let [claims (into [{"pred" "feature/label" "value" label}
                          {"pred" "feature/name" "value" name}
                          {"pred" "feature/lat" "value" (str lat)}
                          {"pred" "feature/lon" "value" (str lon)}
                          {"pred" "feature/sourcing" "value" ":representative"}]
                         (for [r cell-res] {"pred" (str "feature.cell/r" r) "value" (cellfn lat lon r)}))]
        {"id" fid "type" "maps-feature" "label_en" name "claims" claims "relations" []})))})

(defn- new-loaded-store []
  (let [store (kl/new-store)]
    (kl/ingest-batch store (batch features mock-cell))
    store))

(deftest test-query-returns-features-in-the-queried-cell
  (let [store (new-loaded-store)
        cell (mock-cell 35.6812 139.7671 12)
        res (kl/query-by-cells store [cell] 12)
        ids (map #(get % "id") (get-in res [cell ":station"] []))]
    (is (some #{"feature.station.tokyo"} ids))))

(deftest test-foreign-cell-returns-nothing
  (let [store (new-loaded-store)
        res (kl/query-by-cells store ["r12/9999/9999"] 12)]
    (is (= (get res "r12/9999/9999" {}) {}))))

(deftest test-label-filter-narrows
  (let [store (new-loaded-store)
        cell (mock-cell 35.681 139.764 6)
        res (kl/query-by-cells store [cell] 6 ["building"])
        labels-returned (set (keys (get res cell {})))]
    (is (set/subset? labels-returned #{":building"}))
    (is (>= (count (get-in res [cell ":building"] [])) 1))))

(deftest test-coarse-res-aggregates-fine-cells
  (let [store (new-loaded-store)
        r2 (kl/cells-at-res store 2)
        r12 (kl/cells-at-res store 12)]
    (is (<= (count r2) (count r12)))
    (is (>= (kl/feature-count store) 5))))

(deftest test-limit-caps-per-label
  (let [store (new-loaded-store)
        cell (mock-cell 35.681 139.764 2)
        res (kl/query-by-cells store [cell] 2 ["building"] 1)]
    (is (<= (count (get-in res [cell ":building"] [])) 1))))

(deftest test-materialized-row-has-getchunk-fields
  (let [store (new-loaded-store)
        cell (mock-cell 35.6812 139.7671 12)
        row (first (get-in (kl/query-by-cells store [cell] 12) [cell ":station"]))]
    (doseq [k ["id" "label" "name" "lat" "lon"]]
      (is (contains? row k)))))
