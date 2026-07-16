(ns maps.methods.test-transit
  "test_transit.py — kotoba-native transit read tests (ADR-2606064500 R2 aux). unittest →
  clojure.test. 1:1 port; runs the write→read loop against the in-process kotoba stand-in
  server (h3-independent). The Python __main__ runner is omitted."
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [maps.methods.transit :as transit]
            [maps.methods.ingest :as ingest]
            #?(:clj [maps.methods.kotoba-local-server :as kls])))

(def ^:private token "member-did")
;; Two stops on one route; departures deliberately OUT OF ORDER + one before the `after` cutoff.
(def ^:private stop-times
  [["S1" "T1" 1 "08:05:00"] ["S1" "T2" 1 "08:01:00"] ["S1" "T3" 1 "07:30:00"]
   ["S1" "T4" 1 "25:10:00"]   ; past-midnight (GTFS >24:00:00) — must sort AFTER 08:xx
   ["S2" "T1" 2 "08:09:00"]])
(def ^:private trips ["T1" "T2" "T3" "T4"])

(defn- batch []
  {"entities"
   (vec
    (concat
     (for [trip trips]
       {"id" (str "trip.f." trip) "type" "transit-trip"
        "claims" [{"pred" "transit.trip/route" "value" "ROUTE-M"}
                  {"pred" "transit.trip/headsign" "value" (str "to " trip)}
                  {"pred" "transit.trip/service" "value" "weekday"}]})
     (for [[stop trip seq dep] stop-times]
       {"id" (str "stoptime.f." trip "." seq "." stop) "type" "transit-stop-time"
        "claims" [{"pred" "transit.stop-time/stop" "value" stop}
                  {"pred" "transit.stop-time/trip" "value" (str "trip.f." trip)}
                  {"pred" "transit.stop-time/departure-time" "value" dep}
                  {"pred" "transit.stop-time/sequence" "value" (str seq)}
                  {"pred" "transit.stop-time/headsign" "value" (str "to " trip)}]}))) })

#?(:clj (def ^:private server (atom nil)))
#?(:clj (def ^:private base (atom nil)))

#?(:clj
   (defn- with-server [f]
     (let [srv (kls/serve 0 token)]
       (kls/serve-forever srv)
       (reset! server srv)
       (reset! base (str "http://127.0.0.1:" (:port srv)))
       (ingest/push-batch (batch) token @base)
       (try (f) (finally (kls/shutdown srv))))))

#?(:clj (use-fixtures :once with-server))

(deftest test-sorted-earliest-first
  #?(:clj
     (is (= (mapv :departure (transit/next-departures-at-stop @base "S1"))
            ["07:30:00" "08:01:00" "08:05:00" "25:10:00"]))))

(deftest test-after-cutoff-filters
  #?(:clj
     (is (= (mapv :departure (transit/next-departures-at-stop @base "S1" :after "08:00:00"))
            ["08:01:00" "08:05:00" "25:10:00"]))))

(deftest test-limit
  #?(:clj
     (let [rows (transit/next-departures-at-stop @base "S1" :after "00:00:00" :limit 2)]
       (is (= (count rows) 2))
       (is (= (:departure (first rows)) "07:30:00")))))

(deftest test-stop-isolation
  #?(:clj
     (is (= (mapv :departure (transit/next-departures-at-stop @base "S2"))
            ["08:09:00"]))))

(deftest test-unknown-stop-empty
  #?(:clj (is (= (transit/next-departures-at-stop @base "NOPE") []))))

(deftest test-fields-present
  #?(:clj
     (let [r (first (transit/next-departures-at-stop @base "S1" :after "08:00:00"))]
       (is (= (:trip r) "trip.f.T2"))
       (is (= (:headsign r) "to T2")))))

(deftest test-trips-on-route
  #?(:clj
     (is (= (set (map :trip (transit/trips-on-route @base "ROUTE-M")))
            #{"trip.f.T1" "trip.f.T2" "trip.f.T3" "trip.f.T4"}))))

(deftest test-endpoint-down-fails-soft
  (is (= (transit/next-departures-at-stop "http://127.0.0.1:1" "S1") [])))
