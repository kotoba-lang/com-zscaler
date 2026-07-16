(ns watatsuna.methods.autorun
  "autorun.py — watatsuna AUTONOMOUS submarine-cable-resilience heartbeat on the kotoba Datom log.
  ADR-2606012600. 1:1 Clojure port of `methods/autorun.py`.

  Each heartbeat the actor runs its whole RESILIENCE pipeline ITSELF, with no human in the loop:
    observe (load the OFFLINE merged cable graph) → classify
      → analyze (cable⇄station incidence → station degree/capacity → chokepoint-load →
        cable-diversity → redundancy-gap — aggregate-first, G2 RESILIENCE map not interdiction)
      → PERSIST a content-addressed transaction to the append-only LOCAL kotoba Datom log
        (graph datoms + derived :resilience/* signals), linking the previous tx's CID.

  Constitutional posture holds by construction: every derived signal is framed toward redundancy /
  diverse routing / faster repair — NEVER a 'where to cut' target-list (G2, mirrors watatsumi N8);
  public-record cable data only (G1). The loop is deterministic / resume-safe (cycle drives tx-id +
  as-of → same CIDs) and append-only. NO external I/O: ingest is the offline merged graph;
  persistence is the LOCAL append-only log. Live planet-scale ingest is Council + operator gated.

  House style: requires only the GOOD sibling .cljc ports (analyze + kotoba), not any stub.
  (The Python `__main__` argparse demo printer is preserved behind #?(:clj …) as -main.)"
  (:require [watatsuna.methods.analyze :as analyze]
            [watatsuna.methods._edn :as edn]
            [watatsuna.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.string :as str])))

(def base-as-of 20260608)

#?(:clj (def here (-> *file* io/file .getAbsoluteFile .getParentFile)))
#?(:clj (def data (when here (io/file (.getParentFile here) "data"))))
#?(:clj (def merged (when data (io/file data "cable-graph.merged.kotoba.edn"))))
#?(:clj (def seed (when data (io/file data "seed-cable-graph.kotoba.edn"))))
#?(:clj (def log-default (when data (io/file data "watatsuna.datoms.kotoba.edn"))))

#?(:clj
   (defn- graph-path
     "If graph-path is given, use it; else MERGED if it exists, else SEED."
     [graph-path]
     (cond
       (some? graph-path) graph-path
       (and merged (.exists merged)) merged
       :else seed)))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → classify → analyze → persist a content-addressed Datom
     transaction (graph + derived :resilience/* signals). cycle drives tx-id + as-of."
     ([cycle] (run-cycle cycle nil log-default))
     ([cycle graph-path-arg log-path]
      (let [rows (edn/load-edn (graph-path graph-path-arg)) ; observe — OFFLINE merged graph (G7)
            {:keys [cables stations links segs faults]} (#'analyze/attach-orders (analyze/classify rows))
            a (analyze/analyze cables stations links segs faults) ; aggregate RESILIENCE signal (G2)
            datoms (into (kotoba/graph-datoms rows) (kotoba/derived-datoms cables stations a))
            tx (kotoba/make-tx datoms :tx-id cycle :as-of (+ base-as-of cycle)
                               :prev-cid (kotoba/head-cid log-path))
            cid (kotoba/append-tx tx log-path)               ; PERSIST to append-only LOCAL kotoba log
            choke-load (get a "choke_load")
            top-choke (if (seq choke-load)
                        (apply max-key (fn [k] (get choke-load k)) (keys choke-load))
                        "—")]
        {"cycle" cycle
         "cables" (count cables)
         "stations" (count stations)
         "segments" (count segs)
         "faults" (count faults)
         "chokepoints" (count choke-load)
         "top_chokepoint" top-choke
         "redundancy_gaps" (count (get a "redundancy_gap"))
         "datoms" (count datoms)
         "cid" cid}))))

#?(:clj
   (defn run-autonomous
     ([] (run-autonomous 3 nil log-default))
     ([cycles] (run-autonomous cycles nil log-default))
     ([cycles graph-path-arg log-path]
      (let [beats (mapv #(run-cycle % graph-path-arg log-path) (range 1 (inc cycles)))]
        {"cycles" cycles
         "beats" beats
         "log_length" (count (kotoba/read-log log-path))
         "head_cid" (kotoba/head-cid log-path)
         "chain" (kotoba/verify-chain log-path)}))))

#?(:clj
   (defn -main
     "CLI entry: run N autonomous heartbeats → LOCAL kotoba Datom log.
     --cycles/--graph/--log/--fresh (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           arg-after (fn [flag dflt] (let [i (.indexOf argv flag)]
                                       (if (>= i 0) (nth argv (inc i)) dflt)))
           cycles (let [v (arg-after "--cycles" nil)] (if v (Long/parseLong v) 3))
           graph-path-arg (let [v (arg-after "--graph" nil)] (when v (io/file v)))
           log-path (io/file (arg-after "--log" (str log-default)))]
       (when (and (some #{"--fresh"} argv) (.exists log-path)) (.delete log-path))
       (let [res (run-autonomous cycles graph-path-arg log-path)]
         (println (str "# watatsuna — AUTONOMOUS submarine-cable resilience over the kotoba Datom log "
                       "(offline ingest, LOCAL persist; live feed / live-node push stays G7-gated)\n"))
         (doseq [bt (get res "beats")]
           (println (str "  ♥ cycle " (get bt "cycle") ": " (get bt "cables") " cables / "
                         (get bt "stations") " stations / " (get bt "segments") " segs / "
                         (get bt "faults") " faults · chokepoints " (get bt "chokepoints")
                         " (top " (get bt "top_chokepoint") ") · redundancy-gaps "
                         (get bt "redundancy_gaps") " +" (get bt "datoms") " datoms → cid "
                         (subs (get bt "cid") 0 14) "…")))
         (let [ch (get res "chain")]
           (println (str "\n  log: " (get res "log_length") " tx · head "
                         (subs (get res "head_cid") 0 14) "… · chain "
                         (if (get ch "ok") "OK ✓" (str "BROKEN at " (get ch "broken_at")))
                         " · resilience map, never interdiction (G2)")))))))
