(ns tatara.methods.autorun
  "tatara 鑪 — AUTONOMOUS manufacturing-concentration heartbeat on the kotoba Datom log.
  ADR-2606171800.

  Each heartbeat the actor runs its whole aggregate-analysis pipeline ITSELF, no human in the
  loop:
    observe (load the OFFLINE plant graph, G7: no live feed) → classify
      → analyze (per-sector country HHI + single-source → chokepoint export-dependence →
        country employment/floor rollup → per-sector capacity rollup — aggregate-first, G2
        resilience not interdiction)
      → PERSIST a content-addressed transaction to the append-only LOCAL kotoba Datom log
        (graph datoms + derived :concentration/* signals), linking the previous tx's CID into a
        verifiable commit-DAG.

  Constitutional posture holds by construction: only aggregate concentration / chokepoint
  export-dependence is representable — never a 'where to disrupt' / target-list (G2). Facility
  figures are DISCLOSED AGGREGATE sizes; no :worker/* / :person/* attribute exists (G4).

  Deterministic / resume-safe (cycle drives tx-id + as-of → same CIDs), append-only (非終末論).
  WHAT STAYS GATED (G7): it NEVER pulls a live disclosure/GLEIF/OSM feed and NEVER pushes to a
  live kotoba node."
  (:require [tatara.methods.analyze :as analyze]
            [tatara.methods.kotoba :as kotoba]
            [tatara.methods.compose :as compose]
            #?(:clj [clojure.java.io :as io])))

(def base-as-of 20260617)

#?(:clj (def here (-> *file* io/file .getAbsoluteFile .getParentFile)))
#?(:clj (def data (when here (io/file (.getParentFile here) "data"))))
#?(:clj (def merged (when data (io/file data "plant-graph.merged.kotoba.edn"))))
#?(:clj (def seed (when data (io/file data "seed-plant-graph.kotoba.edn"))))
#?(:clj (def log-default (when data (io/file data "tatara.datoms.kotoba.edn"))))

#?(:clj
   (defn- graph-path
     "Pick the graph EDN: the explicit path, else the merged graph if it exists, else the seed."
     [gp]
     (cond
       (some? gp) gp
       (and merged (.exists merged)) merged
       :else seed)))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → classify → analyze → persist a content-addressed Datom
     transaction (graph + derived :concentration/* signals). cycle drives tx-id + as-of."
     ([cycle] (run-cycle cycle nil log-default))
     ([cycle graph log-path]
      (let [gp (graph-path graph)
            rows (analyze/load-edn gp)                     ; observe — OFFLINE graph (G7)
            g (analyze/classify rows)
            a (analyze/analyze g)                          ; aggregate resilience signal (G2)
            ;; cross-actor composition — sibling seeds resolved OFFLINE relative to the actor dir;
            ;; absent siblings → empty legs (graceful), still no external I/O (G7)
            actor-dir (some-> gp clojure.java.io/file .getAbsoluteFile .getParentFile .getParentFile)
            sib-root (some-> actor-dir .getParentFile)
            comp (if sib-root
                   (compose/choke-composition
                    a
                    (compose/watari-choke-transit (io/file sib-root "watari" "data" "seed-craft-graph.kotoba.edn"))
                    (compose/watatsuna-choke-load (io/file sib-root "watatsuna" "data" "cable-graph.merged.kotoba.edn")))
                   [])
            datoms (-> (kotoba/graph-datoms rows)
                       (into (kotoba/derived-datoms a))
                       (into (compose/composition-eavt comp)))
            tx (kotoba/make-tx datoms :tx-id cycle :as-of (+ base-as-of cycle)
                               :prev-cid (kotoba/head-cid log-path))
            cid (kotoba/append-tx tx log-path)             ; PERSIST to append-only LOCAL kotoba log
            top-choke (first (analyze/chokes-by-load a))]
        {:cycle cycle
         :plants (:n-plants a)
         :hubs (:n-hubs a)
         :flows (:n-flows a)
         :sectors (count (:sector-stats a))
         :top-chokepoint (when top-choke (name top-choke))
         :aggregate-employment (:global-headcount a)
         :datoms (count datoms)
         :cid cid}))))

#?(:clj
   (defn run-autonomous
     ([] (run-autonomous 3 nil log-default))
     ([cycles] (run-autonomous cycles nil log-default))
     ([cycles graph log-path]
      (let [beats (mapv #(run-cycle % graph log-path) (range 1 (inc cycles)))]
        {:cycles cycles
         :beats beats
         :log-length (count (kotoba/read-log log-path))
         :head-cid (kotoba/head-cid log-path)
         :chain (kotoba/verify-chain log-path)}))))

#?(:clj
   (defn -main
     "CLI entry: run N autonomous heartbeats → LOCAL kotoba Datom log.
     --cycles/--graph/--log/--fresh (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           arg-after (fn [flag dflt] (let [i (.indexOf argv flag)]
                                       (if (>= i 0) (nth argv (inc i)) dflt)))
           cycles (let [v (arg-after "--cycles" nil)] (if v (Long/parseLong v) 3))
           graph (let [v (arg-after "--graph" nil)] (when v (io/file v)))
           log-path (io/file (arg-after "--log" (str log-default)))]
       (when (and (some #{"--fresh"} argv) (.exists log-path)) (.delete log-path))
       (let [res (run-autonomous cycles graph log-path)]
         (println (str "# tatara — AUTONOMOUS manufacturing-concentration resilience over the kotoba "
                       "Datom log (offline ingest, LOCAL persist; live disclosure/GLEIF/OSM feed / "
                       "live-node push stays G7-gated)\n"))
         (doseq [bt (:beats res)]
           (println (str "  ♥ cycle " (:cycle bt) ": " (:plants bt) " plants / " (:flows bt)
                         " flows / " (:sectors bt) " sectors · top-chokepoint " (:top-chokepoint bt)
                         " · agg-employment " (:aggregate-employment bt) " +" (:datoms bt)
                         " datoms → cid " (subs (:cid bt) 0 14) "…")))
         (let [ch (:chain res)]
           (println (str "\n  log: " (:log-length res) " tx · head " (subs (:head-cid res) 0 14)
                         "… · chain " (if (:ok ch) "OK ✓" (str "BROKEN at " (:broken-at ch)))
                         " · resilience map, never a target-list (G2); no per-worker data (G4)")))))))
