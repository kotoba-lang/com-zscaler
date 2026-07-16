(ns kaname.cell
  "kaname 要 cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).

  Registered in 50-infra/cluster/murakumo/cell-runner/cells.edn as KanameHeartbeatCell
  (node naphtali, cron 53 * * * *, healthz 13083). `fire` runs ONE deterministic heartbeat
  (ADR-2606172100 / pattern 2606091000):

      世界認識 — join every committed mirror into the cross-domain world model → compute the 要
      (R1 betweenness/ΔΦ) → route to OPENING → おせっかい proposal → ONE content-addressed tx
      appended to the actor-local kotoba commit-DAG → chain verified.

  NO external I/O in the cell — the live web-fetch (`--live`) and the LIVE-engine bridge
  (kotoba_bridge, KANAME_KOTOBA_LIVE + operator DID) remain G7/operator-gated. The returned
  summary is aggregate-only (G1): the single 要 + mirror set + counts, never a per-person score."
  (:require [kaname.autorun :as autorun]
            [kotoba.datom :as kd]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn- actor-dir
     "20-actors/kaname, resolved from this namespace's classpath location (runs from any cwd)."
     []
     (-> (io/resource "kaname/cell.cljc") io/file .getParentFile)))

#?(:clj
   (def log-default
     (delay (io/file (actor-dir) "data" "persisted" "kaname.leverage.kotoba.edn"))))

#?(:clj
   (defn fire
     "One heartbeat. Idempotent per log state (cycle derives from log length)."
     ([] (fire nil))
     ([log-path]
      (let [base   (str (.getParentFile (actor-dir)))      ; 20-actors
            target (str (or log-path @log-default))
            n      (count (kd/read-log target))
            r (autorun/beat {:base-dir base :log-path target
                             :tx-id (str "kaname-" n) :as-of (str "as-of:" n) :live? false})]
        (println (str "KanameHeartbeatCell cycle " n ": 要=" (:point r)
                      " world=" (:world r) " mirrors=" (pr-str (:mirrors r))
                      " appended=" (:appended r) (when (:reason r) (str " (" (:reason r) ")"))
                      " head=" (some-> (:head r) (subs 0 (min 16 (count (:head r)))))))
        r))))
