#!/usr/bin/env bb
;; 撓 tawami — seed loader + classifier (clj-native, pure stdlib).
(ns tawami.methods.tawami-edn
  "撓 tawami — load + classify the flexibility-asset seed substrate.
  Sibling of the mio/busshi *_edn loaders. Energy Order Protocol."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn- tx-data-rows?
  "True when the parsed content is a Datomic/Datascript tx-data vector
  (each row a :db/id-bearing entity map), as produced by the EDN-datomize
  transform (manifest/edn-datomize.cljs pattern). Bare seed vectors (rows
  are plain, un-namespaced asset maps) are left as-is."
  [rows]
  (and (vector? rows) (seq rows) (every? #(and (map? %) (contains? % :db/id)) rows)))

(defn- unblob
  "Reverse attr-value's pr-str blobbing of non-scalar values (nested maps /
  vectors-of-maps) back into live data. Scalars and homogeneous collections
  of scalars pass through unchanged."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-row
  "Reconstitute one tx-data entity back into the original bare-keyed asset
  map (strip :db/id, strip the :tawami.asset/* namespace, unblob), so
  downstream key lookups (:type :id :resource-class ...) keep working
  unchanged whether seed.edn is stored bare or as tx-data."
  [row]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc row :db/id)))

(defn parse-edn [text]
  (let [rows (edn/read-string text)]
    (if (tx-data-rows? rows) (mapv reconstitute-row rows) rows)))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)] (parse-edn (slurp r)))))

(defn classify
  "Split the flat seed vector by :type. Returns {:assets [...]}."
  [rows]
  {:assets (vec (filter #(= (:type %) :asset) rows))})

(defn assets
  "Load a seed file and return just the asset rows (:clj only)."
  [path]
  #?(:clj (:assets (classify (load-edn path)))
     :default (throw (ex-info "assets: file load is :clj-only" {}))))
