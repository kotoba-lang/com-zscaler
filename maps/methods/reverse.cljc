(ns maps.methods.reverse
  "maps — kotoba-native reverse geocoding (ADR-2606064500 R2). stdlib only.
  1:1 Clojure port of `methods/reverse.py`.

  H3 cell-ring proximity reverse geocode:
    cell  = latlng_to_cell(lat, lon, res)
    cells = grid_disk(cell, ring)
    cands = AVET(:feature.cell/r{res}, cells)
    → haversine sort → label-filter → top N.

  H3 is not available in Clojure stdlib; ring-cells returns nil (fails-soft to []).
  haversine-m is pure and always testable.

  query-fn abstraction: (fn [pred objects limit] → entity-seq).
  Portable .cljc."
  (:require [clojure.string :as str]))

(def ^:private earth-r 6371000.0)

(defn haversine-m
  "Great-circle distance between two WGS84 points, in metres."
  ^double [^double lat1 ^double lon1 ^double lat2 ^double lon2]
  (let [to-rad #(* % (/ Math/PI 180.0))
        p1     (to-rad lat1)
        p2     (to-rad lat2)
        dp     (to-rad (- lat2 lat1))
        dl     (to-rad (- lon2 lon1))
        a      (+ (* (Math/sin (/ dp 2)) (Math/sin (/ dp 2)))
                   (* (Math/cos p1) (Math/cos p2)
                      (Math/sin (/ dl 2)) (Math/sin (/ dl 2))))]
    (* 2.0 earth-r (Math/asin (Math/min 1.0 (Math/sqrt a))))))

(defn- first-claim [claims pred]
  (some #(when (= (get % "pred") pred) (get % "value")) claims))

(defn reverse-geocode
  "Nearest features to (lat, lon), nearest first.
  Returns [{:id :name :label :lat :lon :distance-m}]. [] if ring-cells-fn unavailable.

  ring-cells-fn: (fn [lat lon res ring] → [cell-strings] | nil)
    Pass nil or a fn that returns nil to fail-soft (no H3 in stdlib).
  query-fn: (fn [pred objects limit] → entity-seq)."
  [query-fn ring-cells-fn lat lon
   & {:keys [res ring labels limit] :or {res 10 ring 2 limit 5}}]
  (let [cells (when ring-cells-fn (ring-cells-fn lat lon res ring))]
    (when (seq cells)
      (let [want (when labels
                   (set (map #(if (str/starts-with? (str %) ":") (str %) (str ":" %)) labels)))
            results
            (reduce
             (fn [acc e]
               (let [claims (get e "claims" [])
                     flat   (when-let [v (first-claim claims "feature/lat")]
                              (try (Double/parseDouble (str v))
                                   (catch #?(:clj Exception :cljs :default) _ nil)))
                     flon   (when-let [v (first-claim claims "feature/lon")]
                              (try (Double/parseDouble (str v))
                                   (catch #?(:clj Exception :cljs :default) _ nil)))
                     label  (first-claim claims "feature/label")
                     name   (first-claim claims "feature/name")]
                 (if (and flat flon (or (nil? want) (contains? want label)))
                   (conj acc {:id         (get e "id")
                              :name       name
                              :label      label
                              :lat        flat
                              :lon        flon
                              :distance-m (Math/round (haversine-m lat lon flat flon))})
                   acc)))
             []
             (query-fn (str "feature.cell/r" (int res)) cells 4000))]
        (take limit (sort-by :distance-m results))))))
