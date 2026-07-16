#!/usr/bin/env bb
;; kafun 花粉 — seed loader + classifier (clj-native, pure stdlib).
(ns kafun.methods.kafun-edn
  "kafun 花粉 — load + classify the forest-stand seed substrate.
  Reads the actor's own EDN substrate (kotoba/seed.edn) into Clojure data and
  splits by :type. Dependency-free (clojure.edn stdlib; file I/O :clj-only).
  Sibling of the ugachi/busshi/kakaku *_edn loaders. ADR-2606211712.

  kotoba/seed.edn is stored as Datomic/Datascript tx-data (Phase 4 EDN
  datomize fan-out): a vector of `:kafun.stand/*`-namespaced entity maps,
  each with a `:db/id`. `classify` transparently reconstitutes the original
  bare-keyed row shape (`{:type :stand :id ... :species ...}`) from tx-data
  so every downstream caller (cell.cljc, ie_flow.cljc, autorun/digest tests,
  70-tools/src/etzhayyim/ie_flow/scoreboard.clj) keeps working unchanged. A
  pre-transform seed.edn (plain vector of bare maps, no :db/id) is also
  still accepted, so this is backward-compatible either way."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)]
       (parse-edn (slurp r)))))

(defn- tx-data?
  "True if rows is already-transformed Datomic/Datascript tx-data
  (a vector of entity maps carrying :db/id) rather than the pre-transform
  plain vector of bare-keyed row maps."
  [rows]
  (and (vector? rows) (seq rows) (map? (first rows)) (contains? (first rows) :db/id)))

(defn- unblob
  "Reverse of edn-datomize's pr-str blobbing: if v looks like a pr-str'd
  collection, read it back; otherwise return v unchanged. Plain scalar
  strings (e.g. :note prose) safely pass through unchanged."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-entity
  "Un-namespace + un-blob one tx-data entity back to a bare-keyed row map,
  dropping :db/id."
  [entity]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc entity :db/id)))

(defn classify
  "Split the flat seed vector by :type. Returns {:stands [...]}.
  Tolerant of both tx-data (post Phase-4 transform) and the pre-transform
  plain bare-map vector shape."
  [rows]
  (let [rows (if (tx-data? rows) (mapv reconstitute-entity rows) rows)]
    {:stands (vec (filter #(= (:type %) :stand) rows))}))

(defn stands
  "Convenience: load a seed file and return just the stand rows (:clj only)."
  [path]
  #?(:clj (:stands (classify (load-edn path)))
     :default (throw (ex-info "stands: file load is :clj-only" {}))))
