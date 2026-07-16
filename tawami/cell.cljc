#!/usr/bin/env bb
;; 撓 tawami — cell entry (kotodama cell-runner contract, ADR-2605192415 §7.1).
(ns tawami.cell
  "撓 tawami cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).
  Registered in cell-runner cells.edn as TawamiFlexibilityHeartbeatCell. One
  heartbeat: analyze the flexibility assets and append the derived datoms to the
  local append-only flexibility ledger. Idempotent per log state. No-server-key,
  offline I/O only — a flexibility map, never a dispatch order."
  (:require [tawami.methods.autorun :as autorun]
            [tawami.methods.tawami-edn :as edn]
            [tawami.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (defn- actor-dir [] (-> (io/resource "tawami/cell.cljc") io/file .getParentFile)))
#?(:clj (def ^:private log-default
          (delay (str (io/file (actor-dir) "data" "persisted" "tawami.flexibility.kotoba.edn")))))

#?(:clj
   (defn fire
     ([] (fire nil))
     ([log-path]
      (let [target (or log-path @log-default)
            assets (edn/assets (str (io/file (actor-dir) "kotoba" "seed.edn")))
            cycle (count (k/read-log target))
            r (autorun/beat {:assets assets
                             :tx-id (str "tawami-beat-" cycle) :as-of (str "cycle-" cycle)
                             :log-path target})]
        (println (str "TawamiFlexibilityHeartbeatCell cycle " cycle ": "
                      (:assets r) " assets, flex-value " (:flex-value r) " kWh-equiv ("
                      (:fast-count r) " fast), appended=" (:appended r)))
        r))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (fire)))
