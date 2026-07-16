#!/usr/bin/env bb
;; 委 yudane — seed loader + classifier (clj-native, pure stdlib).
(ns yudane.methods.yudane-edn
  "委 yudane — load + classify the consented-intention seed substrate.
  Sibling of the mio/tawami *_edn loaders. Energy Order Protocol."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)] (parse-edn (slurp r)))))

(defn- unblob
  "Reverse a pr-str'd blob attribute value back into its original collection, if it
  parses to one. Non-blob (already-live) values pass through unchanged."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Un-namespace + un-blob a single tx-data entity map back into the flat, bare-keyed
  offer map the rest of this actor's code expects (mirrors the pattern used repo-wide
  for datomic/datascript-queryable seed files, ADR-2607100000-ish edn-datomize wave)."
  [entity]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc entity :db/id)))

(defn- tx-data-rows?
  "True when `rows` is already in [{:db/id ... <ns>/<key> <val> ...} ...] tx-data shape
  (the datomic/datascript-queryable form seed.edn now ships in) rather than the legacy
  flat bare-keyed vector."
  [rows]
  (and (vector? rows) (seq rows) (map? (first rows)) (contains? (first rows) :db/id)))

(defn classify
  "Split the flat seed vector by :type. Returns {:offers [...]}. Tolerant of both the
  legacy flat bare-keyed vector AND the tx-data (per-row entity, :yudane.offer/*
  namespaced, :db/id-bearing) shape seed.edn now uses — tx-data rows are reconstituted
  back to bare :type/:id/:cohort-size/... maps first, so every downstream (:type %),
  (:id %), (:cohort-size %) etc. lookup across this actor and its cross-actor consumers
  (energy_order/validate.cljc, digest.cljc, mio/test_suite.cljc, ...) keeps working
  unchanged."
  [rows]
  (let [rows (if (tx-data-rows? rows) (map reconstitute-entity rows) rows)]
    {:offers (vec (filter #(= (:type %) :offer) rows))}))

(defn offers [path]
  #?(:clj (:offers (classify (load-edn path)))
     :default (throw (ex-info "offers: file load is :clj-only" {}))))
