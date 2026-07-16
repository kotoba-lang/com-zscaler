#!/usr/bin/env bb
;; ugachi 穿ち — autonomous heartbeat: assess → append verdicts to the stewardship ledger.
(ns ugachi.methods.autorun
  "autorun.cljc — ugachi 穿ち deterministic heartbeat (ADR-2606170900).

  One beat: load the proposed projects, run the §2(l) gate (optionally busshi-grounded
  via the bridge), and APPEND the verdict datoms as one content-addressed transaction
  to the append-only stewardship ledger (kotoba.cljc). prev-cid chaining keeps the
  ledger tamper-evident + resume-safe.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose verdict datoms
  equal the previous beat's is a NO-OP (nothing appended) — the ledger records CHANGES,
  not a wall-clock liveness tick, so a 30-min loop over a static seed never bloats the
  chain with identical snapshots. No-server-key: appends to a local file only, no network
  I/O. ASSESSMENT ONLY — ugachi never digs."
  (:require [ugachi.methods.gate :as gate]
            [ugachi.methods.bridge :as bridge]
            [ugachi.methods.kotoba :as k]
            [ugachi.methods.ugachi-edn :as ue]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :projects     vector of project maps (required)
     :commodities  busshi commodity maps (optional; enables grounding)
     :tx-id        deterministic tx id (required)
     :as-of        deterministic as-of stamp (required)
     :log-path     ledger path (required)
   IDEMPOTENT-BY-CONTENT: if the new verdict datoms equal the last beat's datoms,
   the beat is a NO-OP — nothing is appended (the ledger records CHANGES, not a
   liveness tick, so it never bloats with identical snapshots).
   Returns {:head <cid> :count <n> :verdicts <tally> :grounded <bool>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [projects commodities tx-id as-of log-path]}]
  (let [grounded? (boolean (seq commodities))
        assessment (if grounded?
                     (bridge/ground-and-assess projects commodities)
                     (gate/assess projects))
        ds (gate/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds) :verdicts (get assessment "tally") :grounded grounded?}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [ug-seed (or (first args) "20-actors/ugachi/kotoba/seed.edn")
           bu-seed (second args) ; optional → enables grounding
           log-path (or (nth args 2 nil)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "ugachi.stewardship.kotoba.edn") str))
           projects (ue/projects ug-seed)
           commodities (when bu-seed
                         (vec (filter #(= (:type %) :commodity)
                                      (ue/normalize-rows (edn/read-string (slurp bu-seed))))))
           ;; deterministic stamps for a manual run (override via real scheduler in R2+)
           r (beat {:projects projects :commodities commodities
                    :tx-id "ugachi-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "stewardship ledger head=" (:head r)
                     " datoms=" (:count r) " grounded=" (:grounded r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "verdicts=" (:verdicts r)))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
