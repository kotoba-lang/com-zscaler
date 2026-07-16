(ns maps.tests.test-transit
  "maps — transit method tests (ADR-2606064500). stdlib; network-free.
  1:1 Clojure port of `methods/test_transit.py`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [maps.methods.transit   :as transit]
            [maps.tests.kotoba-local :as kl]))

(def ^:private STOP-ID "f.station.tokyo")
(def ^:private ROUTE-ID "route.tokaido-shinkansen")
(def ^:private TRIP-ID  "trip.tokaido.001")

(defn- make-transit-store []
  (let [store (kl/make-store)]
    (kl/ingest-batch!
     store
     {"entities"
      [{"id"     (str TRIP-ID ".stop.1")
        "claims" [{"pred" "transit.stop-time/stop"            "value" STOP-ID}
                  {"pred" "transit.stop-time/trip"            "value" TRIP-ID}
                  {"pred" "transit.stop-time/departure-time"  "value" "09:00:00"}
                  {"pred" "transit.stop-time/arrival-time"    "value" "08:58:00"}
                  {"pred" "transit.stop-time/sequence"        "value" "1"}
                  {"pred" "transit.stop-time/headsign"        "value" "Osaka"}]}
       {"id"     (str TRIP-ID ".stop.2")
        "claims" [{"pred" "transit.stop-time/stop"            "value" STOP-ID}
                  {"pred" "transit.stop-time/trip"            "value" TRIP-ID}
                  {"pred" "transit.stop-time/departure-time"  "value" "11:30:00"}
                  {"pred" "transit.stop-time/arrival-time"    "value" "11:28:00"}
                  {"pred" "transit.stop-time/sequence"        "value" "2"}
                  {"pred" "transit.stop-time/headsign"        "value" "Osaka"}]}
       ;; Past-midnight GTFS time (>24h)
       {"id"     (str TRIP-ID ".stop.3")
        "claims" [{"pred" "transit.stop-time/stop"            "value" STOP-ID}
                  {"pred" "transit.stop-time/trip"            "value" TRIP-ID}
                  {"pred" "transit.stop-time/departure-time"  "value" "25:10:00"}
                  {"pred" "transit.stop-time/arrival-time"    "value" "25:08:00"}
                  {"pred" "transit.stop-time/sequence"        "value" "3"}
                  {"pred" "transit.stop-time/headsign"        "value" "Osaka"}]}
       ;; Trip entity for route test
       {"id"     TRIP-ID
        "claims" [{"pred" "transit.trip/route"     "value" ROUTE-ID}
                  {"pred" "transit.trip/headsign"  "value" "Osaka"}
                  {"pred" "transit.trip/service"   "value" "weekday"}
                  {"pred" "transit.trip/direction" "value" "1"}]}]})
    store))

(deftest test-next-departures-all
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        deps   (transit/next-departures-at-stop qfn STOP-ID)]
    (is (pos? (count deps)) "expected departures at the stop")))

(deftest test-next-departures-after-filter
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        deps   (transit/next-departures-at-stop qfn STOP-ID :after "10:00:00")]
    ;; 09:00 departs BEFORE 10:00 → not included
    (doseq [d deps]
      (is (>= (compare (:departure d) "10:00:00") 0)
          (str "departure " (:departure d) " before :after filter")))
    ;; 11:30 and 25:10 are after 10:00 → included
    (is (some #(= "11:30:00" (:departure %)) deps))
    (is (some #(= "25:10:00" (:departure %)) deps))))

(deftest test-next-departures-sorted
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        deps   (transit/next-departures-at-stop qfn STOP-ID)]
    (when (>= (count deps) 2)
      (let [times (map :departure deps)]
        (is (= times (sort times)) "departures must be sorted by departure-time text")))))

(deftest test-next-departures-past-midnight
  ;; GTFS times >24:00:00 must sort AFTER normal times (text sort is correct)
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        deps   (transit/next-departures-at-stop qfn STOP-ID :limit 10)]
    (is (some #(str/starts-with? (or (:departure %) "") "25:") deps)
        "past-midnight departure (25:…) must appear")))

(deftest test-next-departures-limit
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        deps   (transit/next-departures-at-stop qfn STOP-ID :limit 1)]
    (is (<= (count deps) 1) "limit=1 must return ≤1 result")))

(deftest test-trips-on-route
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        trips  (transit/trips-on-route qfn ROUTE-ID)]
    (is (pos? (count trips)) "expected trips on route")
    (is (some #(= TRIP-ID (:trip %)) trips) "TRIP-ID must appear in trips-on-route")))

(deftest test-trips-unknown-route
  (let [store  (make-transit-store)
        qfn    (kl/make-query-fn store)
        trips  (transit/trips-on-route qfn "route.unknown")]
    (is (empty? trips) "unknown route must return empty")))

#?(:clj
   (when (= *ns* (find-ns 'maps.tests.test-transit))
     (run-tests)))
