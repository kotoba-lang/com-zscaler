#!/usr/bin/env bb
;; 20-actors/makura/edn-datomize.bb — EDN → Datomic/Datascript tx-data 変換ツール。
;; Ported/adapted from manifest/edn-datomize.bb (com-junkawasaki/root) and the
;; com-etzhayyim-futawa / com-etzhayyim-mitsuho copies used for the Phase 3
;; sibling-repo fanout. schema.edn accumulates at 20-actors/makura/schema.edn
;; (there is no manifest/ dir at this level of etzhayyim/root).

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def actor-dir (.getParentFile (io/file *file*)))

(defn schema-path [] (io/file actor-dir "schema.edn"))

(defn slurp-edn [f] (edn/read-string (slurp f)))

(defn already-tx-data?
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn classify
  [v]
  (cond
    (string? v)  {:type :db.type/string  :card :db.cardinality/one}
    (boolean? v) {:type :db.type/boolean :card :db.cardinality/one}
    (integer? v) {:type :db.type/long    :card :db.cardinality/one}
    (double? v)  {:type :db.type/double  :card :db.cardinality/one}
    (keyword? v) {:type :db.type/keyword :card :db.cardinality/one}
    (nil? v)     {:type :db.type/string  :card :db.cardinality/one}
    (and (coll? v) (empty? v))
    {:type :db.type/string :card :db.cardinality/many}
    (and (coll? v) (every? string? v))  {:type :db.type/string  :card :db.cardinality/many}
    (and (coll? v) (every? keyword? v)) {:type :db.type/keyword :card :db.cardinality/many}
    (and (coll? v) (every? integer? v)) {:type :db.type/long    :card :db.cardinality/many}
    :else {:type :db.type/string :card :db.cardinality/one :blob true}))

(defn attr-value [v]
  (let [{:keys [blob]} (classify v)]
    (if blob (pr-str v) v)))

(defn keep-ns-key [ns-name k]
  (if (namespace k) k (keyword ns-name (name k))))

(defn load-schema []
  (let [f (schema-path)]
    (if (.exists f) (slurp-edn f) [])))

(defn merge-schema! [new-attrs]
  (let [existing (load-schema)
        by-ident (into {} (map (juxt :db/ident identity)) existing)
        merged-by-ident (reduce (fn [acc {:keys [db/ident] :as attr}]
                                   (if (contains? acc ident) acc (assoc acc ident attr)))
                                 by-ident
                                 new-attrs)
        merged (vec (sort-by (comp str :db/ident) (vals merged-by-ident)))]
    (spit (schema-path) (str ";; schema.edn — Datomic/Datascript 互換スキーマ定義（自動生成 by edn-datomize.bb）\n"
                              ";; :db/ident 属性定義のリスト。Datomic 固有キー(:db.install/_attribute 等)は使わない。\n"
                              ";; 手編集禁止 — 再生成すると上書きされる。\n\n"
                              (pr-str merged)
                              "\n"))
    merged))

;; ---------- single-map file, keep existing namespaces (cells/*.edn) ----------

(defn wrap-map-keep-ns! [rel-path ns-name]
  (let [f (io/file actor-dir rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (into {:db/id -1}
                          (map (fn [[k v]] [(keep-ns-key ns-name k) (attr-value v)]))
                          content)
            attrs (for [[k v] content]
                    (let [{:keys [type card]} (classify v)]
                      {:db/ident (keep-ns-key ns-name k) :db/valueType type :db/cardinality card}))]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped(keep-ns)" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

;; ---------- single-map file, force namespace on ALL bare keys (lex/*.edn) ----------

(defn wrap-map-force-ns! [rel-path ns-name]
  (let [f (io/file actor-dir rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (into {:db/id -1}
                          (map (fn [[k v]] [(keyword ns-name (name k)) (attr-value v)]))
                          content)
            attrs (for [[k v] content]
                    (let [{:keys [type card]} (classify v)]
                      {:db/ident (keyword ns-name (name k)) :db/valueType type :db/cardinality card}))]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped(force-ns)" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

;; ---------- multi-entity vector file, keep existing namespaces per-entity,
;;            promote bare keys with ns-name (kotoba/seed.edn, products.edn) ----------

(defn wrap-vector-keep-ns! [rel-path ns-name]
  (let [f (io/file actor-dir rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (if-not (and (vector? content) (every? map? content))
        (println "skip (not a vector-of-maps):" rel-path)
        (let [tx (vec (map-indexed
                       (fn [i m]
                         (into {:db/id (- -1 i)}
                               (map (fn [[k v]] [(keep-ns-key ns-name k) (attr-value v)]))
                               m))
                       content))
              attrs (mapcat (fn [m]
                              (for [[k v] m]
                                (let [{:keys [type card]} (classify v)]
                                  {:db/ident (keep-ns-key ns-name k) :db/valueType type :db/cardinality card})))
                            content)]
          (spit f (pr-str tx))
          (merge-schema! attrs)
          (println "wrapped(vector-keep-ns)" rel-path "->" (count tx) "entities, ns=" ns-name))))))

(defn -main [& args]
  (let [[mode a b] args]
    (case mode
      "wrap-map-keep-ns"    (wrap-map-keep-ns! a b)
      "wrap-map-force-ns"   (wrap-map-force-ns! a b)
      "wrap-vector-keep-ns" (wrap-vector-keep-ns! a b)
      (do (println "usage: bb edn-datomize.bb [wrap-map-keep-ns <path> <ns> | wrap-map-force-ns <path> <ns> | wrap-vector-keep-ns <path> <ns>]")
          (System/exit 1)))))

(apply -main *command-line-args*)
