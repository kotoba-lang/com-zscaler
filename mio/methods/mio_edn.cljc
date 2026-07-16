#!/usr/bin/env bb
;; 澪 mio — seed loader + classifier (clj-native, pure stdlib).
(ns mio.methods.mio-edn
  "澪 mio — load + classify the flow-improvement CLAIM seed substrate.

  Reads the actor's own EDN substrate (kotoba/seed.edn) into Clojure data with
  real keyword keys, and splits it by :type. Dependency-free (clojure.edn is
  stdlib; file I/O is :clj-only). Sibling of the busshi/kabuto *_edn loaders —
  each actor reads its own substrate. Energy Order Protocol backbone.

  kotoba/seed.edn is now Datomic/Datascript tx-data on disk (each row wrapped
  as a `{:db/id N :mio.claim/* ...}` entity, ADR-2606230001 fan-out, 2026-07).
  `parse-edn` reconstitutes it back into the original flat vector of bare-keyed
  claim maps (stripping :db/id + the :mio.claim namespace) so every downstream
  consumer of `claims`/`load-edn`/`classify` — in this actor's own methods AND
  in 20-actors/energy_order/validate.cljc — keeps working unchanged against
  bare :type/:id/:flow-class/... keys."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn- tx-data?
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn- unblob
  "Non-scalar attribute values are stored pr-str'd (Datomic :db.type/string
  blob). Parse them back to data; leave plain scalars (including ordinary
  strings that don't happen to read as a collection) untouched."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-entity
  "Undo entity-from-map: drop :db/id, strip the :mio.claim namespace back to a
  bare key (every key in a seed row is :mio.claim/* — none were pre-namespaced),
  unblob any pr-str'd values."
  [entity]
  (into {}
        (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc entity :db/id)))

(defn parse-edn
  "Parse an EDN string. Historically a top-level vector of bare-keyed claim
  maps; now a tx-data vector of :mio.claim/* entities — reconstituted back to
  the historical bare-keyed shape so callers never notice the on-disk format
  changed."
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
  "Split the flat seed vector by :type. Returns {:claims [...]}."
  [rows]
  {:claims (vec (filter #(= (:type %) :claim) rows))})

(defn claims
  "Convenience: load a seed file and return just the claim rows (:clj only)."
  [path]
  #?(:clj (:claims (classify (load-edn path)))
     :default (throw (ex-info "claims: file load is :clj-only" {}))))
