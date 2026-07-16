#!/usr/bin/env bb
;; 委 yudane — cell entry (kotodama cell-runner contract, ADR-2605192415 §7.1).
(ns yudane.cell
  "委 yudane cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).
  Registered in cell-runner cells.edn as YudaneIntentionHeartbeatCell. One
  heartbeat: run the consent verdicts over the aggregate intention offers and
  append the content-free datoms to the local append-only intention ledger.
  Idempotent per log state. No-server-key, offline I/O only — content-free,
  never a who-intends-what registry."
  (:require [yudane.methods.autorun :as autorun]
            [yudane.methods.yudane-edn :as edn]
            [yudane.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj (defn- actor-dir [] (-> (io/resource "yudane/cell.cljc") io/file .getParentFile)))
#?(:clj (def ^:private log-default
          (delay (str (io/file (actor-dir) "data" "persisted" "yudane.intention.kotoba.edn")))))

#?(:clj
   (defn fire
     ([] (fire nil))
     ([log-path]
      (let [target (or log-path @log-default)
            offers (edn/offers (str (io/file (actor-dir) "kotoba" "seed.edn")))
            cycle (count (k/read-log target))
            r (autorun/beat {:offers offers
                             :tx-id (str "yudane-beat-" cycle) :as-of (str "cycle-" cycle)
                             :log-path target})]
        (println (str "YudaneIntentionHeartbeatCell cycle " cycle ": "
                      (:consented r) "/" (:offers r) " consented, "
                      (:flex-kwh r) " kWh aggregate flex, appended=" (:appended r)))
        r))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (fire)))
