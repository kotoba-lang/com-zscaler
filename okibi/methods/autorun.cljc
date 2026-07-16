#!/usr/bin/env bb
;; 燠 okibi — autonomous heartbeat: match → append thermal matches to the ledger.
(ns okibi.methods.autorun
  "autorun.cljc — 燠 okibi deterministic, idempotent-by-content heartbeat
  (Energy Order Protocol).

  One beat: load the thermal seed (sources + sinks), run the matching, and APPEND
  the derived match datoms as one content-addressed transaction to the append-only
  THERMAL MATCHING LEDGER (kotoba.cljc). prev-cid chaining keeps it tamper-evident.

  Deterministic (caller supplies tx-id + as-of). IDEMPOTENT-BY-CONTENT: an unchanged
  beat is a NO-OP. No-server-key: appends to a local file only. A matching map,
  never a dispatch order."
  (:require [okibi.methods.analyze :as a]
            [okibi.methods.kotoba :as k]
            #?(:clj [okibi.methods.okibi-edn :as oe])))

(defn beat
  "Run one heartbeat. opts: :sources :sinks :tx-id :as-of :log-path (all required)."
  [{:keys [sources sinks tx-id as-of log-path]}]
  (let [assessment (a/analyze sources sinks)
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :matches (get-in assessment ["totals" "match_count"])
              :matched-kw (get-in assessment ["totals" "matched_kw"])
              :unmatched-demand-kw (get-in assessment ["totals" "unmatched_demand_kw"])}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/okibi/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "okibi.matches.kotoba.edn") str))
           ;; oe/sources+oe/sinks tolerate both the legacy bare-map seed.edn
           ;; shape and the datomized tx-data shape (single reconstitution
           ;; point — see okibi.methods.okibi-edn/classify).
           sources (oe/sources seed)
           sinks (oe/sinks seed)
           r (beat {:sources sources :sinks sinks :tx-id "okibi-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "thermal ledger head=" (:head r)
                     " datoms=" (:count r)
                     " matches=" (:matches r) " matched-kw=" (:matched-kw r)
                     " unmatched-demand=" (:unmatched-demand-kw r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
