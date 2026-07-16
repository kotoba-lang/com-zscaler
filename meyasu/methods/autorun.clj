(ns meyasu.methods.autorun
  "meyasu 目安 — AUTONOMOUS fuse→persist heartbeat on the kotoba Datom log. clj-native SSoT
  (ADR-2606142300 D1: new logic-core authored in Clojure) + ADR-2606073201.

  Each heartbeat the actor runs its whole 統合-arbitrage pipeline ITSELF, no human in the loop:
  observe (the OFFLINE fused-input snapshot — per-product kakaku + mitooshi records) → FUSE into
  unified buyer-transparency + supply-resilience cards (meyasu.methods.agent/handle-fuse) → PERSIST one
  content-addressed transaction (the cards' Datoms) to the append-only LOCAL kotoba Datom log,
  linking the previous CID into a verifiable commit-DAG.

  Constitution holds by construction: a point-asserted / speculative forecast is REFUSED at fuse and
  never reaches a card (G2); a card's forecast is written as a BAND, never a point (G1/G2);
  `:trade`/`:speculation` are unrepresentable (G1); publication / live-node push stay operator-gated
  (no-server-key). NO external I/O — the loop reads a LOCAL snapshot and writes the LOCAL log only;
  live kakaku/mitooshi ingest is the operator-gated leg. Deterministic / resume-safe (cycle drives
  tx-id + as-of; observed-at is a fixed snapshot stamp → same cycles produce the same commit-DAG)."
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [meyasu.methods.agent :as agent]
            [meyasu.methods.kotoba :as k]))

(def base-as-of 20260616)
(def snapshot-stamp "snapshot")   ; deterministic observed-at (no wall clock)

(def ^:private here (-> (io/file *file*) .getParentFile .getParentFile))
(def seed-default (str (io/file here "kotoba" "seed.json")))

(defn load-items
  "Read the fused-input snapshot JSON → items. String-keyed throughout (agent.cljc's
  fuse-one/RESILIENCE-USES convention — \"use\" is already the colon-prefixed string
  \":resilience\" as authored in the seed; no keywordizing needed or wanted)."
  [seed-path]
  (let [doc (json/parse-string (slurp (io/file seed-path)))]
    (vec (get doc "items" []))))

(defn- canon-v [v] (if (keyword? v) (str v) v))   ; :balanced → ":balanced"

(defn- card-datoms
  "One fused card → family EAVT datoms ([:db/add e a v], string attrs) via the actor's
  card-to-datoms (G1/G2: forecast as a band, never a point)."
  [card observed-at]
  (mapv (fn [[e a v]] (k/add e (str a) (canon-v v)))
        (agent/card-to-datoms card observed-at)))

(defn run-cycle
  "One heartbeat: observe snapshot → fuse → persist one content-addressed tx (all cards)."
  [cycle seed-path log-path]
  (let [items (load-items seed-path)
        fused (agent/handle-fuse {"items" items})
        cards (get fused "cards")
        datoms (vec (mapcat #(card-datoms % snapshot-stamp) cards))
        tx (k/make-tx datoms cycle (+ base-as-of cycle) (k/head-cid log-path))
        cid (k/append-tx tx log-path)]
    {:cycle cycle :cards (count cards) :refused (count (get fused "refused"))
     :datoms (count datoms) :cid cid}))

(defn run-autonomous [cycles seed-path log-path]
  (let [beats (mapv #(run-cycle % seed-path log-path) (range 1 (inc cycles)))]
    {:cycles cycles :beats beats :log-length (count (k/read-log log-path))
     :head-cid (k/head-cid log-path) :chain (k/verify-chain log-path)}))

(defn -main [& argv]
  (let [argv (vec argv)
        opt (fn [f d] (let [i (.indexOf argv f)] (if (>= i 0) (nth argv (inc i)) d)))
        flag? (fn [f] (>= (.indexOf argv f) 0))
        cycles (Long/parseLong (str (opt "--cycles" "3")))
        seed-path (opt "--seed" seed-default)
        log-path (opt "--log" k/log-default)]
    (when (and (flag? "--fresh") (.exists (io/file log-path))) (.delete (io/file log-path)))
    (let [res (run-autonomous cycles seed-path log-path)]
      (println "# meyasu — AUTONOMOUS 統合-arbitrage fuse→persist over the kotoba Datom log "
               "(offline snapshot, LOCAL persist; publication + live ingest stay operator-gated)\n")
      (doseq [bt (:beats res)]
        (println (str "  ♥ cycle " (:cycle bt) ": " (:cards bt) " cards (" (:refused bt)
                      " refused G2) +" (:datoms bt) " datoms → cid "
                      (subs (:cid bt) 0 (min 14 (count (:cid bt)))) "…")))
      (let [ch (:chain res)]
        (println (str "\n  log: " (:log-length res) " tx · head "
                      (subs (:head-cid res) 0 (min 14 (count (:head-cid res)))) "… · chain "
                      (if (:ok ch) "OK ✓" (str "BROKEN at " (:broken-at ch)))
                      " · 目安 not a trade (G1), forecast as band (G2)")))
      (System/exit (if (:ok (:chain res)) 0 1)))))
