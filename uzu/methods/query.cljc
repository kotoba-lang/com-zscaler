#!/usr/bin/env bb
;; uzu 渦 — read-back the information log: EAVT index + as-of time-travel (the log is queryable).
(ns uzu.methods.query
  "query.cljc — uzu 渦 information-log read path (ADR-2606211500).

  The append-only commit-DAG (kotoba.cljc) is not write-only: this namespace folds its EAVT
  datoms back into entity views and supports AS-OF time-travel (reconstruct the state as of the
  first n transactions), the way a Datom log is meant to be read (ADR-2605312345). Pure folds
  over datom/tx vectors; :clj only reads the file via kotoba/read-log. No network (no-server-key)."
  (:require [uzu.methods.kotoba :as k]
            [clojure.string :as str]))

(defn index
  "Fold a flat datom seq ([op entity attr value]) into {entity {attr value}} (last write wins).
  Only :db/add is produced by this actor, so there are no retractions to apply."
  [datoms]
  (reduce (fn [m [_op e a v]] (assoc-in m [e a] v)) {} datoms))

(defn pull
  "All attributes of one entity from an index."
  [idx entity]
  (get idx entity {}))

(defn datoms-asof
  "All datoms in the first `n` transactions (as-of read). n=nil ⇒ all txs."
  [txs n]
  (vec (mapcat #(get % ":tx/datoms") (if n (take n txs) txs))))

(defn entities-of-kind
  "Entity ids whose id string starts with `prefix` (e.g. \"uzu:flow/\")."
  [idx prefix]
  (->> (keys idx) (filter #(str/starts-with? % prefix)) sort vec))

;; ── :clj convenience: read a log file and query it ───────────────────────────
#?(:clj
   (do
     (defn index-of-log
       "Index the log file as of the first n txs (n=nil ⇒ whole log)."
       ([log-path] (index-of-log log-path nil))
       ([log-path n] (index (datoms-asof (k/read-log log-path) n))))

     (defn colony-digest
       "Reconstruct the colony digest entity from the log."
       [log-path]
       (pull (index-of-log log-path) "uzu:digest/colony"))

     (defn flow
       "Reconstruct one measured flow entity from the log."
       [log-path flow-id]
       (pull (index-of-log log-path) (str "uzu:flow/" flow-id)))

     (defn -main [& args]
       (let [log-path (or (first args)
                          (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                              (clojure.java.io/file "data" "persisted" "uzu.information.kotoba.edn") str)
                          )]
         (if (empty? (k/read-log log-path))
           (println (str "no log at " log-path " — run autorun first"))
           (let [idx (index-of-log log-path)]
             (println (str "log " log-path " — " (count (k/read-log log-path)) " tx(s)"))
             (println (str "organisms: " (entities-of-kind idx "uzu:organism/")))
             (println (str "flows: " (count (entities-of-kind idx "uzu:flow/"))))
             (println (str "colony digest: " (colony-digest log-path)))))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
