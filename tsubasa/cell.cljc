#!/usr/bin/env bb
;; tsubasa 翼 cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).
(ns tsubasa.cell
  "tsubasa 翼 cell — registered in 50-infra/cluster/murakumo/cell-runner/cells.edn as
  TsubasaHeartbeatCell (node gad, cron 27 * * * *, healthz 13090). `fire` runs ONE
  deterministic, idempotent-by-content heartbeat (ADR-2606072802 §R3 / pattern 2606091000):

      load the fare seed → analyze (per-route carrier concentration → :opening) → append ONE
      content-addressed tx to the actor-local kotoba commit-DAG (no-op when unchanged) → verify.

  NO external I/O in the cell — the autonomous public fetch (`fetch.cljc`) and the LIVE-engine
  bridge (`kotoba_bridge`, TSUBASA_KOTOBA_LIVE + operator DID / member CACAO leash) stay
  operator/consent-gated. The returned summary is aggregate-only (G1): route/carrier counts +
  the head CID, never a per-person datum."
  (:require [tsubasa.methods.autorun :as autorun]
            [tsubasa.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn- actor-dir
     "20-actors/tsubasa, resolved from this namespace's classpath location (runs from any cwd)."
     []
     (-> (io/resource "tsubasa/cell.cljc") io/file .getParentFile)))

#?(:clj
   (defn fire
     "One heartbeat. Idempotent per log state (an unchanged beat appends nothing)."
     ([] (fire nil))
     ([log-path]
      (let [dir (actor-dir)
            seed (str (io/file dir "data" "seed-fares.kotoba.edn"))
            target (str (or log-path (io/file dir "data" "persisted" "tsubasa.observations.kotoba.edn")))
            rows (vec (edn/read-string (slurp seed)))
            n (count (k/read-log target))
            r (autorun/beat {:rows rows :tx-id (str "tsubasa-" n) :as-of (str "as-of:" n)
                             :log-path target})]
        (println (str "TsubasaHeartbeatCell cycle " n ": routes=" (:routes r)
                      " carriers=" (:carriers r) " datoms=" (:count r)
                      " appended=" (:appended r) (when (:reason r) (str " (" (name (:reason r)) ")"))
                      " head=" (:head r)))
        r))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (fire)))
