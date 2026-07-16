(ns maps.tests.test-reverse
  "maps — reverse-geocode + haversine tests (ADR-2606064500). stdlib; network-free.
  1:1 Clojure port of `methods/test_reverse.py`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [maps.methods.reverse    :as reverse-ns]
            [maps.tests.kotoba-local :as kl]))

;; Tokyo Station (anchor)
(def ^:private TST-LAT 35.6812)
(def ^:private TST-LON 139.7671)
(def ^:private TOKYO-CELL "8a276524ddfffff")

;; ── haversine tests (pure) ────────────────────────────────────────────────────

(deftest test-haversine-zero
  (is (< (reverse-ns/haversine-m 35.0 139.0 35.0 139.0) 0.01)
      "haversine of identical points must be ~0"))

(deftest test-haversine-equator
  ;; 1° longitude at equator ≈ 111,195 m (Earth ~111.2 km/°)
  (let [d (reverse-ns/haversine-m 0.0 0.0 0.0 1.0)]
    (is (< (Math/abs (- d 111195.0)) 100.0)
        "1° longitude at equator must be ~111195 m")))

(deftest test-haversine-symmetric
  (let [d1 (reverse-ns/haversine-m 35.0 139.0 36.0 140.0)
        d2 (reverse-ns/haversine-m 36.0 140.0 35.0 139.0)]
    (is (< (Math/abs (- d1 d2)) 0.01) "haversine must be symmetric")))

(deftest test-haversine-tokyo-shibuya
  ;; Approximate Tokyo Station → Shibuya distance ~5.7 km
  (let [d (reverse-ns/haversine-m 35.6812 139.7671 35.6580 139.7016)]
    (is (> d 4000) "Tokyo→Shibuya must be >4 km")
    (is (< d 8000) "Tokyo→Shibuya must be <8 km")))

;; ── reverse-geocode tests (in-memory store) ──────────────────────────────────

(defn- make-rev-store []
  (kl/build-store-from-features
   {"bldg.marunouchi" {":feature/id"       "bldg.marunouchi"
                       ":feature/label"     ":building"
                       ":feature/name"      "Marunouchi Building"
                       ":feature/lat"       35.6809
                       ":feature/lon"       139.7644
                       ":feature.cell/r10"  TOKYO-CELL
                       ":feature/sourcing"  ":representative"}
    "st.tokyo"         {":feature/id"       "st.tokyo"
                       ":feature/label"     ":station"
                       ":feature/name"      "Tokyo Station"
                       ":feature/lat"       TST-LAT
                       ":feature/lon"       TST-LON
                       ":feature.cell/r10"  TOKYO-CELL
                       ":feature/sourcing"  ":representative"}}))

(defn- nearby-cells [_lat _lon _res _ring]
  ;; Stub: always return the Tokyo Station cell
  [TOKYO-CELL])

(deftest test-reverse-geocode-finds-nearest
  (let [store  (make-rev-store)
        qfn    (kl/make-query-fn store)
        result (reverse-ns/reverse-geocode qfn nearby-cells TST-LAT TST-LON
                                           :res 10 :ring 2)]
    (is (pos? (count result)) "expected ≥1 reverse-geocode result")
    ;; nearest feature to Tokyo Station is Tokyo Station itself
    (is (= "st.tokyo" (:id (first result))))))

(deftest test-reverse-geocode-label-filter
  (let [store  (make-rev-store)
        qfn    (kl/make-query-fn store)
        result (reverse-ns/reverse-geocode qfn nearby-cells TST-LAT TST-LON
                                           :res 10 :ring 2 :labels [":building"])]
    (is (every? #(= ":building" (:label %)) result)
        "label filter must only return :building features")))

(deftest test-reverse-geocode-nil-ring-cells-returns-empty
  ;; ring-cells-fn returns nil → fail-soft, no exception
  (let [store  (make-rev-store)
        qfn    (kl/make-query-fn store)
        result (reverse-ns/reverse-geocode qfn (fn [& _] nil) TST-LAT TST-LON)]
    (is (empty? result) "nil ring-cells → empty results")))

(deftest test-reverse-geocode-sorted-by-distance
  (let [store  (make-rev-store)
        qfn    (kl/make-query-fn store)
        result (reverse-ns/reverse-geocode qfn nearby-cells TST-LAT TST-LON)]
    (when (>= (count result) 2)
      (is (apply <= (map :distance-m result))
          "results must be sorted nearest-first"))))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-reverse))
     (run-tests)))
