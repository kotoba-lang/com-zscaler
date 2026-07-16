#!/usr/bin/env bb
;; 澪 mio — cell entry (kotodama cell-runner contract, ADR-2605192415 §7.1).
(ns mio.cell
  "澪 mio cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).
  Registered in 50-infra/cluster/murakumo/cell-runner/cells.edn as
  MioVerificationHeartbeatCell. One heartbeat: verify the suite's flow-improvement
  claims (§9) and append the verdict datoms to the local append-only verification
  ledger. Idempotent per log state (cycle derives from log length). No-server-key,
  offline I/O only — a record of VERIFIED ORDERED FLOW, never a market signal."
  (:require [mio.methods.autorun :as autorun]
            [mio.methods.mio-edn :as edn]
            [mio.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn- actor-dir
     "20-actors/mio, resolved from this namespace's classpath location so the cell
     runs from any cwd (the cell-runner's contract)."
     []
     (-> (io/resource "mio/cell.cljc") io/file .getParentFile)))

#?(:clj
   (def ^:private log-default
     (delay (str (io/file (actor-dir) "data" "persisted" "mio.verifications.kotoba.edn")))))

#?(:clj
   (defn fire
     "One heartbeat. Idempotent per log state (cycle derives from log length)."
     ([] (fire nil))
     ([log-path]
      (let [target (or log-path @log-default)
            seed (str (io/file (actor-dir) "kotoba" "seed.edn"))
            claims (edn/claims seed)
            cycle (count (k/read-log target))
            r (autorun/beat {:claims claims
                             :tx-id (str "mio-beat-" cycle) :as-of (str "cycle-" cycle)
                             :log-path target})]
        (println (str "MioVerificationHeartbeatCell cycle " cycle ": "
                      (:verified r) "/" (:claims r) " verified, Flowrate "
                      (:flowrate r) " kWh-equiv, appended=" (:appended r)
                      " → " (when (:head r) (subs (:head r) 0 (min 16 (count (:head r))))) "…"))
        r))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (fire)))
