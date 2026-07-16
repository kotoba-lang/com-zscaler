#!/usr/bin/env bb
;; chigiri 契 — autonomous heartbeat: load referral registry → append GROUND datoms.
(ns chigiri.methods.autorun
  "autorun.cljc — chigiri 契 deterministic heartbeat (ADR-2605262700, tate/hinagata pattern).

  One beat: load the worldwide legal-aid REFERRAL registry, build the GROUND EAVT datoms
  (one entity per referral body + its disclosed wayfinding attributes), and APPEND them as
  ONE content-addressed transaction to the append-only REFERRAL-REGISTRY LEDGER
  (kotoba.cljc). prev-cid chaining keeps the ledger tamper-evident + resume-safe.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose ground datoms
  equal the previous beat's is a NO-OP (nothing appended) — the ledger records registry
  CHANGES (a new body, a re-verification), not a liveness tick. No-server-key: appends to
  a local file only, no network I/O.

  G8/G14: the ledger records a REFERRAL registry (routing to LICENSED human counsel),
  NEVER advice; :verification-status is preserved verbatim (all seed entries
  :unverified-seed — a beat never upgrades verification)."
  (:require [chigiri.methods.registry :as reg]
            [chigiri.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

(defn ground-datoms-from
  "Vector of referral maps → durable GROUND EAVT datoms in canonical [\":db/add\" e a v] form."
  [referrals]
  (let [out (transient [])]
    (doseq [r referrals]
      (let [eid (reg/entity-id (get r ":referral/id"))]
        (doseq [a reg/referral-attrs :when (and (contains? r a) (some? (get r a)))]
          (conj! out (k/add eid a (get r a))))))
    (persistent! out)))

#?(:clj
   (defn ground-datoms
     ([] (ground-datoms-from (reg/load-referrals)))
     ([seed-path] (ground-datoms-from (reg/load-referrals seed-path)))))

(defn beat
  "Run one heartbeat. opts:
     :datoms   ground datoms (optional; defaults to (ground-datoms))
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path referral-registry-ledger path (required)
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
   (def ^:private here-dir
     (-> *file* io/file .getCanonicalFile .getParentFile .getParentFile)))

#?(:clj
   (defn -main [& args]
     (let [log-path (or (first args)
                        (str (io/file here-dir "data" "persisted" "chigiri.referral-registry.kotoba.edn")))
           r (beat {:tx-id "chigiri-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "referral-registry ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
