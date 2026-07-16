(ns watatsuna.viz.build-viz-data
  "1:1 port of viz/build_viz_data.py build_payload (ADR-2606012600). Reads a cable graph, runs the
  already-ported analyzer, and builds the aggregate-first RESILIENCE viz payload: stations (sized by
  landed capacity, coloured by chokepoint), segments (arcing through the chokepoints they traverse),
  a ranked chokepoint-load panel, and open/closed fault bulletins. NEVER a target-list — it surfaces
  where to ADD redundancy (G2; watatsumi N8).

  Ported: build_payload (pure-stdlib, float-light — one py-round on the capacity sum), exposed as a
  pure `payload` over the classified buckets + analyze result, plus `build-payload` that reads a
  graph file. OMITTED (IO leg, not ported): main() merged/seed selection + JSON write + the
  _template.htm / _globe_template.htm viewer render."
  (:require [clojure.string :as str]
            [watatsuna.methods._edn :as edn]
            [watatsuna.methods.analyze :as an]))

(defn- kw* [v] (str/replace (str v) #"^:+" ""))

;; analyze keeps first-touch order in metadata on the maps it returns; reuse it verbatim so the
;; payload ordering is byte-identical to the Python dict-iteration order.
(defn- station-order [stations] (get (meta stations) :watatsuna.methods.analyze/sorder (keys stations)))
(defn- choke-order [load] (or (get (meta load) :watatsuna.methods.analyze/choke-order) (keys load)))

(defn payload
  "Pure port of build_payload's body. Mirrors build_payload(graph_path) once the file is read +
  classified + analyzed; `source` is the graph file basename."
  [cables stations links segs faults a source]
  (let [sd (get a "station_degree")
        scap (get a "station_capacity")
        scables (get a "station_cables")
        st-out (vec (for [s (station-order stations)
                          :let [meta (get stations s)]
                          :when (and (not= 0 (get sd s))
                                     (contains? meta ":station/lat")
                                     (contains? meta ":station/lon"))]
                      {"id" s
                       "name" (get meta ":station/name" s)
                       "country" (get meta ":station/country" "??")
                       "lat" (get meta ":station/lat")
                       "lon" (get meta ":station/lon")
                       "degree" (get sd s)
                       "capacity" (get scap s)
                       "chokepoints" (mapv kw* (or (get meta ":station/chokepoint") []))
                       "cables" (vec (sort (map (fn [c] (get-in cables [c ":cable/name"] c))
                                                (get scables s #{}))))}))
        seg-out (vec (for [sg segs
                           :let [fr (get stations (get sg ":cable.seg/from"))
                                 to (get stations (get sg ":cable.seg/to"))]
                           :when (and fr to
                                      (contains? fr ":station/lat")
                                      (contains? to ":station/lat"))]
                       {"from" [(get fr ":station/lat") (get fr ":station/lon")]
                        "to" [(get to ":station/lat") (get to ":station/lon")]
                        "traverses" (mapv kw* (or (get sg ":cable.seg/traverses") []))
                        "cable" (get-in cables [(get sg ":cable.seg/cable") ":cable/name"]
                                        (get sg ":cable.seg/cable"))}))
        load (get a "choke_load")
        ccount (get a "choke_count")
        choke-out (vec (for [cp (sort-by (fn [k] (- (double (get load k)))) (choke-order load))]
                         {"name" (kw* cp) "load" (get load cp) "count" (get ccount cp)}))
        fault-out (mapv (fn [f]
                          {"id" (get f ":cable.fault/id")
                           "cable" (get-in cables [(get f ":cable.fault/cable") ":cable/name"]
                                           (get f ":cable.fault/cable"))
                           "kind" (kw* (get f ":cable.fault/kind" ""))
                           "detected" (get f ":cable.fault/detected-at" "")
                           "restored" (or (get f ":cable.fault/restored-at") "open")})
                        faults)]
    {"source" source
     "cables" (count cables)
     "stations" (count st-out)
     "totalCapacity" (an/py-round (reduce + 0.0 (vals (get a "cap"))) 1)
     "chokepoints" choke-out
     "station_list" st-out
     "segments" seg-out
     "faults" fault-out}))

(defn build-payload
  "Port of build_payload(graph_path) — reads + classifies + analyzes the graph file, then builds
  the payload. `source` = the file's basename (path tail)."
  [graph-path]
  (let [rows (edn/load-edn graph-path)
        {:keys [cables stations links segs faults]} (an/classify rows)
        a (an/analyze cables stations links segs faults)
        source (last (str/split (str graph-path) #"/"))]
    (payload cables stations links segs faults a source)))
