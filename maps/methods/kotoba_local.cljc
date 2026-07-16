(ns maps.methods.kotoba-local
  "kotoba_local.py — an in-memory EAVT/AVET reference store for the maps spatial substrate.
  1:1 Clojure port of `methods/kotoba_local.py` (ADR-2606064500 §2).

  NOT the production kotoba engine — a small, dependency-free REFERENCE implementation of the
  two arrangements the maps read hot-path relies on (EAVT + AVET), so the H3-cell-as-Datom +
  AVET design can be EXECUTED and TESTED offline.

  House style: the store is a Clojure atom holding {:eavt ... :avet ...} (the Python class
  instance state). Functions take the store atom explicitly (Python `self`). Maps stay
  string-keyed; ':feature/label' style keywords are kept AS strings, exactly like Python.
  Pure (in-memory only); no host I/O."
  (:require [clojure.string :as str]))

(defn new-store
  "KotobaLocal.__init__ — a fresh in-memory store.
  :eavt  subject -> {pred -> [values]}
  :avet  [pred object] -> #{subjects}"
  []
  (atom {:eavt {} :avet {}}))

(defn ingest-batch
  "Ingest a kg.ingest_batch body: {\"entities\": [{id, claims:[{pred,value}], ...}]}.
  Returns the count of entities ingested (mirrors Python: counts every entity with an id,
  even claim-less ones)."
  [store batch]
  (let [entities (get batch "entities" [])]
    (loop [es entities n 0]
      (if (empty? es)
        n
        (let [ent (first es)
              sid (get ent "id")]
          (if-not sid
            (recur (rest es) n)
            (do
              (swap! store update-in [:eavt sid] (fnil identity {}))
              (doseq [c (get ent "claims" [])]
                (let [pred (get c "pred") val (get c "value")]
                  (when-not (or (nil? pred) (nil? val))
                    (swap! store update-in [:eavt sid pred] (fnil conj []) val)
                    (swap! store update-in [:avet [pred (str val)]] (fnil conj #{}) sid))))
              (recur (rest es) (inc n)))))))))

(defn- first-val
  "self._first — first stored value for (sid, pred), or nil."
  [store sid pred]
  (let [vals (get-in @store [:eavt sid pred])]
    (when (seq vals) (first vals))))

(defn- materialize
  "self._materialize — a feature subject → a getChunk-shaped row."
  [store sid]
  (let [a (get-in @store [:eavt sid])]
    (reduce
     (fn [row pred]
       (let [v (get a pred)]
         (if (seq v)
           (assoc row (second (str/split pred #"/" 2)) (first v))
           row)))
     {"id" sid}
     ["feature/label" "feature/name" "feature/display-name"
      "feature/category" "feature/status" "feature/source-did"
      "feature/lat" "feature/lon" "feature/height-m" "feature/levels"
      "feature/geometry"])))

(defn query-by-cells
  "The getChunk hot-path (§2), as AVET probes. Returns rows grouped by owning cell:
  { cell { label-keyword [ {id, label, name, lat, lon, ...} ... ] } }."
  ([store cells lod] (query-by-cells store cells lod nil 500))
  ([store cells lod labels] (query-by-cells store cells lod labels 500))
  ([store cells lod labels limit]
   (let [cell-pred (str "feature.cell/r" (long lod))
         label-set (when (seq labels)
                     (set (map (fn [l] (if (str/starts-with? (str l) ":") (str l) (str ":" l)))
                               labels)))]
     (reduce
      (fn [out cell]
        (let [subjects (get-in @store [:avet [cell-pred (str cell)]] #{})
              bucket (reduce
                      (fn [bucket sid]
                        (let [lab (first-val store sid "feature/label")]
                          (if (and label-set (not (contains? label-set lab)))
                            bucket
                            (let [lst (get bucket lab [])]
                              (if (>= (count lst) limit)
                                bucket
                                (assoc bucket lab (conj lst (materialize store sid))))))))
                      (get out cell {})
                      subjects)]
          (assoc out cell bucket)))
      {}
      cells))))

(defn avet-query
  "The wire-level AVET query the TS adapter (kotoba-spatial.ts queryByCells) issues:
  probe AVET(predicate, obj) for each obj, optionally ∩ AVET(filter-pred, v) for v in
  filter-in, and return matching subjects as entity dicts {id, claims:[{pred,value}]}."
  ([store predicate objects] (avet-query store predicate objects nil nil 500))
  ([store predicate objects filter-pred filter-in] (avet-query store predicate objects filter-pred filter-in 500))
  ([store predicate objects filter-pred filter-in limit]
   (let [allow (when (seq filter-in) (set filter-in))]
     (loop [objs objects, seen #{}, out []]
       (if (or (empty? objs) (>= (count out) limit))
         out
         (let [obj (first objs)
               subjects (get-in @store [:avet [predicate (str obj)]] #{})
               [seen out done]
               (reduce
                (fn [[seen out _] sid]
                  (cond
                    (>= (count out) limit) (reduced [seen out true])
                    (contains? seen sid) [seen out false]
                    (and allow (not (contains? allow (first-val store sid filter-pred)))) [seen out false]
                    :else
                    (let [claims (vec (for [[p vs] (get-in @store [:eavt sid]) v vs]
                                        {"pred" p "value" v}))]
                      [(conj seen sid) (conj out {"id" sid "claims" claims}) false])))
                [seen out false]
                subjects)]
           (if done out (recur (rest objs) seen out))))))))

(defn entity
  "self.entity — a subject → an entity dict {id, claims}, or nil if absent."
  [store sid]
  (let [a (get-in @store [:eavt sid])]
    (when a
      {"id" sid "claims" (vec (for [[p vs] a v vs] {"pred" p "value" v}))})))

;; ── introspection (for tests / coverage) ──
(defn feature-count
  "Count subjects that carry a :feature/label (a placed feature, not a transit row)."
  [store]
  (count (filter (fn [[_ a]] (contains? a "feature/label")) (:eavt @store))))

(defn cells-at-res
  "All distinct cell object-ids indexed at resolution res."
  [store res]
  (let [pred (str "feature.cell/r" (long res))]
    (set (for [[[p obj] _] (:avet @store) :when (= p pred)] obj))))
