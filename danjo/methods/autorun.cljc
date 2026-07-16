(ns danjo.methods.autorun
  "autorun.py — danjo AUTONOMOUS public-accountability cross-reference heartbeat on the kotoba
  Datom log. ADR-2605301600. 1:1 Clojure port of `methods/autorun.py`.

  Each heartbeat the actor runs its whole oversight pipeline ITSELF, with no human in the loop:
    observe (load the OFFLINE pre-published procurement corpus + the OPEN method-pack, G3)
      → run every IMPLEMENTED open detector (R0/R1: single-bidder-streak) → build
        danjo.discrepancyObservation records (G4 non-adjudicating, G5 ≥2 source CIDs, G6 method-note)
      → PERSIST a content-addressed transaction to the append-only LOCAL kotoba Datom log
        (procurement-record graph datoms + derived observation datoms), linking the previous CID.

  Constitutional posture holds by construction: the censor's EYE, never the SWORD — only FACTUAL
  discrepancy observations are representable, NEVER a verdict (G4). Passive-only ingestion of the
  pre-published corpus (G3). Named-party publication stays G10 + 1 SBT = 1 vote gated — this loop
  persists to the LOCAL log only. Deterministic / resume-safe (cycle drives tx-id + as-of) and
  append-only. No external I/O.

  House style: requires only the GOOD sibling .cljc ports (analyze + kotoba), not any stub.
  (The Python `__main__` argparse demo printer is preserved behind #?(:clj …) as -main.)"
  (:require [danjo.methods.analyze :as analyze]
            [danjo.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.string :as str])))

(def base-as-of 20260609)

#?(:clj (def here (-> *file* io/file .getAbsoluteFile .getParentFile)))
#?(:clj (def data (when here (io/file (.getParentFile here) "data"))))
#?(:clj (def corpus-default (when data (io/file data "corpus.seed.json"))))
#?(:clj (def methods-default (when here (io/file here "v1-jp-seed.json"))))
#?(:clj (def log-default (when data (io/file data "persisted" "danjo.datoms.kotoba.edn"))))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe corpus + open methods → run detectors → persist a
     content-addressed Datom transaction (procurement graph + discrepancy observations). cycle
     drives tx-id + as-of."
     ([cycle] (run-cycle cycle corpus-default methods-default log-default))
     ([cycle corpus-path methods-path log-path]
      (let [corpus (analyze/load-json corpus-path)       ; observe — OFFLINE pre-published corpus (G3)
            methods (analyze/load-json methods-path)      ; the OPEN method-pack (G6)
            records (get corpus "procurementRecords" [])
            observations (analyze/run-all corpus methods) ; FACTUAL observations (G4 non-adjudicating)
            datoms (into (kotoba/graph-datoms records) (kotoba/derived-datoms observations))
            tx (kotoba/make-tx datoms :tx-id cycle :as-of (+ base-as-of cycle)
                               :prev-cid (kotoba/head-cid log-path))
            cid (kotoba/append-tx tx log-path)]           ; PERSIST to append-only LOCAL kotoba log
        {"cycle" cycle
         "records" (count records)
         "methods" (count (get methods "methods" []))
         "observations" (count observations)
         "datoms" (count datoms)
         "cid" cid}))))

#?(:clj
   (defn run-autonomous
     ([] (run-autonomous 3 corpus-default methods-default log-default))
     ([cycles] (run-autonomous cycles corpus-default methods-default log-default))
     ([cycles corpus-path methods-path log-path]
      (let [beats (mapv #(run-cycle % corpus-path methods-path log-path)
                        (range 1 (inc cycles)))]
        {"cycles" cycles
         "beats" beats
         "log_length" (count (kotoba/read-log log-path))
         "head_cid" (kotoba/head-cid log-path)
         "chain" (kotoba/verify-chain log-path)}))))

#?(:clj
   (defn -main
     "CLI entry: run N autonomous heartbeats → LOCAL kotoba Datom log. --cycles/--corpus/--methods/
     --log/--fresh (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           arg-after (fn [flag dflt] (let [i (.indexOf argv flag)]
                                       (if (>= i 0) (nth argv (inc i)) dflt)))
           cycles (let [v (arg-after "--cycles" nil)] (if v (Long/parseLong v) 3))
           corpus-path (io/file (arg-after "--corpus" (str corpus-default)))
           methods-path (io/file (arg-after "--methods" (str methods-default)))
           log-path (io/file (arg-after "--log" (str log-default)))]
       (when (and (some #{"--fresh"} argv) (.exists log-path)) (.delete log-path))
       (let [res (run-autonomous cycles corpus-path methods-path log-path)]
         (println (str "# danjo — AUTONOMOUS public-accountability cross-reference over the kotoba "
                       "Datom log (offline corpus, LOCAL persist; live fetch / named-party publish "
                       "stays G3/G10-gated)\n"))
         (doseq [bt (get res "beats")]
           (println (str "  ♥ cycle " (get bt "cycle") ": " (get bt "records")
                         " procurement records / " (get bt "methods") " open methods → "
                         (get bt "observations") " discrepancy observation(s) +"
                         (get bt "datoms") " datoms → cid " (subs (get bt "cid") 0 14) "…")))
         (let [ch (get res "chain")]
           (println (str "\n  log: " (get res "log_length") " tx · head "
                         (subs (get res "head_cid") 0 14) "… · chain "
                         (if (get ch "ok") "OK ✓" (str "BROKEN at " (get ch "broken_at")))
                         " · the censor's EYE, never the SWORD — non-adjudicating (G4)")))))))
