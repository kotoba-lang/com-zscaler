#!/usr/bin/env bb
;; 撓 tawami — autonomous heartbeat: analyze → append flexibility obs to the ledger.
(ns tawami.methods.autorun
  "autorun.cljc — 撓 tawami deterministic, idempotent-by-content heartbeat
  (Energy Order Protocol).

  One beat: load the flexibility-asset seed, run the Proof-of-Flexibility analysis,
  and APPEND the derived datoms as one content-addressed transaction to the
  append-only FLEXIBILITY LEDGER (kotoba.cljc). prev-cid chaining keeps the ledger
  tamper-evident + resume-safe.

  Deterministic: the caller supplies tx-id + as-of (no wall clock, no Math/random).
  IDEMPOTENT-BY-CONTENT: a beat whose datoms equal the previous beat's is a NO-OP.
  No-server-key: appends to a local file only. A flexibility map, never a dispatch."
  (:require [tawami.methods.analyze :as a]
            [tawami.methods.kotoba :as k]
            [tawami.methods.tawami-edn :as te]))

(defn beat
  "Run one heartbeat. opts: :assets :tx-id :as-of :log-path (all required).
   Returns {:head :count :assets :flex-value :fast-count :appended :reason}."
  [{:keys [assets tx-id as-of log-path]}]
  (let [assessment (a/analyze assets)
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :assets (get-in assessment ["totals" "asset_count"])
              :flex-value (get-in assessment ["totals" "total_flex_value"])
              :fast-count (get-in assessment ["totals" "fast_flex_count"])}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tawami/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "tawami.flexibility.kotoba.edn") str))
           assets (vec (filter #(= (:type %) :asset) (te/parse-edn (slurp seed))))
           r (beat {:assets assets :tx-id "tawami-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "flexibility ledger head=" (:head r)
                     " datoms=" (:count r)
                     " assets=" (:assets r) " flex-value=" (:flex-value r)
                     " fast=" (:fast-count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
