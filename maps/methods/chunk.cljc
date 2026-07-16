(ns maps.methods.chunk
  "maps — kotoba-native chunk read (ADR-2606064500 §2). stdlib only.
  1:1 Clojure port of `methods/chunk.py`.

  Hot-path getChunk successor: AVET(:feature.cell/r{lod}, cells) → grouped GeoJSON.

  query-fn abstraction: (fn [pred objects limit] → [{\"id\" id \"claims\" [...]}])
    - production: wrap HTTP POST to kotoba graph.sparql
    - tests: in-memory KotobaLocal

  Portable .cljc."
  (:require [clojure.string :as str]))

(def ^:private label-map
  {"Place"    ":place"   "Road"         ":road"       "Railway"     ":railway"
   "Building" ":building" "River"       ":river"      "Lake"        ":lake"
   "Coastline" ":coastline" "AdminArea" ":admin-area" "Mountain"    ":mountain"
   "Port"     ":port"    "Airport"      ":airport"    "Station"     ":station"
   "BusStop"  ":bus-stop" "BusRoute"   ":bus-route"  "SeaRoute"   ":sea-route"
   "AirRoute" ":air-route" "LegalEntity" ":legal-entity" "LandRegistry" ":registry"})

(defn fold-label
  "PascalCase or bare string → stored :kebab-keyword."
  [label]
  (let [s (str label)]
    (if (str/starts-with? s ":")
      s
      (or (get label-map s)
          (str ":" (-> s str/trim str/lower-case (str/replace " " "-")))))))

(defn- first-claim [claims pred]
  (some #(when (= (get % "pred") pred) (get % "value")) claims))

(defn- ->feature
  "Entity map + lod → [owner-cell label GeoJSON-Feature] or nil."
  [entity lod]
  (let [claims (get entity "claims" [])
        owner  (first-claim claims (str "feature.cell/r" (int lod)))
        label  (first-claim claims "feature/label")
        geom   (when-let [g (first-claim claims "feature/geometry")]
                 (try #?(:clj ((requiring-resolve 'cheshire.core/parse-string) g)
                         :cljs (js->clj (js/JSON.parse g) :keywordize-keys false))
                      (catch #?(:clj Exception :cljs :default) _ nil)))
        geom   (or geom
                   (let [lat (first-claim claims "feature/lat")
                         lon (first-claim claims "feature/lon")]
                     (when (and lat lon)
                       (try {"type" "Point"
                             "coordinates" [(Double/parseDouble (str lon))
                                            (Double/parseDouble (str lat))]}
                            (catch #?(:clj Exception :cljs :default) _ nil)))))]
    (when (and owner label)
      [owner label
       {"type"       "Feature"
        "geometry"   geom
        "properties" {"id"       (get entity "id")
                      "name"     (first-claim claims "feature/name")
                      "label"    label
                      "category" (first-claim claims "feature/category")
                      "heightM"  (first-claim claims "feature/height-m")
                      "levels"   (first-claim claims "feature/levels")}}])))

(defn get-chunk
  "getChunk-equivalent: per requested cell, features owning it, grouped by label.
  query-fn: (fn [pred objects limit] → entity-seq).
  Returns {:chunks {cell {label [Feature]}} :lod lod :total N}."
  [query-fn h3-cells lod & {:keys [labels limit] :or {limit 500}}]
  (let [cells  (vec (distinct (map str h3-cells)))
        lod    (int lod)
        want   (when labels
                 (set (map fold-label (map str labels))))
        cellset (set cells)
        chunks  (volatile! (into {} (map #(vector % {}) cells)))
        total   (volatile! 0)]
    (doseq [entity (query-fn (str "feature.cell/r" lod) cells 8000)]
      (when-let [[owner label feat] (->feature entity lod)]
        (when (and (contains? cellset owner)
                   (or (nil? want) (contains? want label)))
          (let [bucket (get-in @chunks [owner label] [])]
            (when (< (count bucket) limit)
              (vswap! chunks update-in [owner label] (fnil conj []) feat)
              (vswap! total inc))))))
    {:chunks @chunks :lod lod :total @total}))
