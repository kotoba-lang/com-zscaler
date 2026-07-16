#!/usr/bin/env bb
;; tsubasa 翼 — autonomous heartbeat: analyze → append fare observations to the ledger.
(ns tsubasa.methods.autorun
  "autorun.cljc — tsubasa 翼 deterministic, idempotent-by-content heartbeat
  (ADR-2606072800).

  One beat: load the route / fare / airport / carrier seed, run the honest
  meta-search analysis (route competition + cheapest/greenest/fastest), and APPEND
  the derived-observation datoms as one content-addressed transaction to the
  append-only FARE-OBSERVATION LEDGER (kotoba.cljc). prev-cid chaining keeps the
  ledger tamper-evident + resume-safe.

  Deterministic: the caller supplies tx-id + as-of (no wall clock, no Math/random).
  IDEMPOTENT-BY-CONTENT: a beat whose observation datoms equal the previous beat's
  is a NO-OP (nothing appended) — the ledger records CHANGES, not a wall-clock tick,
  so a recurring loop over a static seed never bloats the chain. No-server-key:
  appends to a local file only, no network I/O. DISCOVERY ONLY — tsubasa takes no
  commission, never books, never tracks the searcher."
  (:require [tsubasa.methods.analyze :as a]
            [tsubasa.methods.kotoba :as k]
            #?(:clj [tsubasa.methods.kotoba-bridge :as bridge])
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :rows      vector of seed rows (:fare/:airport/:carrier) (required)
     :tx-id     deterministic tx id (required)
     :as-of     deterministic as-of stamp (required)
     :log-path  ledger path (required)
   Returns {:head <cid> :count <n> :routes <n> :carriers <n>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [rows tx-id as-of log-path]}]
  (let [analysis (a/analyze rows)
        ds (a/datoms analysis)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :routes (count (get analysis "routes"))
              :carriers (count (get analysis "carriers"))}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     ;; flags: --bridge pushes the local commit-DAG to the LIVE kotoba engine after persist
     ;; (FAIL-OPEN: engine down / operator DID absent → the beat still completes locally).
     (let [pos (vec (remove #(clojure.string/starts-with? (str %) "--") args))
           bridge? (boolean (some #{"--bridge"} args))
           seed (or (first pos) "20-actors/tsubasa/data/seed-fares.kotoba.edn")
           log-path (or (second pos)
                        (-> (clojure.java.io/file *file*) .getParentFile .getParentFile
                            (clojure.java.io/file "data" "persisted" "tsubasa.observations.kotoba.edn") str))
           rows (vec (edn/read-string (slurp seed)))
           r (beat {:rows rows :tx-id "tsubasa-beat-manual" :as-of "manual" :log-path log-path})
           br (when bridge?
                (try (let [b (bridge/push log-path {})]   ; dry-run unless TSUBASA_KOTOBA_LIVE=1
                       (select-keys b [:mode :pending :pushed :principal :datoms-confirmed]))
                     (catch Exception e {:error (.getMessage e)})))]
       (println (str "fare-observation ledger head=" (:head r)
                     " datoms=" (:count r)
                     " routes=" (:routes r) " carriers=" (:carriers r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))
                     (when bridge? (str " | bridge=" (pr-str br)))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
