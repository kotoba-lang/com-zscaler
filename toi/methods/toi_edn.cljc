#!/usr/bin/env bb
;; 樋 toi — seed loader + classifier (clj-native, pure stdlib).
(ns toi.methods.toi-edn
  "樋 toi — load + classify the compute seed substrate into jobs + sites.
  Sibling of the mio/okibi *_edn loaders. Energy Order Protocol."
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
    legacy bare map    — {:type :job :id \"train-1\" ...}
    datomized tx-data  — {:db/id -1 :toi.job/type :job :toi.job/id \"train-1\" ...}
  Un-namespaces + un-blobs a tx-data row back to the original bare-key shape so
  downstream `:type`/`:id`/etc. lookups keep working unchanged either way."
  [row]
  (if (contains? row :db/id)
    (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)])) (dissoc row :db/id))
    row))

(defn classify
  "Split the flat seed vector by :type. Returns {:jobs [...] :sites [...]}.
  Tolerates both the legacy bare-map seed.edn shape and the datomized tx-data
  shape (each row a {:db/id ... :toi.job/* | :toi.site/* ...} entity)."
  [rows]
  (let [rows (map reconstitute-row rows)]
    {:jobs  (vec (filter #(= (:type %) :job) rows))
     :sites (vec (filter #(= (:type %) :site) rows))}))

(defn jobs [path]
  #?(:clj (:jobs (classify (load-edn path)))
     :default (throw (ex-info "jobs: file load is :clj-only" {}))))

(defn sites [path]
  #?(:clj (:sites (classify (load-edn path)))
     :default (throw (ex-info "sites: file load is :clj-only" {}))))
