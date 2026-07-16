#!/usr/bin/env bb
;; busshi 物資 — seed loader + classifier (clj-native, pure stdlib).
(ns busshi.methods.busshi-edn
  "busshi 物資 — load + classify the commodity/materials seed substrate.

  Reads the actor's own EDN substrate (kotoba/seed.edn) into Clojure data with
  real keyword keys, and splits it by :type. Dependency-free (clojure.edn is
  stdlib; file I/O is :clj-only). Sibling of the kabuto/kanjō/kasa/kakaku *_edn
  loaders — each actor reads its own substrate. ADR-2606161730.

  seed.edn is stored on disk as Datomic/Datascript tx-data (a vector of
  entity maps, each carrying :db/id + :busshi.commodity/* namespaced attrs,
  per the repo-wide EDN-queryable convention, 2026-07-10 Phase 4). parse-edn
  transparently RECONSTITUTES tx-data back into the flat, bare-keyed row
  shape (`{:type :commodity :id \"au\" :producers [...] ...}`) that
  `classify`/`commodities` and every downstream caller (analyze.cljc,
  autorun.cljc) already expect — so this is the ONE place that shape lives,
  and the on-disk format can be tx-data without touching any call site."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn- tx-data?
  "True if content is already a Datomic/Datascript tx-data vector (a vector
  of entity maps, each carrying :db/id)."
  [content]
  (and (vector? content) (seq content) (every? map? content)
       (every? #(contains? % :db/id) content)))

(defn- unblob
  "Reverse of edn-datomize's blob encoding: a pr-str'd non-scalar value
  (nested map / vector-of-vectors / etc.) parses back to a collection and is
  restored; a genuinely scalar string round-trips unchanged (it either fails
  to parse as EDN, or parses to a non-collection like a symbol, in which case
  the original string is kept)."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-entity
  "tx-data entity map -> flat bare-keyed row map (drops :db/id, strips each
  attr's namespace, unblobs pr-str'd values)."
  [entity]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc entity :db/id)))

(defn parse-edn
  "Parse an EDN string into Clojure data. Accepts either the legacy flat
  top-level vector-of-maps shape, or the on-disk tx-data shape (vector of
  :db/id entity maps) — tx-data is transparently reconstituted back to the
  flat shape so every caller keeps using plain bare keys (:type, :producers,
  :sourcing, ...) unchanged."
  [text]
  (let [content (edn/read-string text)]
    (if (tx-data? content)
      (mapv reconstitute-entity content)
      content)))

#?(:clj
   (defn load-edn
     "Load + parse an EDN file from disk (:clj only)."
     [path]
     (with-open [r (io/reader path)]
       (parse-edn (slurp r)))))

(defn classify
  "Split the flat seed vector by :type. Returns {:commodities [...]}."
  [rows]
  {:commodities (vec (filter #(= (:type %) :commodity) rows))})

(defn commodities
  "Convenience: load a seed file and return just the commodity rows (:clj only)."
  [path]
  #?(:clj (:commodities (classify (load-edn path)))
     :default (throw (ex-info "commodities: file load is :clj-only" {}))))
