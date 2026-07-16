#!/usr/bin/env bb
;; 澪 mio — autonomous heartbeat: analyze → append verdicts to the ledger.
(ns mio.methods.autorun
  "autorun.cljc — 澪 mio deterministic, idempotent-by-content heartbeat
  (Energy Order Protocol backbone).

  One beat: load the flow-improvement CLAIM seed, run the Proof-of-Useful-Flow
  verification, and APPEND the derived verdict datoms as one content-addressed
  transaction to the append-only VERIFICATION LEDGER (kotoba.cljc). prev-cid
  chaining keeps the ledger tamper-evident + resume-safe.

  Deterministic: the caller supplies tx-id + as-of (no wall clock, no Math/random).
  IDEMPOTENT-BY-CONTENT: a beat whose verdict datoms equal the previous beat's is a
  NO-OP (nothing appended) — the ledger records CHANGES, not a wall-clock tick, so a
  recurring loop over a static claim set never bloats the chain. No-server-key:
  appends to a local file only, no network I/O. OBSERVATION + VERIFICATION ONLY —
  mio never trades, never actuates; reward issuance stays 1 SBT=1 vote."
  (:require [mio.methods.analyze :as a]
            [mio.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :claims    vector of claim maps (required)
     :tx-id     deterministic tx id (required)
     :as-of     deterministic as-of stamp (required)
     :log-path  ledger path (required)
   Returns {:head <cid> :count <n> :claims <n> :verified <n> :flowrate <score>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [claims tx-id as-of log-path]}]
  (let [assessment (a/analyze claims)
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :claims (get-in assessment ["totals" "total_claims"])
              :verified (get-in assessment ["totals" "verified_claims"])
              :flowrate (get-in assessment ["totals" "verified_flowrate_score"])}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/mio/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "mio.verifications.kotoba.edn") str))
           claims (vec (filter #(= (:type %) :claim) (edn/read-string (slurp seed))))
           r (beat {:claims claims :tx-id "mio-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "verification ledger head=" (:head r)
                     " datoms=" (:count r)
                     " claims=" (:claims r) " verified=" (:verified r)
                     " flowrate=" (:flowrate r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
