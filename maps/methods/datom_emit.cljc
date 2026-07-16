(ns maps.methods.datom-emit
  "maps — kotoba Datom-log emitter (canonical EAVT state, ADR-2605312345).
  New file (ADR-2606064500 R2).

  Projects the maps spatial graph into append-only kotoba Datoms [e a v tx op].

    GROUND (durable, op :add) — one datom per (entity, attribute, value): the
      :feature/* nodes, :feature.rel/* edges, :geo.alias/* aliases.
      Keeps \":feature.cell/r*\" cell stamps (derived at ingest, stored here).

    DERIVED (transient, :bond/is-transient true) — coverage metrics (cell count,
      bbox). Computed on READ, not persisted (N1/G2 — no per-feature 'coverage score').

  CONSTITUTIONAL (ADR-2606064500 gates):
    G3 — every GROUND datom for a :feature/* entity carries :feature/sourcing.
    G9 — feature = placed thing, never person; no PII-class attribute allowed.

  House style: keyword strings stay ':ns/name' strings (not Clojure keywords);
  pure fns; file I/O only at the #?(:clj) edge. Portable .cljc."
  (:require [clojure.string :as str]
            [maps.methods.analyze :as analyze]))

;; Emission order of :feature/* attributes (mirrors Python's to_kg_batch claim order).
(def feature-attrs
  [":feature/id" ":feature/label" ":feature/sourcing"
   ":feature/name" ":feature/display-name" ":feature/category"
   ":feature/source-did" ":feature/lat" ":feature/lon"
   ":feature/height-m" ":feature/levels"
   ":feature/geometry" ":feature/props"
   ;; cell stamps at standard resolutions
   ":feature.cell/r2" ":feature.cell/r4" ":feature.cell/r6"
   ":feature.cell/r8" ":feature.cell/r10" ":feature.cell/r12"])

(def rel-attrs
  [":feature.rel/id" ":feature.rel/from" ":feature.rel/to" ":feature.rel/kind"])

(def alias-attrs
  [":geo.alias/id" ":geo.alias/scheme" ":geo.alias/code"
   ":geo.alias/feature" ":geo.alias/label"])

(defn- fmt [v]
  (cond
    (true? v)  "true"
    (false? v) "false"
    (nil? v)   "nil"
    (string? v)
    (if (str/starts-with? v ":")
      v
      (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\""))
    :else (str v)))

(defn- datoms-for-entity
  "Emit one :add datom per (entity, attr, value) for the given attrs."
  [entity attrs tx-id]
  (into []
        (keep (fn [a]
                (let [v (get entity a)]
                  (when (some? v)
                    (str "[" (fmt (get entity ":feature/id"
                                       (get entity ":feature.rel/id"
                                            (get entity ":geo.alias/id" "?"))))
                         " " a " " (fmt v) " " tx-id " :add]"))))
              attrs)))

(defn emit-datoms
  "Return a vector of EDN datom strings from the maps graph.
  [features rels aliases] are the classify outputs."
  [features rels aliases]
  (let [tx-id  "tx.maps.seed.r1"
        ground (into []
                     (concat
                      ;; :feature/* entities
                      (mapcat #(datoms-for-entity % feature-attrs tx-id) (vals features))
                      ;; :feature.rel/* edges
                      (mapcat #(datoms-for-entity % rel-attrs tx-id) rels)
                      ;; :geo.alias/* aliases
                      (mapcat #(datoms-for-entity % alias-attrs tx-id) (vals aliases))))
        ;; DERIVED: coverage metrics (transient)
        n-feats  (count features)
        derived  [(str "[\"coverage/feature-count\" :coverage/feature-count " n-feats
                       " tx.maps.seed.r1.derived :add :bond/is-transient true]")
                  (str "[\"coverage/n-rels\" :coverage/n-rels " (count rels)
                       " tx.maps.seed.r1.derived :add :bond/is-transient true]")
                  (str "[\"coverage/n-aliases\" :coverage/n-aliases " (count aliases)
                       " tx.maps.seed.r1.derived :add :bond/is-transient true]")]]
    {:ground ground :derived derived}))

(defn render-edn
  "Full kotoba-EDN emission block (ground + transient header)."
  [features rels aliases]
  (let [{:keys [ground derived]} (emit-datoms features rels aliases)
        L (transient [])]
    (conj! L ";; maps — kotoba Datom log emission (ADR-2606064500 R2).")
    (conj! L ";; GROUND datoms — durable :add, one per (e a v tx).")
    (conj! L "[")
    (doseq [d ground] (conj! L (str " " d)))
    (conj! L "]")
    (conj! L "")
    (conj! L ";; ── DERIVED readouts (transient, :bond/is-transient true; NOT the ground state)")
    (conj! L ";; Computed on READ from the seed graph; never re-ingest as :authoritative.")
    (conj! L "[")
    (doseq [d derived] (conj! L (str " " d)))
    (conj! L "]")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& argv]
     (let [here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (clojure.java.io/file here "data" "seed-spatial-graph.kotoba.edn")
           outdir (clojure.java.io/file here "out")
           [features rels aliases] (analyze/classify (analyze/load-edn seed))]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "maps-datoms.kotoba.edn")
             (render-edn features rels aliases))
       (println (str "maps datom-emit: " (count features) " features, "
                     (count rels) " rels, " (count aliases) " aliases"))
       0)))
