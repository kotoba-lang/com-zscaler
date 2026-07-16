#!/usr/bin/env bb
;; tsuchifumi 土踏み — seed loader + classifier (clj-native, pure stdlib).
(ns tsuchifumi.methods.tsuchifumi-edn
  "tsuchifumi 土踏み — load + classify the seed substrate (kotoba/seed.edn).
  Reads the actor's own EDN substrate into Clojure data and splits by :type into
  {:regions … :evidence … :drivers …}. Dependency-free (clojure.edn stdlib; file
  I/O :clj-only). Sibling of the kafun/ugachi/busshi *_edn loaders. ADR-2606212000.

  kotoba/seed.edn is stored on disk as a Datomic/Datascript tx-data vector
  (each region/evidence/driver row is its own entity with :db/id + a
  namespaced-by-:type attribute set — :tsuchifumi.region/*, :tsuchifumi.ev/*,
  :tsuchifumi.driver/* — so the file is directly queryable). `reconstitute-rows`
  reverses that back into the flat bare-key row shape every method/test in this
  actor is written against, so callers of `load-seed`/`classify` are unaffected."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)]
       (parse-edn (slurp r)))))

(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn reconstitute-rows
  "Reconstitute a kotoba/seed.edn-shaped tx-data vector (`[{:db/id -1
  :tsuchifumi.region/id ... } ...]`) back into the flat bare-key rows
  `classify` expects. Strips :db/id, un-prefixes every namespaced key back to
  its bare local name (every key in a raw seed row is ours, never a
  pre-existing foreign namespace, so this is safe here), and un-blobs any
  pr-str'd non-scalar value. A no-op on rows that are already bare (so callers
  stay valid even against an un-transformed/legacy seed.edn)."
  [tx-data]
  (mapv (fn [entity]
          (into {}
                (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
                (dissoc entity :db/id)))
        tx-data))

(defn classify
  "Split the flat seed vector by :type. Returns {:regions [...] :evidence [...] :drivers [...]}."
  [rows]
  {:regions  (vec (filter #(= (:type %) :region) rows))
   :evidence (vec (filter #(= (:type %) :evidence) rows))
   :drivers  (vec (filter #(= (:type %) :driver) rows))})

(defn load-seed
  "Load a seed file and return the classified map (:clj only)."
  [path]
  #?(:clj (classify (reconstitute-rows (load-edn path)))
     :default (throw (ex-info "load-seed: file load is :clj-only" {}))))
