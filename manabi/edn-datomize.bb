#!/usr/bin/env bb
;; 20-actors/manabi/edn-datomize.bb — EDN → Datomic/Datascript tx-data 変換ツール。
;;
;; 「datomic/datascript query 可能」の定義: ファイルのトップレベルが
;; (d/transact conn (edn/read-string (slurp file))) にそのまま渡せる
;; tx-data ベクタ（entity-map のベクタ、各 map は :db/id を持つ）であること。
;;
;; マップ1個のファイルは [{...:db/id -1}] に包み、既存キーはファイル種別ごとの
;; 名前空間を付けた属性名にリネームする。値が Datomic の scalar valueType
;; （string/long/double/boolean/keyword、またはそれらの集合）に収まらないもの
;; （入れ子 map、map を含む vector 等）は pr-str した文字列として保持する
;; （valueType=string の "blob" 属性にする）。属性定義はこのディレクトリの
;; schema.edn に自動登録する（Datomic/Datascript 両対応、:db.install/_attribute
;; 等の Datomic 固有キーは使わない）。
;;
;; 使い方:
;;   bb 20-actors/manabi/edn-datomize.bb wrap-map <path> <ns>          — map 1個のファイルを変換
;;                                                       (トップレベル全キーに ns を強制)
;;   bb 20-actors/manabi/edn-datomize.bb wrap-map-keep-ns <path> <ns>  — map 1個のファイルを変換
;;                                                       (既に名前空間付きのキーはそのまま保持し、
;;                                                        裸キーだけ ns を付与)
;;
;; このコピーは 20-actors/manabi 用に schema-path のみ調整（20-actors/manabi/schema.edn）。
;; 他のロジックは manifest/edn-datomize.cljs / etzhayyim/com-etzhayyim-manabi の
;; edn-datomize.bb (keep-ns 拡張版) と同一。Phase 4 fan-out (etzhayyim/root
;; 20-actors/<actor> 単位)。

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str])

(def root (str/trim (:out (shell/sh "git" "rev-parse" "--show-toplevel"))))

(defn schema-path [] (io/file root "20-actors" "manabi" "schema.edn"))

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
    (spit (schema-path) (str ";; 20-actors/manabi/schema.edn — Datomic/Datascript 互換スキーマ定義（自動生成 by edn-datomize.bb）\n"
                              ";; :db/ident 属性定義のリスト。Datomic 固有キー(:db.install/_attribute 等)は使わない。\n"
                              ";; 手編集禁止 — 再生成すると上書きされる。\n"
                              ";;\n"
                              ";; wrap-map-keep-ns で変換したファイルは、元々 Clojure 慣用の名前空間付き\n"
                              ";; キーワード(:cell/id 等)をそのまま保持し、裸キーだけファイル(ディレクトリ)\n"
                              ";; 単位の namespace を付与している。値はすべて scalar はそのまま、非scalar(入れ子\n"
                              ";; map/vector-of-map)は pr-str の \"blob\" (valueType=string) として保持。\n"
                              ";;\n"
                              ";; 対象外（この pass では意図的に未変換）: manifest.edn — 5 shared cross-cutting\n"
                              ";; tools (70-tools/src/etzhayyim/: gen_tier_b_actors.clj, vitals.cljc,\n"
                              ";; actor_publish.cljc, aozora_deploy.cljc, ownership_matrix.cljc) が bare-map\n"
                              ";; 前提で :actor/tier :actor/id 等を直接 keyword lookup している。共有パッチが\n"
                              ";; landing するまで manifest.edn は据え置き。\n"
                              ";; 対象外（既に vector-of-entity-map で query 可能と判断、no work needed）:\n"
                              ";; kotoba/schema.edn（Datomic schema 定義そのもの）, kotoba/seed.edn（既に\n"
                              ";; :module/id :learner/did 等の名前空間付きキーを持つ複数 entity map の\n"
                              ";; ベクタ）。\n\n"
                              (pr-str merged)
                              "\n"))
    merged))

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
      "wrap-map-keep-ns" (wrap-map-keep-ns! a b)
      (do (println "usage: bb 20-actors/manabi/edn-datomize.bb [wrap-map-keep-ns <path> <ns>]")
          (System/exit 1)))))

(apply -main *command-line-args*)
