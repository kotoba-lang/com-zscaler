#!/usr/bin/env bb
;; 樋 toi — autonomous heartbeat: route → append routings to the ledger.
(ns toi.methods.autorun
  "autorun.cljc — 樋 toi deterministic, idempotent-by-content heartbeat
  (Energy Order Protocol).

  One beat: load the compute seed (jobs + sites), run the routing, and APPEND the
  derived routing datoms as one content-addressed transaction to the append-only
  COMPUTE ROUTING LEDGER (kotoba.cljc). prev-cid chaining keeps it tamper-evident.

  Deterministic (caller supplies tx-id + as-of). IDEMPOTENT-BY-CONTENT: an unchanged
  beat is a NO-OP. No-server-key: appends to a local file only. A routing map, never
  a forced dispatch."
  (:require [toi.methods.analyze :as a]
            [toi.methods.kotoba :as k]
            #?(:clj [toi.methods.toi-edn :as te])))

(defn beat
  "Run one heartbeat. opts: :jobs :sites :tx-id :as-of :log-path (all required)."
  [{:keys [jobs sites tx-id as-of log-path]}]
  (let [assessment (a/analyze jobs sites)
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :routed (get-in assessment ["totals" "routed_count"])
              :avoided-carbon-kg (get-in assessment ["totals" "avoided_carbon_kg"])
              :in-place (get-in assessment ["totals" "in_place_count"])}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/toi/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "toi.routings.kotoba.edn") str))
           ;; te/jobs+te/sites tolerate both the legacy bare-map seed.edn
           ;; shape and the datomized tx-data shape (single reconstitution
           ;; point — see toi.methods.toi-edn/classify).
           jobs (te/jobs seed)
           sites (te/sites seed)
           r (beat {:jobs jobs :sites sites :tx-id "toi-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "routing ledger head=" (:head r)
                     " datoms=" (:count r)
                     " routed=" (:routed r) " avoided-kg=" (:avoided-carbon-kg r)
                     " in-place=" (:in-place r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
