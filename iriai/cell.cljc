#!/usr/bin/env bb
;; iriai 入会 — fleet heartbeat cell. ADR-2606280900 (fleet residency).
(ns iriai.cell
  "iriai 入会 — `fire` runs ONE deterministic, idempotent-by-content commons beat
  (infra + 資金 + 管理 + 物理 twin + 運用 maintain → append to the commons ledger).

  Registered in 50-infra/cluster/murakumo/cell-runner/cells.edn as
  IriaiCommonsHeartbeatCell (node judah, cron 44 * * * *, healthz 13093) — the
  kaname/kafun/mimamori heartbeat track. No-server-key, no external I/O: it loads the
  actor's own seed and appends to the LOCAL append-only kotoba commit-DAG. An unchanged
  assessment is a NO-OP (:appended false :reason :no-change). ASSESSMENT/SIM ONLY (G5) —
  iriai never produces, actuates, or dispatches; the Murakumo digest + live-engine bridge
  stay operator/Council-gated."
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.autorun :as autorun]
            #?(:clj [clojure.java.io :as io])))

(def ^:private seed-path "20-actors/iriai/kotoba/seed.edn")

#?(:clj
   (defn- default-log []
     (-> (io/file *file*) .getParentFile
         (io/file "data" "persisted" "iriai.commons.kotoba.edn") str)))

(defn fire
  "Cell entry — run one commons heartbeat beat. opts (all optional):
     :seed-path  seed EDN path (default 20-actors/iriai/kotoba/seed.edn)
     :log-path   ledger path (default data/persisted/iriai.commons.kotoba.edn)
     :tx-id :as-of  deterministic stamps (default fixed strings → idempotent-by-content)
   Returns a compact status map for the cell-runner."
  [& [opts]]
  #?(:clj
     (let [{:keys [seed-path log-path tx-id as-of]
            :or {seed-path seed-path}} (or opts {})
           rows   (ie/parse-edn (slurp seed-path))
           cells  (vec (filter #(= (:type %) :lifeline-cell) rows))
           assets (vec (filter #(= (:type %) :asset) rows))
           log    (or log-path (default-log))
           r (autorun/beat {:cells cells :assets assets
                            :tx-id (or tx-id "iriai-cell-beat")
                            :as-of (or as-of "cell") :log-path log})]
       {:cell "IriaiCommonsHeartbeatCell"
        :head (:head r) :datoms (:count r) :appended (:appended r)
        :reason (:reason r) :infra (:infra r) :fund (:fund r) :gov (:gov r)
        :twin (:twin r) :maint (:maint r)
        :server-held-key false})
     :default (throw (ex-info "fire is :clj-only" {}))))

#?(:clj
   (defn -main [& _]
     (let [r (fire {})]
       (println (str "IriaiCommonsHeartbeatCell fired — head=" (:head r)
                     " datoms=" (:datoms r) " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "infra=" (:infra r) " fund=" (:fund r) " gov=" (:gov r)
                     " twin=" (:twin r) " maint=" (:maint r))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
