#!/usr/bin/env bb
;; kafun 花粉 — autonomous heartbeat: assess → append verdicts to the remediation ledger.
(ns kafun.methods.autorun
  "autorun.cljc — kafun 花粉 deterministic heartbeat (ADR-2606211712).

  One beat: load the forest stands, run the 花粉撲滅 remediation gate, and APPEND
  the verdict datoms as one content-addressed transaction to the append-only
  remediation ledger (kotoba.cljc). prev-cid chaining keeps the ledger tamper-evident
  + resume-safe — this is the 持続永続化 leg.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose verdict datoms
  equal the previous beat's is a NO-OP (nothing appended) — the ledger records
  CHANGES, not a wall-clock liveness tick, so a 6-hour loop over a static seed never
  bloats the chain with identical snapshots. No-server-key: appends to a local file
  only, no network I/O. ASSESSMENT ONLY — kafun never cuts and never plants."
  (:require [kafun.methods.remediate :as rem]
            [kafun.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :stands    vector of stand maps (required)
     :tx-id     deterministic tx id (required)
     :as-of     deterministic as-of stamp (required)
     :log-path  ledger path (required)
   IDEMPOTENT-BY-CONTENT: if the new verdict datoms equal the last beat's datoms,
   the beat is a NO-OP — nothing is appended (the ledger records CHANGES, not a
   liveness tick, so it never bloats with identical snapshots).
   Returns {:head <cid> :count <n> :verdicts <tally>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [stands tx-id as-of log-path]}]
  (let [assessment (rem/assess stands)
        ds (rem/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds) :verdicts (get assessment "tally")}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/kafun/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "kafun.remediation.kotoba.edn") str))
           stands (vec (filter #(= (:type %) :stand)
                               (edn/read-string (slurp seed))))
           ;; deterministic stamps for a manual run (override via real scheduler in R1+)
           r (beat {:stands stands
                    :tx-id "kafun-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "remediation ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "verdicts=" (:verdicts r)))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
