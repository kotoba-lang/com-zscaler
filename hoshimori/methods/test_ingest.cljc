(ns hoshimori.methods.test-ingest
  "Tests for the hoshimori ingest bridge (py→cljc port, ADR-2606073600 §G7).
  The Python ingest.py shipped without a unit test; this locks the pure-fn
  behavior of the cljc port, incl. the G1 no-ephemeris invariant on its output."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [hoshimori.methods.ingest :as ing]))

(deftest regime-buckets
  (is (= ":leo" (ing/regime "500" "480" "95")))
  (is (= ":meo" (ing/regime "20000" "19000" "720")))
  (is (= ":geo" (ing/regime "35786" "35780" "1436")))
  (is (= ":heo" (ing/regime "40000" "500" "700")))      ; high apogee
  (is (= ":heo" (ing/regime "30000" "500" "600")))      ; ap-pe > 20000 eccentric
  (is (nil? (ing/regime "" "" "")))                      ; unparseable → nil
  (is (nil? (ing/regime "0" "0" "0"))))                  ; ap<=0 → nil

(deftest aggregate-counts-only
  (let [rows [{"OWNER" "US"  "OBJECT_TYPE" "PAY" "APOGEE" "550" "PERIGEE" "540" "PERIOD" "95"   "DECAY_DATE" ""}
              {"OWNER" "US"  "OBJECT_TYPE" "DEB" "APOGEE" "600" "PERIGEE" "590" "PERIOD" "96"   "DECAY_DATE" ""}
              {"OWNER" "PRC" "OBJECT_TYPE" "R/B" "APOGEE" "35786" "PERIGEE" "35770" "PERIOD" "1436" "DECAY_DATE" ""}
              ;; decayed object is dropped (no longer on orbit)
              {"OWNER" "US"  "OBJECT_TYPE" "PAY" "APOGEE" "550" "PERIGEE" "540" "DECAY_DATE" "2020-01-01"}
              ;; missing OWNER → "TBD"
              {"OBJECT_TYPE" "PAY" "APOGEE" "800" "PERIGEE" "790" "PERIOD" "100" "DECAY_DATE" ""}]
        [owners regimes] (ing/aggregate rows)]
    (is (= {:pay 1 :rb 0 :deb 1 :total 2} (get owners "US")))
    (is (= {:pay 0 :rb 1 :deb 0 :total 1} (get owners "PRC")))
    (is (= {:pay 1 :rb 0 :deb 0 :total 1} (get owners "TBD")))
    ;; only PAY + R/B contribute to regime occupancy (DEB excluded)
    (is (= {":leo" 2 ":geo" 1} regimes))))

(deftest emit-shapes
  (let [op (ing/emit-operator "US" {:total 12 :pay 9 :rb 2 :deb 1})]
    (is (str/includes? op ":organism/id \"orbit.cat.us\""))
    (is (str/includes? op ":op/jurisdiction \"US\""))
    (is (str/includes? op ":op/object-count 12"))
    (is (str/includes? op ":organism/sourcing :authoritative")))
  ;; unmapped owner code passes through (slug lowercased, slash→dash)
  (let [op (ing/emit-operator "X/Y" {:total 5 :pay 5 :rb 0 :deb 0})]
    (is (str/includes? op "orbit.cat.x-y")))
  (let [oc (ing/emit-occupancy ":leo" 4200)]
    (is (str/includes? oc ":organism/id \"orbit.occ.leo\""))
    (is (str/includes? oc ":occ/regime :leo"))
    (is (str/includes? oc ":occ/on-orbit-count 4200"))))

(deftest merge-graph-valid-edn-and-g1
  (let [seed "[\n {:organism/id \"x\" :organism/kind :regime}\n]"
        rows (concat (repeat 25 {"OWNER" "US"  "OBJECT_TYPE" "PAY" "APOGEE" "550" "PERIGEE" "540" "PERIOD" "95"   "DECAY_DATE" ""})
                     (repeat 22 {"OWNER" "PRC" "OBJECT_TYPE" "R/B" "APOGEE" "35786" "PERIGEE" "35770" "PERIOD" "1436" "DECAY_DATE" ""}))
        [owners regimes] (ing/aggregate rows)
        merged (ing/merge-graph seed owners regimes)
        parsed (edn/read-string merged)]
    ;; merged document is a single valid EDN vector
    (is (vector? parsed))
    (is (>= (count parsed) 3))                 ; seed node + ≥2 aggregate nodes
    (is (str/includes? merged "CelesTrak SATCAT aggregate ingest"))
    ;; only owners with ≥20 on-orbit objects appear (the 20-count threshold)
    (is (str/includes? merged "orbit.cat.us"))
    (is (str/includes? merged "orbit.cat.cn"))
    ;; G1 — no per-object positional / ephemeris attribute leaks into the merged graph
    (doseq [k [":geo/lat" ":geo/lon" ":obj/altitude-km" ":eph/" ":tle/" ":obj/velocity" "APOGEE" "PERIGEE"]]
      (is (not (str/includes? merged k)) (str "G1: no-ephemeris — found leaked key " k)))))
