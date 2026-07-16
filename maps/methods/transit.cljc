(ns maps.methods.transit
  "maps — kotoba-native transit reads (ADR-2606064500 R2 aux). stdlib only.
  1:1 Clojure port of `methods/transit.py`.

  AVET successors to legacy RisingWave idx_maps_stop_time_stop_dep /
  idx_maps_trip_route:

    next-departures-at-stop: AVET(:transit.stop-time/stop, stop-id)
      → filter dep ≥ after → sort by departure-time (HH:MM:SS, sortable within service day)
    trips-on-route: AVET(:transit.trip/route, route-id)

  GTFS departure_time may exceed 24:00:00 (past-midnight trips); text sort is correct.

  query-fn: (fn [pred objects limit] → entity-seq), OR a kotoba endpoint URL string
  (wrapped via maps.methods.search/http-avet-fn — the production HTTP AVET path).
  Portable .cljc."
  (:require [clojure.string :as str]
            [maps.methods.search :as search-ns]))

(defn- claims-map [entity]
  (reduce (fn [m c] (assoc m (get c "pred") (get c "value")))
          {}
          (get entity "claims" [])))

(defn- resolve-query-fn [query-fn-or-endpoint]
  #?(:clj (if (string? query-fn-or-endpoint)
            (search-ns/http-avet-fn query-fn-or-endpoint)
            query-fn-or-endpoint)
     :cljs query-fn-or-endpoint))

(defn next-departures-at-stop
  "Next scheduled departures at a stop after `after` (HH:MM:SS), earliest first.
  Returns [{:stop-time :trip :departure :arrival :headsign :sequence}]."
  [query-fn-or-endpoint stop-id & {:keys [after limit] :or {after "00:00:00" limit 10}}]
  (let [query-fn (resolve-query-fn query-fn-or-endpoint)
        rows
        (reduce
         (fn [acc e]
           (let [c   (claims-map e)
                 dep (get c "transit.stop-time/departure-time")]
             (if (and dep (>= (compare dep after) 0))
               (conj acc {:stop-time (get e "id")
                          :trip      (get c "transit.stop-time/trip")
                          :departure dep
                          :arrival   (get c "transit.stop-time/arrival-time")
                          :headsign  (get c "transit.stop-time/headsign")
                          :sequence  (get c "transit.stop-time/sequence")})
               acc)))
         []
         (query-fn "transit.stop-time/stop" [stop-id] 2000))]
    (take limit (sort-by :departure rows))))

(defn trips-on-route
  "All trips on a route (idx_maps_trip_route successor).
  Returns [{:trip :headsign :service :direction}]."
  [query-fn-or-endpoint route-id & {:keys [limit] :or {limit 2000}}]
  (let [query-fn (resolve-query-fn query-fn-or-endpoint)]
    (reduce
     (fn [acc e]
       (let [c (claims-map e)]
         (conj acc {:trip      (get e "id")
                    :headsign  (get c "transit.trip/headsign")
                    :service   (get c "transit.trip/service")
                    :direction (get c "transit.trip/direction")})))
     []
     (query-fn "transit.trip/route" [route-id] limit))))
