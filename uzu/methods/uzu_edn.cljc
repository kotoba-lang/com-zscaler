#!/usr/bin/env bb
;; uzu 渦 — seed loader + classifier (clj-native, pure stdlib).
(ns uzu.methods.uzu-edn
  "uzu 渦 — load + classify the seed substrate (ADR-2606211500).
  Reads the actor's own EDN substrate (kotoba/seed.edn) and splits by :type into the
  world tape, organism configs, measured flows, and circulation edges. Sibling of the
  ugachi/busshi *_edn loaders. Dependency-free (clojure.edn; file I/O :clj-only)."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)] (parse-edn (slurp r)))))

(defn classify
  "Split the flat seed vector by :type."
  [rows]
  {:tape (->> rows (filter #(= (:type %) :world-step)) (sort-by :step) vec)
   :organisms (vec (filter #(= (:type %) :organism) rows))
   :flows (vec (filter #(= (:type %) :flow) rows))
   :edges (vec (filter #(= (:type %) :circulation) rows))})

(defn tape
  "Convenience: load a seed file and return the ordered world tape (:clj only)."
  [path]
  #?(:clj (:tape (classify (load-edn path)))
     :default (throw (ex-info "tape: file load is :clj-only" {}))))

(defn organisms [path]
  #?(:clj (:organisms (classify (load-edn path)))
     :default (throw (ex-info "organisms: file load is :clj-only" {}))))

(defn flows [path]
  #?(:clj (:flows (classify (load-edn path)))
     :default (throw (ex-info "flows: file load is :clj-only" {}))))

;; ── ontology loading (tolerant of both the bare-map and the tx-data shape) ──
;;
;; kotoba/ontology.uzu.edn was converted to a Datomic/Datascript tx-data vector
;; (edn-datomize pass, ADR-2607100000-series): `[{:db/id -1 :ontology/* ...}]`
;; with non-scalar values pr-str'd into blob strings. `load-ontology` always
;; hands callers back the original bare-keyed map shape
;; ({:node-kinds [...] :enums {...} ...}) so `get-in ontology [:enums :regime]`
;; style lookups (validate.cljc et al) are unaffected by the on-disk shape.

(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (parse-edn v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn reconstitute-ontology
  "tx-data [{:db/id -1 :ontology/* ...}] -> the original bare-keyed ontology map.
  Keys that were already namespaced on disk (:ontology/id :ontology/version
  :ontology/adr) keep that namespace; the rest (:node-kinds :enums
  :unit-classes :invariants :attributes) are un-blobbed and returned bare,
  matching kotoba/ontology.uzu.edn's pre-datomize shape."
  [tx-data]
  (let [e (first tx-data)
        top #{:ontology/id :ontology/version :ontology/adr}]
    (into {}
          (map (fn [[k v]]
                 [(if (contains? top k) k (keyword (name k)))
                  (unblob v)]))
          (dissoc e :db/id))))

(defn already-tx-data? [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn ontology-map
  "Load an ontology EDN value already read into memory (via parse-edn/load-edn),
  tolerant of both the bare-map (pre-datomize) and tx-data (post-datomize) shape."
  [content]
  (if (already-tx-data? content) (reconstitute-ontology content) content))

#?(:clj
   (defn load-ontology [path]
     (ontology-map (load-edn path))))
