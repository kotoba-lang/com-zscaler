#!/usr/bin/env bb
;; 樋 toi — cell entry (kotodama cell-runner contract, ADR-2605192415 §7.1).
(ns toi.cell
  "樋 toi cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).
  Registered in cell-runner cells.edn as ToiComputeRoutingHeartbeatCell. One
  heartbeat: route deferrable compute to favourable sites and append the routing
  datoms to the local append-only routing ledger. Idempotent per log state.
  No-server-key, offline I/O only — a routing map, never a forced job-kill."
  (:require [toi.methods.autorun :as autorun]
            [toi.methods.toi-edn :as edn]
            [toi.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (defn- actor-dir [] (-> (io/resource "toi/cell.cljc") io/file .getParentFile)))
#?(:clj (def ^:private log-default
          (delay (str (io/file (actor-dir) "data" "persisted" "toi.routings.kotoba.edn")))))

#?(:clj
   (defn fire
     ([] (fire nil))
     ([log-path]
      (let [target (or log-path @log-default)
            seed (str (io/file (actor-dir) "kotoba" "seed.edn"))
            cycle (count (k/read-log target))
            r (autorun/beat {:jobs (edn/jobs seed) :sites (edn/sites seed)
                             :tx-id (str "toi-beat-" cycle) :as-of (str "cycle-" cycle)
                             :log-path target})]
        (println (str "ToiComputeRoutingHeartbeatCell cycle " cycle ": "
                      (:routed r) " routed, " (:avoided-carbon-kg r) " kgCO2 avoided, "
                      (:in-place r) " in-place, appended=" (:appended r)))
        r))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (fire)))
