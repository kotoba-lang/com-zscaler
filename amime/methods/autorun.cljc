#!/usr/bin/env bb
;; amime 網目 — autonomous heartbeat: solve mesh → append resilience datoms to the ledger.
(ns amime.methods.autorun
  "autorun.cljc — amime 網目 deterministic heartbeat (ADR-2606212020).

  One beat: load the mesh seed, solve the multi-site flow + N-1 contingency, and APPEND the
  resilience datoms as one content-addressed transaction to the append-only mesh-resilience
  ledger (kotoba.cljc). prev-cid chaining keeps the ledger tamper-evident + resume-safe.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock, no
  Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose datoms equal the previous
  beat's is a NO-OP (nothing appended) — the ledger records CHANGES, not a liveness tick, so a
  30-min loop over a static mesh never bloats the chain. No-server-key: local file only, no
  network. SIM ONLY — amime never dispatches; hikari actuates under Council gate."
  (:require [amime.methods.mesh :as mesh]
            [amime.methods.emit :as emit]
            [amime.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :seed     {:sites [...] :links [...]} (required)
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path ledger path (required)
   IDEMPOTENT-BY-CONTENT: identical datoms → NO-OP (nothing appended).
   Returns {:head <cid> :count <n> :served-fraction <f> :critical-link <id|nil>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [seed tx-id as-of log-path]}]
  (let [sln (mesh/solve seed)
        crit (first (mesh/contingency seed))
        ds (emit/datoms seed)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds)
              :served-fraction (:served-fraction sln)
              :critical-link (when crit (name (:link crit)))}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/amime/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "amime.mesh.kotoba.edn") str))
           seed (mesh/classify (edn/read-string (slurp seed-path)))
           r (beat {:seed seed :tx-id "amime-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "mesh-resilience ledger head=" (:head r)
                     " datoms=" (:count r)
                     " served=" (format "%.1f%%" (* 100.0 (double (:served-fraction r))))
                     " critical-link=" (:critical-link r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
