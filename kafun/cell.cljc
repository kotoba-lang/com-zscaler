(ns kafun.cell
  "kafun 花粉 cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).

  Registered in 50-infra/cluster/murakumo/cell-runner/cells.edn as
  KafunRemediationHeartbeatCell (node simeon, cron 31 * * * *, healthz 13091). `fire`
  runs ONE deterministic remediation heartbeat (ADR-2606211712 / pattern 2606091000):

      load the forest stands → run the 花粉撲滅 remediation gate → APPEND the verdict
      datoms as ONE content-addressed tx to the actor-local kotoba commit-DAG, but ONLY
      when they CHANGE (idempotent-by-content; an unchanged beat is a no-op) → resume-safe.

  NO external I/O in the cell, NO held key (no-server-key): it appends to a LOCAL ledger
  only. ASSESSMENT-ONLY — kafun never cuts and never plants (G5). The returned summary is
  aggregate-only (the route tally + head CID), never a per-person / per-parcel datum (G2).
  Live forestry, the Murakumo digest narration (--live), and any live-engine bridge remain
  operator/Council-gated."
  (:require [kafun.methods.kafun-edn :as ke]
            [kafun.methods.autorun :as autorun]
            [kafun.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn- actor-dir
     "20-actors/kafun, resolved from this namespace's classpath location (runs from any cwd)."
     []
     (-> (io/resource "kafun/cell.cljc") io/file .getParentFile)))

#?(:clj
   (def log-default
     (delay (io/file (actor-dir) "data" "persisted" "kafun.remediation.kotoba.edn"))))

#?(:clj
   (defn fire
     "One remediation heartbeat. Idempotent per log state (cycle derives from log length);
     an unchanged assessment is a no-op (`:appended false :reason :no-change`)."
     ([] (fire nil))
     ([log-path]
      (let [seed   (str (io/file (actor-dir) "kotoba" "seed.edn"))
            target (str (or log-path @log-default))
            stands (ke/stands seed)
            n      (count (k/read-log target))
            r (autorun/beat {:stands stands :log-path target
                             :tx-id (str "kafun-" n) :as-of (str "as-of:" n)})]
        (println (str "KafunRemediationHeartbeatCell cycle " n
                      ": verdicts=" (pr-str (:verdicts r))
                      " appended=" (:appended r) (when (:reason r) (str " (" (name (:reason r)) ")"))
                      " head=" (some-> (:head r) (subs 0 (min 16 (count (:head r)))))))
        r))))
