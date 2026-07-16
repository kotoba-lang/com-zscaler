#!/usr/bin/env bb
;; tate 盾 — autonomous heartbeat: load registries + member docs → append GROUND datoms.
(ns tate.methods.autorun
  "autorun.cljc — tate 盾 deterministic heartbeat (on ADR-2605312345, ugachi pattern).

  One beat: load the coded clause-pattern + procedure registries and the member's
  own docs/notices (synthetic at R0, G1), build the GROUND EAVT datoms, and APPEND
  them as ONE content-addressed transaction to the append-only DEFENSE LEDGER
  (kotoba.cljc). prev-cid chaining keeps the ledger tamper-evident + resume-safe.

  GROUND ONLY: clause flags + plan status are DERIVED (computed on read,
  :bond/is-transient, G2) and are NEVER persisted — the ledger holds durable facts
  only, so a clause-pattern registry update appends, but a re-scan of the same docs
  does not.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose ground datoms
  equal the previous beat's is a NO-OP (nothing appended) — the ledger records
  CHANGES, not a liveness tick, so a 30-min loop over a static seed never bloats the
  chain with identical snapshots. No-server-key: appends to a local file only, no
  network I/O. tate never gives advice and never represents (G2/G3)."
  (:require [tate.methods.terms-scan :as terms]
            [tate.methods.respond-plan :as respond]
            [tate.methods.datom-emit :as de]
            [tate.methods.kotoba :as k]))

(defn ground-datoms
  "Build the durable GROUND EAVT datoms (registries + member docs/notices) in the
  canonical [\":db/add\" e a v] form. Derived flags/plans are excluded by design (G2)."
  []
  (let [[docs notices] (terms/load-docs)
        patterns (terms/load-patterns)
        procs (respond/load-procs)
        out (transient [])]
    ;; ── coded registries (disclosed shapes)
    (doseq [p patterns a de/clause-attrs :when (contains? p a)]
      (conj! out (k/add (get p ":clause/id") a (get p a))))
    (doseq [p procs a de/proc-attrs :when (contains? p a)]
      (conj! out (k/add (get p ":proc/id") a (get p a))))
    ;; ── member docs/notices (synthetic at R0 — G1)
    (doseq [d docs a de/doc-attrs :when (contains? d a)]
      (conj! out (k/add (get d ":doc/id") a (get d a))))
    (doseq [n notices a de/notice-attrs :when (contains? n a)]
      (conj! out (k/add (get n ":notice/id") a (get n a))))
    (persistent! out)))

(defn beat
  "Run one heartbeat. opts:
     :datoms   ground datoms (optional; defaults to (ground-datoms))
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path defense-ledger path (required)
   IDEMPOTENT-BY-CONTENT: if the new ground datoms equal the last beat's datoms,
   the beat is a NO-OP — nothing is appended.
   Returns {:head <cid> :count <n> :appended <bool> :reason <kw|nil>}."
  [{:keys [datoms tx-id as-of log-path]}]
  (let [ds (or datoms (ground-datoms))
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= ds last-ds)
        base {:count (count ds)}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [log-path (or (first args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "tate.defense-ledger.kotoba.edn") str))
           ;; deterministic stamps for a manual run (override via real scheduler in R2+)
           r (beat {:tx-id "tate-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "defense ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
