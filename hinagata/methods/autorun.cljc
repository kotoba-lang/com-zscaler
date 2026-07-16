#!/usr/bin/env bb
;; hinagata 雛形 — autonomous heartbeat: load the template graph → append GROUND datoms.
(ns hinagata.methods.autorun
  "autorun.cljc — hinagata 雛形 deterministic heartbeat (ADR-2606111954, tate/ugachi pattern).

  One beat: load the legal-template-commons seed graph, build the GROUND EAVT datoms
  (template/clause/statute/jurisdiction/concept/license NODES + their :en/* 縁), and
  APPEND them as ONE content-addressed transaction to the append-only TEMPLATE-COMMONS
  LEDGER (kotoba.cljc). prev-cid chaining keeps the ledger tamper-evident + resume-safe.

  GROUND ONLY: groundedness / reusability / statute-pull are DERIVED (integral of
  incident 縁, computed on read, N1/G2) and are NEVER persisted — the ledger holds
  durable public facts only, so a seed edit appends but a re-read does not.

  Deterministic by construction: the caller supplies tx-id + as-of (no wall clock,
  no Math/random) → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose ground datoms
  equal the previous beat's is a NO-OP (nothing appended). No-server-key: appends to
  a local file only, no network I/O. A COMMONS record, never advice (G1/N3)."
  (:require [clojure.string :as str]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.datom-emit :as de]
            [hinagata.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (def ^:private here-dir
     ;; Captured at namespace-LOAD time: *file* is this source file here, NOT at call-time
     ;; (sci rebinds *file* to the caller / -e expr). Actor root = methods/../ (mirrors terms_scan).
     (-> *file* io/file .getCanonicalFile .getParentFile .getParentFile)))

#?(:clj
   (defn default-seed-path []
     (str (io/file here-dir "data" "seed-legal-template-graph.kotoba.edn"))))

(defn ground-datoms-from
  "Build the durable GROUND EAVT datoms from an already-loaded {:nodes :edges} graph, in
  the canonical [\":db/add\" e a v] form. Derived readouts are excluded by design (N1/G2)."
  [{:keys [nodes edges]}]
  (let [out (transient [])]
    ;; ── node datoms (EDN read order via node-vals; deterministic)
    (doseq [n (analyze/node-vals nodes)]
      (let [nid (get n ":lt/id")]
        (doseq [a de/node-attrs :when (and (contains? n a) (some? (get n a)))]
          (conj! out (k/add nid a (get n a))))))
    ;; ── edge datoms (edge entity id content-stable: en.<from>.<kind>.<to>)
    (doseq [e edges]
      (let [eid (str "en." (get e ":en/from") "."
                     (str/replace (str (get e ":en/kind")) #"^:+" "") "." (get e ":en/to"))]
        (doseq [a de/edge-attrs :when (and (contains? e a) (some? (get e a)))]
          (conj! out (k/add eid a (get e a))))))
    (persistent! out)))

#?(:clj
   (defn ground-datoms
     ([] (ground-datoms (default-seed-path)))
     ([seed-path] (ground-datoms-from (analyze/load-file* seed-path)))))

(defn beat
  "Run one heartbeat. opts:
     :datoms   ground datoms (optional; defaults to (ground-datoms))
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path template-commons-ledger path (required)
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
                        (str (io/file here-dir "data" "persisted" "hinagata.template-commons.kotoba.edn")))
           ;; deterministic stamps for a manual run (override via real scheduler in R2+)
           r (beat {:tx-id "hinagata-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "template-commons ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
