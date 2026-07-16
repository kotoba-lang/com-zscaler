#!/usr/bin/env bb
;; 20-actors/tatekata/edn-datomize.bb — EDN → Datomic/Datascript tx-data 変換ツール。
;; Ported from manifest/edn-datomize.bb (com-junkawasaki/root) / etzhayyim/com-etzhayyim-tatekata
;; copy, adjusted so schema-path is this directory's schema.edn (no manifest/ dir convention
;; in etzhayyim/root's 20-actors/<actor>/ layout).
;;
;; See com-junkawasaki/root manifest/CLAUDE.md for the design doc. Logic identical to the
;; sibling repo's copy (wrap-map / wrap-map-keep-ns / classify / merge-schema!).

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def root (-> (io/file *file*) .getParentFile .getAbsolutePath))

(defn schema-path [] (io/file root "schema.edn"))

(defn slurp-edn [path] (edn/read-string (slurp path)))

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

(defn namespaced-key [ns-name k]
  (keyword ns-name (name k)))

(defn entity-from-map
  [content ns-name]
  (into {:db/id -1}
        (map (fn [[k v]] [(namespaced-key ns-name k) (attr-value v)]))
        content))

(defn schema-attrs
  [content ns-name]
  (for [[k v] content]
    (let [{:keys [type card]} (classify v)]
      {:db/ident (namespaced-key ns-name k)
       :db/valueType type
       :db/cardinality card})))

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
                              ";; 手編集禁止 — 再生成すると上書きされる。\n"
                              ";;\n"
                              ";; wrap-map-keep-ns で変換したファイルは、元々 Clojure 慣用の名前空間付き\n"
                              ";; キーワード(:cell/id 等)をそのまま保持し、裸キーだけファイル(ディレクトリ)単位の\n"
                              ";; namespace を付与している。値はすべて scalar はそのまま、非scalar(入れ子 map/\n"
                              ";; vector-of-map)は pr-str の \"blob\"(valueType=string)として保持している。\n\n"
                              (pr-str merged)
                              "\n"))
    merged))

(defn wrap-map! [rel-path ns-name]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (entity-from-map content ns-name)
            attrs (schema-attrs content ns-name)]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

(defn keep-ns-key [ns-name k]
  (if (namespace k) k (keyword ns-name (name k))))

(defn transform-keep-ns [content ns-name]
  (into {:db/id -1}
        (map (fn [[k v]] [(keep-ns-key ns-name k) (attr-value v)]))
        content))

(defn schema-attrs-keep-ns [content ns-name]
  (for [[k v] content]
    (let [{:keys [type card]} (classify v)]
      {:db/ident (keep-ns-key ns-name k)
       :db/valueType type
       :db/cardinality card})))

(defn wrap-map-keep-ns! [rel-path ns-name]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (transform-keep-ns content ns-name)
            attrs (schema-attrs-keep-ns content ns-name)]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped(keep-ns)" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

(defn -main [& args]
  (let [[mode a b] args]
    (case mode
      "wrap-map"         (wrap-map! a b)
      "wrap-map-keep-ns" (wrap-map-keep-ns! a b)
      (do (println "usage: bb edn-datomize.bb [wrap-map <path> <ns> | wrap-map-keep-ns <path> <ns>]")
          (System/exit 1)))))

(apply -main *command-line-args*)
