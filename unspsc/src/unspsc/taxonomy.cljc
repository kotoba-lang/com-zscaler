(ns unspsc.taxonomy
  "The per-code UNSPSC taxonomy data table — the DATA half of the data-driven
  actor model. Portable across JVM, ClojureScript (browser) and SCI:

    - JVM   : `ensure-loaded!` reads resources/unspsc-taxonomy.edn off the classpath.
    - Browser/cljs : the host fetches the EDN and injects it via `set-table!`
                     (clojure.core/format and java.io are avoided here).

  On disk, resources/unspsc-taxonomy.edn is stored as Datomic/Datascript
  tx-data (a vector of one entity per UNSPSC code, `:unspsc/*`-namespaced
  attrs, `:db/id` per-entity tempid) per the repo-wide 'datomic/datascript
  queryable' convention — but every in-process consumer of THIS namespace
  (and of `unspsc.build-taxonomy` / `unspsc.enrich`, which round-trip the
  same file) still works against the plain `{code -> {:code :title ...}}`
  shape. `tx-data->table` / `table->tx-data` are the ONLY place that shape
  conversion happens; `normalize-loaded` makes the JVM load path tolerant of
  both the old bare-map shape and the new tx-data shape (idempotent)."
  #?(:clj (:require [clojure.edn :as edn]
                    [clojure.java.io :as io])))

(defonce ^:private table* (atom nil))

(defn set-table!
  "Injects the code->taxon map (the browser/cljs path: host fetches the EDN)."
  [m]
  (reset! table* m))

(defn loaded? [] (some? @table*))

#?(:clj
   (do
     (defn tx-data?
       "True if content is already [{...:db/id ...} ...] tx-data shape."
       [content]
       (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

     (defn- unblob [v]
       (if (string? v)
         (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
              (catch Exception _ v))
         v))

     (defn entity->taxon
       "Reconstitutes one tx-data entity back into a bare-keyed taxon map
       (strips the :unspsc/ namespace, drops :db/id, unblobs pr-str'd values)."
       [entity]
       (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
             (dissoc entity :db/id)))

     (defn tx-data->table
       "[{...} ...] tx-data -> {code -> taxon}."
       [tx-data]
       (into {} (map (fn [e] (let [t (entity->taxon e)] [(:code t) t]))) tx-data))

     (defn taxon->entity
       "One {code taxon} pair -> a :db/id + :unspsc/*-namespaced tx-data entity."
       [idx taxon]
       (into {:db/id (- (inc idx))}
             (map (fn [[k v]] [(keyword "unspsc" (name k)) v]))
             taxon))

     (defn table->tx-data
       "{code -> taxon} -> tx-data vector, sorted by code for deterministic diffs."
       [table]
       (vec (map-indexed (fn [idx [_code taxon]] (taxon->entity idx taxon))
                          (sort-by first table))))

     (defn normalize-loaded
       "Accepts either shape read off disk and returns the plain code->taxon map."
       [content]
       (if (tx-data? content) (tx-data->table content) content))

     (defn load-from-resource!
       "JVM: load the taxonomy from the classpath resource."
       []
       (if-let [r (io/resource "unspsc-taxonomy.edn")]
         (with-open [rdr (io/reader r)]
           (set-table! (normalize-loaded (edn/read (java.io.PushbackReader. rdr)))))
         (throw (ex-info "taxonomy resource not found — run `clojure -M:build-taxonomy`"
                         {:resource "unspsc-taxonomy.edn"}))))))

(defn ensure-loaded! []
  (when-not (loaded?)
    #?(:clj  (load-from-resource!)
       :cljs (throw (ex-info "taxonomy not loaded — call set-table! with the fetched EDN" {})))))

(defn table [] (ensure-loaded!) @table*)

(defn taxon [code] (get (table) code))

(defn codes [] (keys (table)))

(defn codes-in-segment [segment]
  (->> (table) vals (filter #(= segment (:segment %))) (map :code) sort))

(defn count-codes [] (count (table)))
