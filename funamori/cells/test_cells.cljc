(ns funamori.cells.test-cells
  "Kotoba-native cell-spec invariants for funamori R0 cells (babashka).
  R0 cells are declarative EDN Pregel state-graphs over the .cljc methods; .solve() is R1.
  This test pins that every cell spec is well-formed and coherent with the manifest + schema:
  entry∈nodes, edges reference declared nodes, gates non-empty, Murakumo-only LLM,
  :cell/method ∈ manifest methods, and every written datom is declared in the schema."
  (:require [clojure.test :refer [deftest is]]
            [clojure.edn :as edn]))

(def cell-ids ["site_qualification" "membrane_attestation" "power_characterization" "stack_service"])

(defn- resolve-path [rel]
  ;; robust to cwd: actor-dir (run_tests.sh), 20-actors, or repo root
  (->> [rel (str "funamori/" rel) (str "20-actors/funamori/" rel)]
       (filter #(.exists (java.io.File. ^String %)))
       first))

(defn- read-edn [rel]
  (let [p (resolve-path rel)]
    (when-not p (throw (ex-info (str "cannot locate " rel " from cwd") {})))
    (edn/read-string (slurp p))))

(defn- unblob
  "cells/*.edn are now Datomic/Datascript tx-data (edn-datomize wrap, :cell/* namespace
  kept as-is since it was already meaningfully namespaced); non-scalar values
  (:cell/state-graph, :cell/llm, :cell/kotoba) are pr-str blob strings. Parse back to the
  original nested value where possible."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "tx-data [{:db/id -1 :cell/id \"...\" :cell/state-graph \"...blob...\" ...}] -> the original
  bare {:cell/id \"...\" :cell/state-graph {...} ...} map (keys keep their :cell/* namespace
  unchanged -- it was never re-prefixed) so the (:cell/* c) lookups below are unchanged."
  [tx-data]
  (into {} (map (fn [[k v]] [k (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(def cells (into {} (map (fn [id] [id (reconstitute-entity (read-edn (str "cells/" id ".edn")))]) cell-ids)))
(def manifest (read-edn "manifest.edn"))
(def schema (read-edn "kotoba/schema.edn"))
(def schema-idents (set (map :db/ident schema)))
(def manifest-method-ids (set (map :method/id (:actor/methods manifest))))
(def manifest-cell-ids (set (map :cell/id (:actor/cells manifest))))

(deftest test-all-cells-present-and-parse
  (is (= (set cell-ids) (set (keys cells))))
  (is (= (set cell-ids) manifest-cell-ids)))

(deftest test-required-keys
  (doseq [[id c] cells]
    (is (= id (:cell/id c)) (str id " :cell/id"))
    (is (string? (:cell/glyph c)) (str id " glyph"))
    (is (= :wasm (:cell/runtime c)) (str id " runtime"))
    (is (map? (:cell/state-graph c)) (str id " state-graph"))
    (is (map? (:cell/kotoba c)) (str id " kotoba"))))

(deftest test-state-graph-well-formed
  (doseq [[id c] cells]
    (let [{:keys [nodes entry edges]} (:cell/state-graph c)
          node-set (set nodes)
          valid (conj node-set :end)]
      (is (contains? node-set entry) (str id ": entry " entry " ∈ nodes"))
      (doseq [[a b] edges]
        (is (contains? node-set a) (str id ": edge src " a " declared"))
        (is (contains? valid b) (str id ": edge dst " b " declared (or :end)")))
      ;; reachability: a path from entry eventually reaches :end
      (is (some (fn [[_ b]] (= :end b)) edges) (str id ": graph reaches :end")))))

(deftest test-gates-nonempty
  (doseq [[id c] cells]
    (is (seq (:cell/gates c)) (str id ": gates non-empty"))
    (is (every? string? (:cell/gates c)) (str id ": gates are strings"))))

(deftest test-murakumo-only-llm
  (doseq [[id c] cells]
    (let [llm (:cell/llm c)]
      (is (= :murakumo (:provider llm)) (str id ": Murakumo-only (ADR-2605215000)"))
      (is (= "127.0.0.1:4000" (:endpoint llm)) (str id ": LiteLLM loopback"))
      (is (true? (:charter-rider llm)) (str id ": charter-rider")))))

(deftest test-method-references-exist
  (doseq [[id c] cells]
    (is (contains? manifest-method-ids (:cell/method c))
        (str id ": :cell/method " (:cell/method c) " ∈ manifest methods"))))

(deftest test-written-datoms-declared-in-schema
  (doseq [[id c] cells]
    (doseq [w (get-in c [:cell/kotoba :writes])]
      (is (contains? schema-idents w)
          (str id ": written datom " w " declared in kotoba/schema.edn")))
    (doseq [r (get-in c [:cell/kotoba :reads])]
      (is (contains? schema-idents r)
          (str id ": read datom " r " declared in kotoba/schema.edn")))))
