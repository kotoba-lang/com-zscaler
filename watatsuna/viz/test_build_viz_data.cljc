(ns watatsuna.viz.test-build-viz-data
  "Tests for the watatsuna resilience viz payload builder (ADR-2606012600 port of build_viz_data
  build_payload). A hand rows fixture (driven through the trusted analyze classify+analyze) pins
  every filter/transform — the degree==0 + missing-lat/lon station skips, chokepoint keyword-strip,
  sorted cable-name list, segment endpoint/lat guards, chokepoint-load panel, fault open-vs-restored
  — and a seed run pins the aggregate counts + the documented Malacca top chokepoint."
  (:require [clojure.test :refer [deftest is]]
            [watatsuna.methods.analyze :as an]
            [watatsuna.viz.build-viz-data :as v]))

(def ^:private rows-fix
  [{":cable/id" "ca1" ":cable/name" "Alpha" ":cable/design-capacity-tbps" 100.0}
   {":cable/id" "ca2" ":cable/name" "Beta" ":cable/design-capacity-tbps" 50.0}
   {":station/id" "s1" ":station/name" "Land One" ":station/country" "US" ":station/lat" 10.0 ":station/lon" 20.0 ":station/chokepoint" [":malacca"]}
   {":station/id" "s2" ":station/name" "Land Two" ":station/country" "JP" ":station/lat" 30.0 ":station/lon" 40.0}
   {":station/id" "s3" ":station/name" "No Coords" ":station/country" "FR"}          ; degree>0 but no lat/lon → skipped
   {":station/id" "s4" ":station/country" "DE" ":station/lat" 1.0 ":station/lon" 2.0} ; degree 0 → skipped
   {":cable.link/id" "l1" ":cable.link/cable" "ca1" ":cable.link/station" "s1"}
   {":cable.link/id" "l2" ":cable.link/cable" "ca2" ":cable.link/station" "s1"}        ; s1 lands 2 cables
   {":cable.link/id" "l3" ":cable.link/cable" "ca1" ":cable.link/station" "s2"}
   {":cable.link/id" "l4" ":cable.link/cable" "ca1" ":cable.link/station" "s3"}        ; s3 degree 1, no coords
   {":cable.seg/id" "sg1" ":cable.seg/from" "s1" ":cable.seg/to" "s2" ":cable.seg/cable" "ca1" ":cable.seg/traverses" [":malacca"]}
   {":cable.seg/id" "sg2" ":cable.seg/from" "s1" ":cable.seg/to" "s3" ":cable.seg/cable" "ca1"} ; s3 no lat → skipped
   {":cable.fault/id" "f1" ":cable.fault/cable" "ca1" ":cable.fault/kind" ":under-investigation" ":cable.fault/detected-at" "2026-01-01" ":cable.fault/restored-at" "2026-01-05"}
   {":cable.fault/id" "f2" ":cable.fault/cable" "ca2" ":cable.fault/kind" ":shunt-fault" ":cable.fault/detected-at" "2026-02-01"}])

(defn- payload-fix []
  (let [{:keys [cables stations links segs faults]} (an/classify rows-fix)
        a (an/analyze cables stations links segs faults)]
    (v/payload cables stations links segs faults a "fixture.edn")))

(deftest test-station-filters-and-fields
  (let [p (payload-fix)
        s1 (first (get p "station_list"))]
    (is (= "fixture.edn" (get p "source")))
    (is (= 2 (get p "cables")))
    (is (= 2 (get p "stations")))                ; s3 (no coords) + s4 (degree 0) skipped
    (is (= 150.0 (get p "totalCapacity")))       ; round(100+50, 1)
    (is (= "s1" (get s1 "id")))
    (is (= 2 (get s1 "degree")))
    (is (= ["Alpha" "Beta"] (get s1 "cables")))  ; sorted cable names
    (is (= ["malacca"] (get s1 "chokepoints")))  ; keyword stripped
    (is (= 150.0 (get s1 "capacity")))
    ;; s3 + s4 absent from station_list
    (is (nil? (first (filter #(#{"s3" "s4"} (get % "id")) (get p "station_list")))))))

(deftest test-segments-and-chokepoints
  (let [p (payload-fix)]
    (is (= 1 (count (get p "segments"))))        ; sg2 skipped (s3 no lat)
    (is (= {"from" [10.0 20.0] "to" [30.0 40.0] "traverses" ["malacca"] "cable" "Alpha"}
           (first (get p "segments"))))
    (is (= [{"name" "malacca" "load" 150.0 "count" 2}] (get p "chokepoints")))))

(deftest test-faults-open-and-restored
  (let [faults (get (payload-fix) "faults")]
    (is (= 2 (count faults)))
    (is (= {"id" "f1" "cable" "Alpha" "kind" "under-investigation"
            "detected" "2026-01-01" "restored" "2026-01-05"} (first faults)))
    (is (= "open" (get (second faults) "restored")))   ; no :restored-at → "open"
    (is (= "shunt-fault" (get (second faults) "kind")))))

(deftest test-seed-aggregate
  (let [p (v/build-payload "20-actors/watatsuna/data/seed-cable-graph.kotoba.edn")]
    (is (= "seed-cable-graph.kotoba.edn" (get p "source")))
    (is (= 14 (get p "cables")))
    (is (= 22 (get p "stations")))
    (is (= 1748.2 (get p "totalCapacity")))
    (is (= 11 (count (get p "segments"))))
    (is (= 2 (count (get p "faults"))))
    ;; documented finding: Malacca carries the top chokepoint load
    (is (= "malacca" (get (first (get p "chokepoints")) "name")))
    (is (= 490.16 (get (first (get p "chokepoints")) "load")))))
