(ns maps.tests.kotoba-local
  "In-memory EAVT/AVET reference store for maps tests (ADR-2606064500 §2).
  Clojure port of `methods/kotoba_local.py`. stdlib only.

  Implements the same AVET probe contract the production kotoba engine serves.
  NOT the production engine — a test harness for offline verification.

  The query-fn produced by `make-query-fn` has the same signature as all
  maps method query-fns: (fn [pred objects limit] → entity-seq)."
  (:require [clojure.string :as str]))

(defn make-store
  "Return a fresh in-memory store atom: {:eavt {sid {pred [val]}} :avet {[pred val] #{sids}}}."
  []
  (atom {:eavt {} :avet {}}))

(defn ingest-batch!
  "Ingest a kg.ingest_batch body into the store atom.
  batch = {\"entities\" [{\"id\" id \"claims\" [{\"pred\" p \"value\" v}]}]}"
  [store batch]
  (let [n (volatile! 0)]
    (doseq [ent (get batch "entities" [])]
      (let [sid (get ent "id")]
        (when sid
          (vswap! n inc)
          (doseq [c (get ent "claims" [])]
            (let [p (get c "pred") v (get c "value")]
              (when (and p (some? v))
                (swap! store
                       (fn [s]
                         (-> s
                             (update-in [:eavt sid p] (fnil conj []) v)
                             (update-in [:avet [p (str v)]] (fnil conj #{}) sid))))))))))
    @n))

(defn- first-val [store sid pred]
  (first (get-in store [:eavt sid pred])))

(defn- materialize [store sid]
  (let [a (get-in store [:eavt sid] {})
        row (cond-> {"id" sid}
              (seq (get a "feature/label"))    (assoc "label"   (first (get a "feature/label")))
              (seq (get a "feature/name"))     (assoc "name"    (first (get a "feature/name")))
              (seq (get a "feature/lat"))      (assoc "lat"     (Double/parseDouble (first (get a "feature/lat"))))
              (seq (get a "feature/lon"))      (assoc "lon"     (Double/parseDouble (first (get a "feature/lon")))))
        claims (into []
                     (for [[pred vals] a val vals]
                       {"pred" pred "value" val}))]
    (assoc row "claims" claims)))

(defn avet
  "AVET probe: all entities that have pred=value for any value in `objects`.
  Returns entity dicts {\"id\" id \"claims\" [{\"pred\" p \"value\" v}]}."
  [store-atom pred objects limit]
  (let [s @store-atom
        sids (into #{} (mapcat #(get-in s [:avet [pred (str %)]] #{}) objects))]
    (take limit (map #(materialize s %) sids))))

(defn make-query-fn
  "Returns a query-fn (fn [pred objects limit] → entity-seq) backed by this store."
  [store-atom]
  (fn [pred objects limit]
    (avet store-atom pred objects limit)))

(defn build-store-from-features
  "Build an in-memory store from a :feature/* map (keyed by :feature/id).
  Converts \":feature/id\" string-keyed maps into the kg.ingest_batch entity shape."
  [features]
  (let [store (make-store)
        batch {"entities"
               (mapv (fn [[fid f]]
                       {"id"     fid
                        "type"   "maps-feature"
                        "claims" (into [] (keep (fn [[k v]]
                                                  (when (and k v (not= k ":feature/id"))
                                                    {"pred" (subs (str k) 1) "value" (str v)}))
                                                f))})
                     features)}]
    (ingest-batch! store batch)
    store))
