#!/usr/bin/env bb
;; itonami 営み — autonomous heartbeat: load the factory-ops log → append GROUND datoms.
(ns itonami.methods.autorun
  "autorun.cljc — itonami 営み deterministic heartbeat (ADR-2606082300, inochi/tate pattern).

  One beat: load the factory-operations seed log, build the GROUND EAVT datoms (:station/*
  node datoms + :tick/* scan-cycle observations), and APPEND them as ONE content-addressed
  transaction to the append-only FACTORY-OPS LEDGER (kotoba.cljc). prev-cid chaining keeps
  the ledger tamper-evident + resume-safe.

  GROUND ONLY: per-station OEE/energy/quality KPIs + routed findings are DERIVED (computed on
  read, G3 — aggregates of the disclosed ticks, not facts) and are NEVER persisted.
  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock); stations
  emit in insertion order + ticks content-stable (tick.<sid>.<t>) → resume-safe.
  IDEMPOTENT-BY-CONTENT: a beat whose ground datoms equal the previous beat's is a NO-OP.
  No-server-key: appends to a local file only, no network I/O. G1: observations only, never a
  line write-back."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            [itonami.methods.datom-emit :as de]
            [itonami.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

(defn- station-order [stations]
  (or (:itonami.methods.analyze/order (meta stations)) (keys stations)))

(defn- strip-colon [s] (if (str/starts-with? s ":") (subs s 1) s))

(defn ground-datoms-from
  "Build the durable GROUND EAVT datoms from a loaded {:stations :ticks} ops log, in the
  canonical [\":db/add\" e a v] form. Derived KPIs/findings excluded by design (G3)."
  [{:keys [stations ticks]}]
  (let [out (transient [])]
    ;; ── station node datoms (insertion order)
    (doseq [sid (station-order stations)]
      (let [st (get stations sid)]
        (doseq [a de/station-attrs :when (and (contains? st a) (some? (get st a)))]
          (conj! out (k/add sid a (get st a))))))
    ;; ── scan-cycle tick datoms (eid = tick.<sid>.<t>, content-stable; mirrors datom_emit)
    (doseq [tk ticks]
      (let [sid (get tk ":tick/station") t (get tk ":tick/t")
            eid (str "tick." (strip-colon (str sid)) "." t)]
        (doseq [a de/tick-attrs :when (and (contains? tk a) (some? (get tk a)))]
          (conj! out (k/add eid a (get tk a))))))
    (persistent! out)))

#?(:clj
   (def ^:private here-dir
     (-> *file* io/file .getCanonicalFile .getParentFile .getParentFile)))

#?(:clj
   (defn default-seed-path []
     (str (io/file here-dir "data" "seed-factory-ops.kotoba.edn"))))

#?(:clj
   (defn ground-datoms
     ([] (ground-datoms (default-seed-path)))
     ([seed-path] (ground-datoms-from (analyze/load-file* seed-path)))))

(defn beat
  "Run one heartbeat. opts:
     :datoms   ground datoms (optional; defaults to (ground-datoms))
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path factory-ops-ledger path (required)
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
                        (str (io/file here-dir "data" "persisted" "itonami.factory-ops.kotoba.edn")))
           r (beat {:tx-id "itonami-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "factory-ops ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
