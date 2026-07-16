#!/usr/bin/env bb
;; umisachi 海幸 — AUTONOMOUS marine-bounty restoration heartbeat on the kotoba Datom log.
(ns umisachi.methods.autorun
  "autorun.clj — umisachi AUTONOMOUS seafood/marine-bounty heartbeat (ADR-2606074200 / 2605312345).

  Each heartbeat the actor runs its whole pipeline itself, no human in the loop:
    observe (load the OFFLINE marine-bounty graph, G1 charter-clean at load) → analyze
    (edge-primary provisioning-nourishment vs 取-depletion integrals, aggregate-first) →
    PERSIST a content-addressed transaction (graph datoms + derived :bond/*) to the append-only
    kotoba Datom log, linking the previous tx's CID.

  Deterministic / resume-safe (cycle drives tx-id + as-of → same CIDs) and append-only (非終末論).
  WHAT STAYS GATED (G7): never a live FAO/RAM/ICES feed, never a live-node push — ingest is the
  offline graph, persistence is the LOCAL append-only log. G1: a RESTORATION map, never a
  catch-target list.

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/autorun.clj --cycles 3 --fresh"
  (:require [umisachi.methods.analyze :as a]
            [umisachi.methods.kotoba :as k]
            [clojure.java.io :as io]))

(def ^:private this-file *file*)
(defn- data-dir [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile (io/file "data")))
(defn- seed [] (io/file (data-dir) "seed-umisachi-graph.kotoba.edn"))
(defn- default-log [] (io/file (data-dir) "umisachi.datoms.kotoba.edn"))
(def BASE-AS-OF 20260714)

(defn- graph-path [gp] (or gp (seed)))

(defn run-cycle
  "One autonomous heartbeat: observe → analyze → persist a content-addressed Datom transaction
  (graph + derived :bond/* integrals). cycle drives tx-id + as-of."
  [cycle & {:keys [graph-path* log-path]}]
  (let [log-path (or log-path (default-log))
        [nodes edges] (a/load-file (str (graph-path graph-path*)))   ; observe — OFFLINE (G7)
        res (a/analyze nodes edges)
        datoms (vec (concat (k/graph-datoms nodes edges) (k/derived-datoms res)))
        tx (k/make-tx datoms :tx-id cycle :as-of (+ BASE-AS-OF cycle) :prev-cid (k/head-cid log-path))
        cid (k/append-tx tx log-path)
        top-nourish (if (seq (:nourishment res)) (key (apply max-key val (:nourishment res))) "—")]
    {:cycle cycle :nodes (count nodes) :edges (count edges)
     :nourishment-bearers (count (:nourishment res)) :top-nourishment top-nourish
     :depletion-bearers (count (:depletion res)) :datoms (count datoms) :cid cid}))

(defn run-autonomous [& {:keys [cycles graph-path* log-path] :or {cycles 3}}]
  (let [log-path (or log-path (default-log))
        beats (mapv #(run-cycle % :graph-path* graph-path* :log-path log-path) (range 1 (inc cycles)))]
    {:cycles cycles :beats beats :log-length (count (k/read-log log-path))
     :head-cid (k/head-cid log-path) :chain (k/verify-chain log-path)}))

(defn -main [& argv]
  (let [args (vec argv)
        cyc-idx (.indexOf args "--cycles")
        cycles (if (>= cyc-idx 0) (Integer/parseInt (nth args (inc cyc-idx))) 3)
        log-idx (.indexOf args "--log")
        log-path (if (>= log-idx 0) (io/file (nth args (inc log-idx))) (default-log))]
    (when (and (some #{"--fresh"} args) (.exists (io/file log-path)))
      (.delete (io/file log-path)))
    (let [res (run-autonomous :cycles cycles :log-path log-path)]
      (println (str "# umisachi — AUTONOMOUS marine-bounty restoration over the kotoba Datom log "
                    "(offline ingest, LOCAL persist; live feed / live-node push stays G7-gated)\n"))
      (doseq [bt (:beats res)]
        (println (format "  ♥ cycle %d: %d nodes / %d 縁 · nourishment-bearers %d (top %s) · depletion-bearers %d +%d datoms → cid %s…"
                         (:cycle bt) (:nodes bt) (:edges bt) (:nourishment-bearers bt)
                         (:top-nourishment bt) (:depletion-bearers bt) (:datoms bt)
                         (subs (:cid bt) 0 14))))
      (let [ch (:chain res)]
        (println (format "\n  log: %d tx · head %s… · chain %s · restoration map, never a catch-target list (G1)"
                         (:log-length res) (subs (:head-cid res) 0 14)
                         (if (:ok ch) "OK ✓" (str "BROKEN at " (:broken-at ch)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
