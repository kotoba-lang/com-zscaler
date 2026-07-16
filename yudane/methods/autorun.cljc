#!/usr/bin/env bb
;; 委 yudane — autonomous heartbeat: translate → append consent verdicts to the ledger.
(ns yudane.methods.autorun
  "autorun.cljc — 委 yudane deterministic, idempotent-by-content heartbeat
  (Energy Order Protocol).

  One beat: load the consented-intention seed, run the consent verdicts, and APPEND the
  derived (content-free) datoms as one content-addressed transaction to the append-only
  INTENTION LEDGER (kotoba.cljc). prev-cid chaining keeps it tamper-evident.

  Deterministic (caller supplies tx-id + as-of). IDEMPOTENT-BY-CONTENT: an unchanged
  beat is a NO-OP. No-server-key: appends to a local file only. Content-free — the
  ledger never becomes a who-intends-what registry."
  (:require [yudane.methods.analyze :as a]
            [yudane.methods.kotoba :as k]
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts: :offers :tx-id :as-of :log-path (all required)."
  [{:keys [offers tx-id as-of log-path]}]
  (let [assessment (a/analyze offers)
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :offers (get-in assessment ["totals" "offer_count"])
              :consented (get-in assessment ["totals" "consented_count"])
              :flex-kwh (get-in assessment ["totals" "consented_flex_kwh"])}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/yudane/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "yudane.intention.kotoba.edn") str))
           offers (vec (filter #(= (:type %) :offer) (edn/read-string (slurp seed))))
           r (beat {:offers offers :tx-id "yudane-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "intention ledger head=" (:head r)
                     " datoms=" (:count r)
                     " offers=" (:offers r) " consented=" (:consented r)
                     " flex-kwh=" (:flex-kwh r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
