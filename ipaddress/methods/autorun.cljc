(ns ipaddress.methods.autorun
  "autorun.py — ipaddress AUTONOMOUS heartbeat loop on the kotoba Datom log.
  1:1 Clojure port of `methods/autorun.py` (ADR-2605301400 §T2).

  Each heartbeat the actor runs its whole pipeline ITSELF, no human in the loop:

    observe (load the OFFLINE merged IP/ASN graph) → classify → analyze concentration
      → PERSIST a content-addressed transaction to the append-only kotoba Datom log
        (graph datoms + derived :ipnet/* concentration), linking the previous tx's CID.

  Deterministic / idempotent-by-CID / append-only. NO external I/O — offline ingest,
  LOCAL persist (G7/G8 stay gated). Live full-universe RIR/RDAP ingest + live-node push
  stay one human gate-flip away by design.

  House style: pure orchestration; file I/O via the sibling kotoba/ip-edn #?(:clj …) edges.
  The Python __main__ CLI (argparse) is omitted; -main is provided behind #?(:clj …).
  NOTE: imports NO network module path (no urllib/http/socket/requests/subprocess) — the
  no-external-I/O test scans this source string."
  (:require [ipaddress.methods.analyze :as analyze]
            [ipaddress.methods.ip-edn :as ip-edn]
            [ipaddress.methods.kotoba :as kotoba]))

(def base-as-of 20260608)

#?(:clj
   (def ^:private here (-> *file* clojure.java.io/file .getAbsoluteFile .getParentFile)))
#?(:clj
   (def ^:private data (clojure.java.io/file (.getParentFile here) "data")))
#?(:clj
   (def merged (clojure.java.io/file data "ip-network.merged.kotoba.edn")))
#?(:clj
   (def seed (clojure.java.io/file data "seed-ip-network.kotoba.edn")))
#?(:clj
   (def log (clojure.java.io/file data "ipaddress.datoms.kotoba.edn")))

#?(:clj
   (defn- resolve-graph-path [gp]
     (if (some? gp)
       gp
       (if (.exists merged) merged seed))))

#?(:clj
   (defn run-cycle
     "One autonomous heartbeat: observe → classify → analyze → persist a content-addressed
     Datom transaction (graph + derived :ipnet/* concentration). Returns a heartbeat summary.
     cycle drives tx-id + as-of (deterministic / resume-safe)."
     [cycle & {:keys [graph-path log-path] :or {graph-path nil log-path log}}]
     (let [rows (ip-edn/load-edn (resolve-graph-path graph-path))
           b (ip-edn/classify rows)
           a (analyze/analyze b)
           datoms (into (kotoba/graph-datoms rows) (kotoba/derived-datoms a))
           tx (kotoba/make-tx datoms {:tx-id cycle :as-of (+ base-as-of cycle)
                                      :prev-cid (kotoba/head-cid log-path)})
           cid (kotoba/append-tx tx log-path)
           top (if (seq (get a "asn_prefix")) (first (get a "asn_prefix")) [nil "—" 0 nil nil])]
       {"cycle" cycle
        "rirs" (count (get b "rirs"))
        "asns" (count (get b "asns"))
        "ranges" (+ (get a "v4") (get a "v6"))
        "prefix_hhi" (get a "prefix_hhi")
        "space_hhi" (get a "space_hhi")
        "top_asn" (nth top 1)
        "datoms" (count datoms)
        "cid" cid})))

#?(:clj
   (defn run-autonomous
     "Drive `cycles` self-paced heartbeats. Each appends one content-addressed transaction to
     the kotoba Datom log. Returns the run summary + final head CID + chain verification."
     [& {:keys [cycles graph-path log-path] :or {cycles 3 graph-path nil log-path log}}]
     (let [beats (mapv (fn [c] (run-cycle c :graph-path graph-path :log-path log-path))
                       (range 1 (inc cycles)))]
       {"cycles" cycles
        "beats" beats
        "log_length" (count (kotoba/read-log log-path))
        "head_cid" (kotoba/head-cid log-path)
        "chain" (kotoba/verify-chain log-path)})))
