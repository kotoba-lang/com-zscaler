(ns shionome.methods.autorun
  "autorun.cljc — 潮目 (shionome) AUTONOMOUS heartbeat loop on the kotoba Datom log.
  Clojure port of methods/autorun.py (1:1, the run-cycle/run-autonomous core). ADR-2606072200.

  Each heartbeat the actor runs its whole pipeline ITSELF, no human in the loop:
    observe (load public-source seed) → weave + VALIDATE (G1/G2/G3, トレードはしない)
      → aggregate concentration (edge-primary, G4)
      → draft DRY-RUN social posts (G5 mirror, G2 no-trade body scan)
      → PERSIST a content-addressed transaction to the append-only kotoba Datom log (G10).

  Deterministic (the caller supplies the cycle index → tx-id + as-of → resume-safe CIDs);
  idempotent-by-CID (each tx links the previous content address → a verifiable commit-DAG that
  only grows, 非終末論). Live external publication + live ingest stay G8/G7-gated (one human
  gate-flip away); this loop NEVER posts to a network and NEVER ingests live. All same-actor
  dependencies (weave / social / kotoba / edn) are already ported. stdlib only; file I/O at
  the #?(:clj) edge."
  (:require [shionome.methods.edn :as sedn]
            [shionome.methods.weave :as weave]
            [shionome.methods.social :as social]
            [shionome.methods.kotoba :as kotoba]))

(def ^:private seed-default "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")
(def ^:private log-default "20-actors/shionome/data/shionome.datoms.kotoba.edn")
(def ^:private BASE-AS-OF 20260607)

(defn- draft-posts
  "Build the dry-run social posts for one heartbeat from the woven graph + concentration."
  [g c]
  (let [allsrcs (sort (distinct (mapcat #(get % ":flow/sources" []) (get g "flows"))))]
    (if (seq allsrcs)
      (cond-> []
        (seq (get c "net_flow_by_bucket")) (conj (social/draft-netflow-post (get c "net_flow_by_bucket") allsrcs))
        (seq (get c "rotation_pairs"))      (conj (social/draft-rotation-post (get c "rotation_pairs") allsrcs))
        true                                (conj (social/draft-regime-post (get c "regime") allsrcs)))
      [])))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → validate → weave → analyze → dry-run post → persist a
     content-addressed Datom transaction. Returns a heartbeat summary. `cycle` drives tx-id +
     as-of (deterministic / resume-safe)."
     ([cycle] (run-cycle cycle seed-default log-default))
     ([cycle seed-path log-path]
      (let [g      (weave/weave (sedn/load-edn seed-path))   ; observe + VALIDATE (raises on a gate)
            c      (weave/concentration g)                   ; aggregate, edge-primary (G4)
            posts  (draft-posts g c)                         ; DRY-RUN, no-trade body-scanned (G2/G5)
            datoms (into (kotoba/graph-datoms g)
                         (kotoba/post-datoms posts (str "post-c" cycle)))
            tx     (kotoba/make-tx datoms {:tx-id cycle :as-of (+ BASE-AS-OF cycle)
                                           :prev-cid (kotoba/head-cid log-path)})
            cid    (kotoba/append-tx tx log-path)]           ; PERSIST to append-only log (G10)
        {"cycle" cycle
         "regime" (get-in c ["regime" "regime"])
         "top_inflow" (if (seq (get c "net_flow_by_bucket"))
                        (get (first (get c "net_flow_by_bucket")) "label") "—")
         "datoms" (count datoms)
         "posts" (count posts)
         "cid" cid}))))

#?(:clj
   (defn run-autonomous
     "Drive `cycles` self-paced heartbeats; each appends one content-addressed transaction to the
     kotoba Datom log. Returns the run summary + final head CID + chain verification."
     ([cycles] (run-autonomous cycles seed-default log-default))
     ([cycles seed-path log-path]
      (let [beats (mapv #(run-cycle % seed-path log-path) (range 1 (inc cycles)))]
        {"cycles" cycles
         "beats" beats
         "log_length" (count (kotoba/read-log log-path))
         "head_cid" (kotoba/head-cid log-path)
         "chain" (kotoba/verify-chain log-path)}))))
