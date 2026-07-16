(ns hirameki.methods.autorun
  "hirameki 閃き — deterministic, idempotent-by-content heartbeat.

  One beat: load the patent seed, run the release analysis, APPEND the derived
  release-observation datoms as ONE content-addressed tx to the append-only ledger.
  prev-cid chaining keeps the ledger tamper-evident + resume-safe. IDEMPOTENT-BY-CONTENT:
  a beat whose observation datoms equal the previous beat's is a NO-OP — the ledger records
  CHANGES, not a wall-clock tick, so a recurring loop over a static seed never bloats the
  chain. Deterministic: caller supplies tx-id + as-of (no wall clock, no Math/random) →
  resume-safe. No-server-key: appends to a local file only, no network I/O.
  OBSERVATION ONLY — hirameki never files, never litigates, never trades. ADR-2606212200."
  (:require [hirameki.methods.analyze :as a]
            [hirameki.methods.kotoba :as k]
            #?(:clj [hirameki.methods.hirameki-edn :as he])
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.edn :as edn])))

(defn beat
  "Run one heartbeat. opts:
     :rows      seed rows (flat vector or {:fields :patents}) (required)
     :ref-year  deterministic release-clock reference year (optional)
     :tx-id     deterministic tx id (required)
     :as-of     deterministic as-of stamp (required)
     :log-path  ledger path (required)
   Returns {:head <cid> :count <n> :fields <n> :patents <n> :sections <n>
            :appended <bool> :reason <kw|nil>}."
  [{:keys [rows ref-year tx-id as-of log-path]}]
  (let [assessment (if ref-year (a/analyze rows ref-year) (a/analyze rows))
        ds (a/datoms assessment)
        prev (k/head-cid log-path)
        last-ds (let [txs (k/read-log log-path)]
                  (when (seq txs) (get (last txs) ":tx/datoms")))
        base {:count (count ds)
              :fields (count (get assessment "fields"))
              :patents (count (get assessment "patents"))
              :sections (count (get assessment "sections"))}]
    (if (= ds last-ds)
      (assoc base :head prev :appended false :reason :no-change)
      (let [tx (k/make-tx ds tx-id as-of prev)
            head (k/append-tx tx log-path)]
        (assoc base :head head :appended true :reason nil)))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/hirameki/kotoba/seed.edn")
           log-path (or (second args)
                        (-> (io/file *file*) .getParentFile .getParentFile
                            (io/file "data" "persisted" "hirameki.observations.kotoba.edn") str))
           rows (he/classify (he/load-edn seed))
           r (beat {:rows rows :tx-id "hirameki-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "observation ledger head=" (:head r)
                     " datoms=" (:count r)
                     " fields=" (:fields r) " patents=" (:patents r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
