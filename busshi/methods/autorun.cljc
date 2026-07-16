#!/usr/bin/env bb
;; busshi 物資 — autonomous heartbeat: analyze → append observations to the ledger.
(ns busshi.methods.autorun
  "autorun.cljc — busshi 物資 deterministic, idempotent-by-content heartbeat
  (ADR-2606171000).

  One beat: load the commodity/materials seed, run the §2(l) resilience analysis,
  and APPEND the derived-observation datoms as one content-addressed transaction to
  the append-only OBSERVATION LEDGER (kotoba.cljc). prev-cid chaining keeps the
  ledger tamper-evident + resume-safe.

  Deterministic: the caller supplies tx-id + as-of (no wall clock, no Math/random).
  IDEMPOTENT-BY-CONTENT: a beat whose observation datoms equal the previous beat's
  is a NO-OP (nothing appended) — the ledger records CHANGES, not a wall-clock tick,
  so a recurring loop over a static seed never bloats the chain. No-server-key:
  appends to a local file only, no network I/O. OBSERVATION ONLY — busshi never
  trades, never extracts."
  (:require [busshi.methods.analyze :as a]
            [busshi.methods.busshi-edn :as be]
            [busshi.methods.kotoba :as k]))

(defn beat
  "Run one heartbeat. opts:
     :commodities  vector of commodity maps (required)
     :tx-id        deterministic tx id (required)
     :as-of        deterministic as-of stamp (required)
     :log-path     ledger path (required)
   Returns {:head <cid> :count <n> :commodities <n> :classes <n>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [commodities tx-id as-of log-path]}]
  (let [assessment (a/analyze commodities)
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :commodities (count (get assessment "commodities"))
              :classes (count (get assessment "classes"))}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/busshi/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "busshi.observations.kotoba.edn") str))
           commodities (be/commodities seed)
           r (beat {:commodities commodities :tx-id "busshi-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "observation ledger head=" (:head r)
                     " datoms=" (:count r)
                     " commodities=" (:commodities r) " classes=" (:classes r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
