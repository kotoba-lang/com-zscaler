(ns kabuto.methods.autorun
  "autorun.py — kabuto AUTONOMOUS public-company-supply-chain resilience heartbeat on the kotoba
  Datom log. ADR-2606022000. 1:1 Clojure port of `methods/autorun.py`.

  Each heartbeat the actor runs its whole supply-chain RESILIENCE pipeline ITSELF, no human in the
  loop:
    observe (load the OFFLINE merged public-company graph, G7: no live fetch) → classify
      → analyze (in/out degree → single-source → sector × commodity concentration → jurisdiction /
        region-bloc load → intermediaries / tier-depth / cross-bloc corridors / market-cap HHI —
        aggregate-first, G2 RESILIENCE + accountability map not a target-list)
      → PERSIST a content-addressed transaction to the append-only LOCAL kotoba Datom log
        (graph datoms + derived :supply/* signals), linking the previous tx's CID.

  Constitutional posture holds by construction: every derived signal is framed toward supply
  diversification + corporate-power accountability — NEVER a 'who to hit' / raid / takeover map (G2);
  public listed-company public-record data only (G1); concentration is an observation, never an
  antitrust/sanctions verdict (G4). The loop persists exactly what analyze computes, derived flagged
  :supply/derived.

  Deterministic / resume-safe (cycle drives tx-id + as-of → same CIDs) and append-only. WHAT STAYS
  GATED (G7 / G11): it NEVER fetches the live GLEIF/EDGAR/exchange universe and NEVER pushes to a live
  kotoba node or posts to atproto.

  Determinism note: kabuto's analyze builds intermediaries/tier_depth by iterating Python `set`s, so
  `_canonical_order` sorts the datoms by canonical JSON before hashing — making the CID reproducible
  / resume-safe across processes (EAVT is an unordered set, so order carries no meaning). This port
  mirrors that exactly via kotoba/datom-key.

  House style: requires only the actor's existing .cljc siblings (kabuto-edn + analyze + kotoba).
  (The Python `__main__` argparse demo printer is preserved behind #?(:clj …) as -main.)"
  (:require [kabuto.methods.kabuto-edn :as kedn]
            [kabuto.methods.analyze :as analyze]
            [kabuto.methods.kotoba :as kotoba]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.string :as str])))

(defn canonical-order
  "Sort datoms by canonical JSON so the tx is DETERMINISTIC regardless of analyze's internal
  ordering. EAVT assertions are an unordered set (order carries no meaning), so a canonical sort
  here makes the content-addressed CID reproducible / resume-safe. Mirrors `_canonical_order`."
  [datoms]
  (vec (sort-by kotoba/datom-key datoms)))

(def base-as-of 20260609)

#?(:clj (def here (-> *file* io/file .getAbsoluteFile .getParentFile)))
#?(:clj (def data (when here (io/file (.getParentFile here) "data"))))
#?(:clj (def merged (when data (io/file data "companies.merged.kotoba.edn"))))
#?(:clj (def seed (when data (io/file data "seed-public-companies.kotoba.edn"))))
#?(:clj (def log-default (when data (io/file data "kabuto.datoms.kotoba.edn"))))

#?(:clj
   (defn- graph-path
     "Default observation graph: merged graph if present, else the seed (mirrors _graph_path)."
     [graph-path]
     (cond
       graph-path graph-path
       (and merged (.exists merged)) merged
       :else seed)))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → classify → analyze → persist a content-addressed Datom
     transaction (graph + derived :supply/* signals). cycle drives tx-id + as-of."
     ([cycle] (run-cycle cycle nil log-default))
     ([cycle graph-path* log-path]
      (let [rows (kedn/load-edn (graph-path graph-path*))       ; observe — OFFLINE merged graph (G7)
            {:keys [companies edges]} (kedn/classify rows)
            a (analyze/analyze companies edges)                 ; aggregate RESILIENCE signal (G2)
            datoms (canonical-order (into (kotoba/graph-datoms rows)
                                          (kotoba/derived-datoms a))) ; deterministic / resume-safe
            tx (kotoba/make-tx datoms :tx-id cycle :as-of (+ base-as-of cycle)
                               :prev-cid (kotoba/head-cid log-path))
            cid (kotoba/append-tx tx log-path)                  ; PERSIST to append-only LOCAL log
            systemic (get a "systemic")
            top-systemic (if (seq systemic)
                           (key (apply max-key (fn [[_ v]] v) systemic))
                           "—")]
        {"cycle" cycle
         "companies" (count companies)
         "edges" (count edges)
         "single_source" (count (get a "single_source"))
         "intermediaries" (count (get a "intermediaries"))
         "cap_hhi" (get a "cap_hhi" 0.0)
         "top_systemic" top-systemic
         "datoms" (count datoms)
         "cid" cid}))))

#?(:clj
   (defn run-autonomous
     ([] (run-autonomous 3 nil log-default))
     ([cycles] (run-autonomous cycles nil log-default))
     ([cycles graph-path* log-path]
      (let [beats (mapv #(run-cycle % graph-path* log-path) (range 1 (inc cycles)))]
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
           graph-path* (let [g (arg-after "--graph" nil)] (when g (io/file g)))
           log-path (io/file (arg-after "--log" (str log-default)))]
       (when (and (some #{"--fresh"} argv) (.exists log-path)) (.delete log-path))
       (let [res (run-autonomous cycles graph-path* log-path)]
         (println (str "# kabuto — AUTONOMOUS public-company supply-chain resilience over the kotoba "
                       "Datom log (offline ingest, LOCAL persist; live GLEIF/EDGAR universe / "
                       "live-node push stays G7/G11-gated)\n"))
         (doseq [bt (get res "beats")]
           (println (str "  ♥ cycle " (get bt "cycle") ": " (get bt "companies")
                         " companies / " (get bt "edges") " supply-edges · single-source "
                         (get bt "single_source") " · intermediaries " (get bt "intermediaries")
                         " · cap-HHI " (get bt "cap_hhi") " +" (get bt "datoms")
                         " datoms → cid " (subs (get bt "cid") 0 14) "…")))
         (let [ch (get res "chain")]
           (println (str "\n  log: " (get res "log_length") " tx · head "
                         (subs (get res "head_cid") 0 14) "… · chain "
                         (if (get ch "ok") "OK ✓" (str "BROKEN at " (get ch "broken_at")))
                         " · resilience + accountability map, never a target-list (G2/G4)")))))))
