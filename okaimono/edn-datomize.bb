#!/usr/bin/env bb
;; 20-actors/okaimono/edn-datomize.bb — EDN → Datomic/Datascript tx-data 変換ツール。
;;
;; 「datomic/datascript query 可能」の定義: ファイルのトップレベルが
;; (d/transact conn (edn/read-string (slurp file))) にそのまま渡せる
;; tx-data ベクタ（entity-map のベクタ、各 map は :db/id を持つ）であること。
;;
;; マップ1個のファイルは [{...:db/id -1}] に包み、既存キーはファイル種別ごとの
;; 名前空間を付けた属性名にリネームする。値が Datomic の scalar valueType
;; （string/long/double/boolean/keyword、またはそれらの集合）に収まらないもの
;; （入れ子 map、map を含む vector 等）は pr-str した文字列として保持する。
;; 属性定義は 20-actors/okaimono/schema.edn に自動登録する（Datomic/Datascript
;; 両対応、:db.install/_attribute 等の Datomic 固有キーは使わない）。
;;
;; このコピーは 20-actors/okaimono 用に schema-path のみ調整（この actor の
;; kotoba/schema.edn は別物 — actor 自身の EAVT スキーマであり、この tool の
;; 出力先ではない。混同を避けるため actor ディレクトリ直下 20-actors/okaimono/schema.edn
;; に出力する）。他のロジックは manifest/edn-datomize.bb / 各 sibling repo の
;; edn-datomize.bb と同一。
;;
;; 使い方（このリポジトリ内、worktree ルートから相対パスで呼ぶ）:
;;   bb 20-actors/okaimono/edn-datomize.bb wrap-map <path> <ns>
;;   bb 20-actors/okaimono/edn-datomize.bb wrap-map-keep-ns <path> <ns>

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def root (System/getProperty "user.dir"))

(defn schema-path [] (io/file root "20-actors/okaimono/schema.edn"))

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
    (spit (schema-path) (str ";; 20-actors/okaimono/schema.edn — Datomic/Datascript 互換スキーマ定義\n"
                              ";; (自動生成 by edn-datomize.bb, Phase 4 fan-out).\n"
                              ";; :db/ident 属性定義のリスト。Datomic 固有キー(:db.install/_attribute 等)は使わない。\n"
                              ";; 手編集禁止 — 再生成すると上書きされる。\n"
                              ";; 注意: 20-actors/okaimono/kotoba/schema.edn は別物 (actor 自身の kotoba EAVT\n"
                              ";; スキーマ — 未変更、対象外)。混同しないこと。\n\n"
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

;; ---------- keep-ns variant (top-level keys already idiomatically namespaced) ----------

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
