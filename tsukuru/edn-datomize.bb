#!/usr/bin/env bb
;; edn-datomize.bb — EDN → Datomic/Datascript tx-data 変換ツール (Phase 4, 20-actors/tsukuru).
;; Adapted from orgs/etzhayyim/com-etzhayyim-tsukuru/edn-datomize.bb (itself adapted from the
;; superproject manifest/edn-datomize.bb, Phase 1). Adds wrap-seq! for files whose top-level
;; content is ALREADY a vector of bare-keyed maps (e.g. lex/cnt.edn, lex/euv.edn) — each
;; element becomes its own entity (sequential :db/id tempids), same namespace-preserving /
;; blob rules as wrap-map!.
;;
;; 「datomic/datascript query 可能」の定義: ファイルのトップレベルが
;; (d/transact conn (edn/read-string (slurp file))) にそのまま渡せる
;; tx-data ベクタ（entity-map のベクタ、各 map は :db/id を持つ）であること。
;;
;; 使い方:
;;   bb edn-datomize.bb wrap-map <path> <ns>   — map 1個のファイルを変換
;;   bb edn-datomize.bb wrap-seq <path> <ns>   — vector-of-bare-keyed-maps のファイルを変換
;;     (top-level が既にベクタで、各要素が bare-key の map。各要素に :db/id を振り、
;;      namespaced-key/attr-value を要素ごとに適用する)

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str])

(def root (str/trim (:out (shell/sh "git" "rev-parse" "--show-toplevel"))))
;; This worktree is sparse-checked-out to just 20-actors/tsukuru/*, and IS the actor dir root
;; for schema.edn placement purposes (per the coordinator's convention: schema.edn accumulates
;; at 20-actors/tsukuru/schema.edn, since there's no repo-root manifest/ dir convention here).
(def actor-dir (io/file root "20-actors" "tsukuru"))

(defn schema-path [] (io/file actor-dir "schema.edn"))

(defn slurp-edn [path] (edn/read-string (slurp path)))

(defn already-tx-data?
  "既に [{...:db/id ...} ...] 形式に変換済みか判定（再実行の冪等性用）。"
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn classify
  "値から Datomic :db/valueType + :db/cardinality を推定する。scalar に収まらない
   値（入れ子 map / map を含む vector 等）は :blob true を返す(pr-str して string 化)。"
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

(defn ->key-kw
  "Coerce an original top-level key (keyword OR string — some tsukuru kotoba/*.edn files use
   plain string keys to mirror agent.cljc's plain-map idiom) to a keyword for namespacing."
  [k]
  (cond (keyword? k) k
        (string? k)  (keyword k)
        :else (keyword (str k))))

(defn namespaced-key
  "既に名前空間付き(idiomatic Clojure style: :actor/id, :cell/id 等)のキーは
   そのまま保持し(re-prefix しない)、bare キーだけ ns-name で名前空間を付ける
   (namespace-preserving mode)。string キーは常に bare 扱いで namespace を付ける。"
  [ns-name k]
  (let [kw (->key-kw k)]
    (if (namespace kw) kw (keyword ns-name (name kw)))))

(defn entity-from-map
  "トップレベル map の各キーに ns-name の名前空間を付け、:db/id を足した 1 entity にする。"
  [content ns-name db-id]
  (into {:db/id db-id}
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
                              ";; kotoba/schema.edn (このアクターの手書き domain schema: :factory/* /\n"
                              ";; :production-order/* 等)とは別物 — こちらは tx-data 化した cells/*.edn /\n"
                              ";; lex/*.edn / kotoba/{cnt,euv}-process-catalog.edn の属性定義のみを集約する\n"
                              ";; (kotoba/schema.edn とは ident が重複しないよう既存キーの namespace を尊重\n"
                              ";; して生成している)。\n\n"
                              (pr-str merged)
                              "\n"))
    merged))

(defn wrap-map! [rel-path ns-name]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (if (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)
      (let [entity (entity-from-map content ns-name -1)
            attrs (schema-attrs content ns-name)]
        (spit f (pr-str [entity]))
        (merge-schema! attrs)
        (println "wrapped" rel-path "->" (count entity) "attrs, ns=" ns-name)))))

(defn wrap-seq! [rel-path ns-name]
  (let [f (io/file root rel-path)
        content (slurp-edn f)]
    (cond
      (already-tx-data? content)
      (println "skip (already tx-data):" rel-path)

      (not (and (vector? content) (every? map? content)))
      (do (println "SKIP (not a vector-of-maps):" rel-path)
          (System/exit 1))

      :else
      (let [entities (map-indexed (fn [i m] (entity-from-map m ns-name (- -1 i))) content)
            attrs (mapcat #(schema-attrs % ns-name) content)]
        (spit f (pr-str (vec entities)))
        (merge-schema! attrs)
        (println "wrapped(seq)" rel-path "->" (count entities) "entities, ns=" ns-name)))))

(defn -main [& args]
  (let [[mode a b] args]
    (case mode
      "wrap-map" (wrap-map! a b)
      "wrap-seq" (wrap-seq! a b)
      (do (println "usage: bb edn-datomize.bb wrap-map|wrap-seq <path> <ns>")
          (System/exit 1)))))

(apply -main *command-line-args*)
