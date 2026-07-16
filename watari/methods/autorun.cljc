(ns watari.methods.autorun
  "autorun.py — watari AUTONOMOUS live-moving-craft situational-awareness heartbeat on the kotoba
  Datom log. ADR-2606041827. 1:1 Clojure port of `methods/autorun.py`.

  Each heartbeat the actor runs its whole SITUATIONAL-AWARENESS pipeline ITSELF, with no human in
  the loop:
    observe (load the OFFLINE merged craft graph, G7: no live feed) → classify
      → analyze (latest as-of fix per craft → lane/corridor load by kind → chokepoint transit →
        approach congestion → freshness tail — aggregate-first, G2 awareness not surveillance)
      → PERSIST a content-addressed transaction to the append-only LOCAL kotoba Datom log
        (graph datoms + derived :movement/* signals), linking the previous tx's CID.

  Constitutional posture holds by construction: aggregate lane/chokepoint/approach density framed
  toward safety + congestion-easing — NEVER a 'follow this craft' / targeting feed (G2); a craft is
  a craft, NEVER a person — no track linked to a named individual, no pattern-of-life (G4, the
  defining gate). The chokepoint-transit output composes with watatsuna's static cable-load over
  the SAME chokepoint keywords (静↔動 maritime resilience).

  The loop is deterministic / resume-safe (cycle drives tx-id + as-of → same CIDs) and append-only
  (the fix stream IS the trajectory; 非終末論). WHAT STAYS GATED (G7): it NEVER pulls a live
  AISStream / OpenSky / adsb.fi feed and NEVER pushes to a live kotoba node.

  House style: requires only the GOOD sibling .cljc ports (analyze + kotoba), not any stub.
  (The Python `__main__` argparse demo printer is preserved behind #?(:clj …) as -main.)"
  (:require [watari.methods.analyze :as analyze]
            [watari.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.string :as str])))

(def base-as-of 20260608)

#?(:clj (def here (-> *file* io/file .getAbsoluteFile .getParentFile)))
#?(:clj (def data (when here (io/file (.getParentFile here) "data"))))
#?(:clj (def merged (when data (io/file data "craft-graph.merged.kotoba.edn"))))
#?(:clj (def seed (when data (io/file data "seed-craft-graph.kotoba.edn"))))
#?(:clj (def log-default (when data (io/file data "watari.datoms.kotoba.edn"))))

#?(:clj
   (defn- graph-path
     "Pick the graph EDN: the explicit path, else the merged graph if it exists, else the seed."
     [graph-path]
     (cond
       (some? graph-path) graph-path
       (and merged (.exists merged)) merged
       :else seed)))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → classify → analyze → persist a content-addressed Datom
     transaction (graph + derived :movement/* signals). cycle drives tx-id + as-of."
     ([cycle] (run-cycle cycle nil log-default))
     ([cycle graph log-path]
      (let [rows (kotoba/load-edn (graph-path graph))      ; observe — OFFLINE merged graph (G7);
                                                           ; kotoba reader preserves record key
                                                           ; order so the tx CID matches python3

            [craft fixes legs lanes] (analyze/classify rows)
            a (analyze/analyze craft fixes legs lanes)      ; aggregate situational-awareness (G2)
            datoms (into (kotoba/graph-datoms rows) (kotoba/derived-datoms craft lanes a))
            tx (kotoba/make-tx datoms :tx-id cycle :as-of (+ base-as-of cycle)
                               :prev-cid (kotoba/head-cid log-path))
            cid (kotoba/append-tx tx log-path)              ; PERSIST to append-only LOCAL kotoba log
            choke (get a "choke_transit")
            top-choke (if (seq choke)
                        (apply max-key (fn [k] (get choke k)) (keys choke))
                        "—")]
        {"cycle" cycle
         "craft" (count craft)
         "fixes" (count fixes)
         "lanes" (count lanes)
         "chokepoints" (count choke)
         "top_chokepoint" top-choke
         "stale" (count (get a "stale"))
         "datoms" (count datoms)
         "cid" cid}))))

#?(:clj
   (defn run-autonomous
     ([] (run-autonomous 3 nil log-default))
     ([cycles] (run-autonomous cycles nil log-default))
     ([cycles graph log-path]
      (let [beats (mapv #(run-cycle % graph log-path) (range 1 (inc cycles)))]
        {"cycles" cycles
         "beats" beats
         "log_length" (count (kotoba/read-log log-path))
         "head_cid" (kotoba/head-cid log-path)
         "chain" (kotoba/verify-chain log-path)}))))

#?(:clj
   (defn -main
     "CLI entry: run N autonomous heartbeats → LOCAL kotoba Datom log. --cycles/--graph/--log/--fresh
     (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           arg-after (fn [flag dflt] (let [i (.indexOf argv flag)]
                                       (if (>= i 0) (nth argv (inc i)) dflt)))
           cycles (let [v (arg-after "--cycles" nil)] (if v (Long/parseLong v) 3))
           graph (let [v (arg-after "--graph" nil)] (when v (io/file v)))
           log-path (io/file (arg-after "--log" (str log-default)))]
       (when (and (some #{"--fresh"} argv) (.exists log-path)) (.delete log-path))
       (let [res (run-autonomous cycles graph log-path)]
         (println (str "# watari — AUTONOMOUS moving-craft situational-awareness over the kotoba "
                       "Datom log (offline ingest, LOCAL persist; live AIS/ADS-B feed / live-node "
                       "push stays G7-gated)\n"))
         (doseq [bt (get res "beats")]
           (println (str "  ♥ cycle " (get bt "cycle") ": " (get bt "craft") " craft / "
                         (get bt "fixes") " fixes / " (get bt "lanes") " lanes · chokepoints "
                         (get bt "chokepoints") " (top " (get bt "top_chokepoint") ") · stale-tail "
                         (get bt "stale") " +" (get bt "datoms") " datoms → cid "
                         (subs (get bt "cid") 0 14) "…")))
         (let [ch (get res "chain")]
           (println (str "\n  log: " (get res "log_length") " tx · head "
                         (subs (get res "head_cid") 0 14) "… · chain "
                         (if (get ch "ok") "OK ✓" (str "BROKEN at " (get ch "broken_at")))
                         " · situational-awareness, no person-tracking (G2/G4))")))))))
