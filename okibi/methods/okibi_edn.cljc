#!/usr/bin/env bb
;; 燠 okibi — seed loader + classifier (clj-native, pure stdlib).
(ns okibi.methods.okibi-edn
  "燠 okibi — load + classify the thermal seed substrate into sources + sinks.
  Sibling of the mio/tawami *_edn loaders. Energy Order Protocol."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)] (parse-edn (slurp r)))))

(defn- unblob
  "seed.edn rows may now carry pr-str'd (blob) values for non-scalar attrs
  (datomize transform). Read them back to live EDN; pass through unchanged
  otherwise."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :default :default) _ v))
    v))

(defn- reconstitute-row
  "Tolerates seed.edn rows in EITHER shape:
    legacy bare map      — {:type :source :id \"dc-a\" ...}
    datomized tx-data     — {:db/id -1 :okibi.source/type :source :okibi.source/id \"dc-a\" ...}
  Un-namespaces + un-blobs a tx-data row back to the original bare-key shape so
  downstream `:type`/`:id`/etc. lookups keep working unchanged either way."
  [row]
  (if (contains? row :db/id)
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)])) (dissoc row :db/id))
    row))

(defn classify
  "Split the flat seed vector by :type. Returns {:sources [...] :sinks [...]}.
  Tolerates both the legacy bare-map seed.edn shape and the datomized tx-data
  shape (each row a {:db/id ... :okibi.source/* | :okibi.sink/* ...} entity)."
  [rows]
  (let [rows (map reconstitute-row rows)]
    {:sources (vec (filter #(= (:type %) :source) rows))
     :sinks   (vec (filter #(= (:type %) :sink) rows))}))

(defn sources [path]
  #?(:clj (:sources (classify (load-edn path)))
     :default (throw (ex-info "sources: file load is :clj-only" {}))))

(defn sinks [path]
  #?(:clj (:sinks (classify (load-edn path)))
     :default (throw (ex-info "sinks: file load is :clj-only" {}))))
