#!/usr/bin/env bb
;; 燠 okibi — cell entry (kotodama cell-runner contract, ADR-2605192415 §7.1).
(ns okibi.cell
  "燠 okibi cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).
  Registered in cell-runner cells.edn as OkibiThermalMatchHeartbeatCell. One
  heartbeat: match waste-heat sources to demand sinks (temperature cascade +
  distance) and append the match datoms to the local append-only thermal ledger.
  Idempotent per log state. No-server-key, offline I/O only — a matching map,
  never a dispatch order."
  (:require [okibi.methods.autorun :as autorun]
            [okibi.methods.okibi-edn :as edn]
            [okibi.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (defn- actor-dir [] (-> (io/resource "okibi/cell.cljc") io/file .getParentFile)))
#?(:clj (def ^:private log-default
          (delay (str (io/file (actor-dir) "data" "persisted" "okibi.matches.kotoba.edn")))))

#?(:clj
   (defn fire
     ([] (fire nil))
     ([log-path]
      (let [target (or log-path @log-default)
            seed (str (io/file (actor-dir) "kotoba" "seed.edn"))
            cycle (count (k/read-log target))
            r (autorun/beat {:sources (edn/sources seed) :sinks (edn/sinks seed)
                             :tx-id (str "okibi-beat-" cycle) :as-of (str "cycle-" cycle)
                             :log-path target})]
        (println (str "OkibiThermalMatchHeartbeatCell cycle " cycle ": "
                      (:matches r) " matches, " (:matched-kw r) " kW matched, "
                      (:unmatched-demand-kw r) " kW unmet, appended=" (:appended r)))
        r))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (fire)))
