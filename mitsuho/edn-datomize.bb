#!/usr/bin/env bb
;; edn-datomize.bb — EDN → Datomic/Datascript tx-data 変換ツール（mitsuho actor local, Phase 4）。
;; com-junkawasaki/root superproject の manifest/edn-datomize.bb / com-etzhayyim-mitsuho の
;; 移植版を 20-actors/mitsuho 用にさらに移植（schema-path をこの actor 配下に調整 +
;; wrap-vec-add-id モードを追加。Phase 4 EDN-datomize fanout、2026-07-10）。
;;
;; 「datomic/datascript query 可能」の定義: ファイルのトップレベルが
;; (d/transact conn (edn/read-string (slurp file))) にそのまま渡せる
;; tx-data ベクタ（entity-map のベクタ、各 map は :db/id を持つ）であること。
;;
;; マップ1個のファイルは [{...:db/id -1}] に包み、既存キーはファイル種別ごとの
;; 名前空間を付けた属性名にリネームする（wrap-map。裸キー用）。既に慣用の
;; 名前空間付きキーを持つファイルは wrap-map-keep-ns で既存名前空間を保持する。
;; 既に「名前空間付きキーの entity-map のベクタ」だが各 map に :db/id が無い
;; ファイル（kotoba/seed.edn, products.edn 相当）は wrap-vec-add-id で
;; 各 entity に一意な負の tempid を振る（キー名は一切変更しない）。
;; 値が Datomic の scalar valueType（string/long/double/boolean/keyword、
;; またはそれらの集合）に収まらないもの（入れ子 map、map を含む vector 等）は
;; pr-str した文字列として保持する。属性定義はこの actor 配下の schema.edn に
;; 自動登録する（kotoba/schema.edn は別物の手書きスキーマなので触らない）。
;;
;; 使い方:
;;   bb edn-datomize.bb wrap-map          <path> <ns>  — 裸キーの map 1個のファイルを変換
;;   bb edn-datomize.bb wrap-map-keep-ns  <path> <ns>  — 名前空間付きキーの map を変換（既存 ns 保持）
;;   bb edn-datomize.bb wrap-vec-add-id   <path>        — 名前空間付きキーの entity-map ベクタに :db/id を付与

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str])

(def root (str/trim (:out (shell/sh "git" "rev-parse" "--show-toplevel"))))

;; this actor's local accumulator — kotoba/schema.edn is a separate, hand-curated
;; Datomic schema file (with :db/doc / :db/unique) and is NEVER touched by this script.
(defn schema-path [] (io/file root "20-actors" "mitsuho" "schema.edn"))

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

(defn keep-or-namespace-key [ns-name k]
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
    (spit (schema-path) (str ";; 20-actors/mitsuho/schema.edn — Datomic/Datascript 互換スキーマ定義\n"
                              ";; (自動生成 by edn-datomize.bb, Phase 4 fanout, 2026-07-10).\n"
                              ";; :db/ident 属性定義のリスト。Datomic 固有キー(:db.install/_attribute 等)は使わない。\n"
                              ";; kotoba/schema.edn とは別物（あちらは :db/doc/:db/unique 付きの手書き\n"
                              ";; Datomic スキーマで、このツールは一切触らない）。手編集禁止 — 再生成すると\n"
                              ";; 上書きされる。\n"
                              ";;\n"
                              ";; 対象: cells/*.edn (wrap-map-keep-ns, 既存 :cell/* namespace 保持) +\n"
                              ";; lex/*.edn (wrap-map, ファイル単位の lex.<lexiconName> namespace 新規付与)。\n"
                              ";; kotoba/seed.edn / products.edn は wrap-vec-add-id で :db/id のみ付与\n"
                              ";; （属性は kotoba/schema.edn に既存定義済みのため新規属性なし）。\n"
                              ";; manifest.edn は 5 個の共有 cross-cutting tool 側の bare-map 前提read-siteが\n"
                              ";; 未パッチのため意図的に対象外（別の shared-infra patch 待ち）。\n\n"
                              (pr-str merged)
                              "\n"))
    merged))

(defn wrap-map! [rel-path ns-name]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (into {:db/id -1} (map (fn [[k v]] [(namespaced-key ns-name k) (attr-value v)])) content)
            attrs (for [[k v] content]
                    (let [{:keys [type card]} (classify v)]
                      {:db/ident (namespaced-key ns-name k) :db/valueType type :db/cardinality card}))]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

(defn wrap-map-keep-ns! [rel-path ns-name]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (into {:db/id -1} (map (fn [[k v]] [(keep-or-namespace-key ns-name k) (attr-value v)])) content)
            attrs (for [[k v] content]
                    (let [{:keys [type card]} (classify v)]
                      {:db/ident (keep-or-namespace-key ns-name k) :db/valueType type :db/cardinality card}))]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped(preserve-ns)" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

;; ---------- vector-of-already-namespaced-entity-maps, missing :db/id ----------
;; kotoba/seed.edn and products.edn are already vectors of entity-maps with
;; meaningfully-namespaced keys (:parcel/id, :product/id, ...) matching a real
;; schema (kotoba/schema.edn / shared :product/* vocabulary) — they just lack a
;; :db/id per entity. Add a unique negative tempid to each map; leave every key
;; and value untouched except blob-ifying genuinely non-scalar values (none
;; observed in mitsuho's seed.edn / products.edn as of this pass).
(defn wrap-vec-add-id! [rel-path]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (cond
      (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)

      (not (and (vector? content) (seq content) (every? map? content)))
      (println "skip (not a vector-of-maps):" rel-path)

      :else
      (let [entities (map-indexed
                       (fn [i m]
                         (into {:db/id (- (inc i))}
                               (map (fn [[k v]] [k (attr-value v)]))
                               m))
                       content)
            attrs (for [m content [k v] m]
                    (let [{:keys [type card]} (classify v)]
                      {:db/ident k :db/valueType type :db/cardinality card}))]
        (spit f (pr-str (vec entities)))
        (merge-schema! attrs)
        (println "wrapped(vec-add-id)" rel-path "->" (count entities) "entities")))))

(defn -main [& args]
  (let [[mode a b] args]
    (case mode
      "wrap-map" (wrap-map! a b)
      "wrap-map-keep-ns" (wrap-map-keep-ns! a b)
      "wrap-vec-add-id" (wrap-vec-add-id! a)
      (do (println "usage: bb edn-datomize.bb [wrap-map <path> <ns> | wrap-map-keep-ns <path> <ns> | wrap-vec-add-id <path>]")
          (System/exit 1)))))

(apply -main *command-line-args*)
