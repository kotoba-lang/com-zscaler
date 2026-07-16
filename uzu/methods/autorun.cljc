#!/usr/bin/env bb
;; uzu 渦 — autonomous heartbeat: live the organisms + measure the field → append to the log.
(ns uzu.methods.autorun
  "autorun.cljc — uzu 渦 deterministic heartbeat (ADR-2606211500).

  One beat: load the seed, run every organism across the world tape (metabolism.cljc),
  measure the real-world energy field (measure.cljc), and APPEND the organism + flow
  datoms as one content-addressed transaction to the append-only information log
  (kotoba.cljc). prev-cid chaining keeps the log tamper-evident + resume-safe.

  Deterministic by construction: the tape IS the world (no Math/random) and the caller
  supplies tx-id + as-of (no wall clock). IDEMPOTENT-BY-CONTENT: a beat whose datoms equal
  the previous beat's is a NO-OP (nothing appended) — the log records CHANGES, not a
  liveness tick. No-server-key: appends to a local file only, no network I/O."
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.metabolism :as metab]
            [uzu.methods.measure :as measure]
            [uzu.methods.digest :as digest]
            [uzu.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn assess
  "Pure: run all organisms across the tape, measure the field, and SELF-REFLECT (the colony
  digest). The persisted datoms carry organism beats + measured flows + the colony digest,
  so the autonomous heartbeat is self-aware of its own state each beat. Returns
  {:lives [summary…] :field … :digest … :datoms […]}."
  [{:keys [tape organisms flows edges]}]
  (let [lives (mapv (fn [o] (metab/live o tape)) organisms)
        field (measure/field {:flows flows :edges edges})
        dg (digest/colony lives field)
        org-datoms (vec (mapcat metab/datoms lives))
        flow-datoms (measure/datoms flows)
        digest-datoms (digest/datoms dg)]
    {:lives (mapv metab/summary lives)
     :field field
     :digest dg
     :datoms (vec (concat org-datoms flow-datoms digest-datoms))}))

(defn beat
  "Run one heartbeat over the classified seed. opts:
     :seed     classified seed {:tape :organisms :flows :edges} (required)
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path information-log path (required)
   IDEMPOTENT-BY-CONTENT: a beat whose datoms equal the last beat's is a NO-OP.
   Returns {:head <cid> :count <n> :lives […] :totals … :appended <bool> :reason <kw|nil>}."
  [{:keys [seed tx-id as-of log-path]}]
  (let [{:keys [lives field datoms digest]} (assess seed)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        unchanged? (= datoms last-ds)
        base {:count (count datoms) :lives lives :totals (:totals field)
              :closed? (:closed? field) :digest digest}]
    (if unchanged?
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx datoms tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed-path (or (first args) "20-actors/uzu/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "uzu.information.kotoba.edn") str))
           seed (ue/classify (ue/load-edn seed-path))
           r (beat {:seed seed :tx-id "uzu-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "information-log head=" (:head r) " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println "── organisms (energy ledger; conserved) ──")
       (doseq [s (:lives r)]
         (println (format "  %-7s alive=%-5s final-energy=%7.3f lifespan=%2d/%2d belief=%s actions=%s"
                          (:id s) (str (:alive? s)) (:final-energy s)
                          (:lifespan s) (:beats s) (str (:final-belief-of s)) (str (:actions s)))))
       (println "── measured field (information; per-class, NEVER cross-summed) ──")
       (doseq [[cls t] (:totals r)]
         (println (format "  %-14s total=%.3e %-7s (n=%d)" (str cls) (:total t) (:unit t) (:n t))))
       (println (str "circulation closed? " (:closed? r)))
       (println "── colony self-reflection (digest) ──")
       (println (digest/report (:digest r)))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
