#!/usr/bin/env bb
;; ugachi 穿ち — seed loader + classifier (clj-native, pure stdlib).
(ns ugachi.methods.ugachi-edn
  "ugachi 穿ち — load + classify the extraction-project seed substrate.
  Reads the actor's own EDN substrate (kotoba/seed.edn) into Clojure data and
  splits by :type. Dependency-free (clojure.edn stdlib; file I/O :clj-only).
  Sibling of the busshi/kakaku/kabuto *_edn loaders. ADR-2606161800."
  (:require [clojure.edn :as edn]
            #?(:clj [clojure.java.io :as io])))

(defn parse-edn [text] (edn/read-string text))

#?(:clj
   (defn load-edn [path]
     (with-open [r (io/reader path)]
       (parse-edn (slurp r)))))

;; ── tx-data normalization (datomic/datascript queryable seed files) ─────────
;;
;; kotoba/seed.edn (and sibling actors' seed files, e.g. busshi's) may now be
;; stored as Datomic/Datascript tx-data — `[{:db/id -1 <ns>/<key> <value> ...}
;; …]`, one entity per row, namespaced per manifest/edn-datomize.cljs — rather
;; than the legacy flat `[{:type … :id … …} …]` shape. normalize-rows accepts
;; EITHER shape and always returns legacy flat rows, so every bare-key / :type
;; lookup below (and in gate.cljc / bridge.cljc / autorun.cljc / the test
;; suites) keeps working unchanged regardless of which shape the source file
;; is currently in — actors migrate to tx-data independently of each other.

(defn- unblob
  "Non-scalar attribute values (nested maps / vectors-of-maps) are pr-str'd by
  the datomizer into a blob string; parse it back if it round-trips to a coll."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-entity
  "tx-data entity map -> legacy bare-keyed map (drops :db/id, strips namespace)."
  [entity]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc entity :db/id)))

(defn already-tx-data?
  "Recognizes `[{...:db/id ...} ...]` tx-data (see manifest/edn-datomize.cljs)."
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn normalize-rows
  "Accept legacy flat rows OR tx-data; always return legacy flat rows."
  [content]
  (if (already-tx-data? content)
    (mapv reconstitute-entity content)
    content))

(defn classify
  "Split the flat seed vector by :type. Returns {:projects [...]}."
  [rows]
  {:projects (vec (filter #(= (:type %) :project) (normalize-rows rows)))})

(defn projects
  "Convenience: load a seed file and return just the project rows (:clj only)."
  [path]
  #?(:clj (:projects (classify (load-edn path)))
     :default (throw (ex-info "projects: file load is :clj-only" {}))))
