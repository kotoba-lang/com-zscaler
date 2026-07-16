#!/usr/bin/env bb
;; junkan 循環 — autonomous heartbeat: analyze → append findings to the local ledger.
(ns junkan.methods.autorun
  "autorun.cljc — junkan 循環 deterministic heartbeat (ADR-2605290927).

  One beat: load the global governance-asymmetry instruments, run the analysis-only
  system-dynamics read-off (analyze.cljc), and APPEND the findings datoms as one
  content-addressed transaction to the append-only LOCAL findings ledger (kotoba.cljc).
  prev-cid chaining keeps the ledger tamper-evident + resume-safe.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose findings datoms
  equal the previous beat's is a NO-OP (nothing appended) — the ledger records
  CHANGES (a new instrument, a flipped regime), not a wall-clock liveness tick, so a
  30-min loop over a static seed never bloats the chain with identical snapshots.

  G4 ANALYSIS-ONLY: no-server-key — appends to a local file only, no network I/O,
  no outward/dispatch path (enforced by absence). junkan never touches."
  (:require [junkan.methods.junkan-edn :as je]
            [junkan.methods.analyze :as az]
            [junkan.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :instruments  vector of instrument maps (required)
     :tx-id        deterministic tx id (required)
     :as-of        deterministic as-of stamp (required)
     :log-path     ledger path (required)
   IDEMPOTENT-BY-CONTENT: if the new findings datoms equal the last beat's datoms,
   the beat is a NO-OP — nothing is appended.
   Returns {:head <cid> :count <n> :regimes <map> :appended <bool> :reason <kw|nil>}."
  [{:keys [instruments tx-id as-of log-path]}]
  (let [analysis (az/analyze instruments)
        ds (az/datoms instruments analysis)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        regimes (into {} (map (fn [[s sp]] [s (name (:regime sp))]) (get analysis "stocks")))
        base {:count (count ds) :regimes regimes
              :instruments (count instruments)
              :jurisdictions (get-in analysis ["coverage" :jurisdictions])}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "junkan.governance.kotoba.edn") str))
           instruments (vec (filter #(= (:type %) :instrument) (edn/read-string (slurp seed))))
           r (beat {:instruments instruments
                    :tx-id "junkan-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "findings ledger head=" (:head r)
                     " datoms=" (:count r)
                     " instruments=" (:instruments r)
                     " jurisdictions=" (:jurisdictions r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "stock regimes=" (:regimes r)))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
