#!/usr/bin/env bb
;; tanemaki 種蒔き — autonomous heartbeat: load the stewardship graph → append GROUND datoms.
(ns tanemaki.methods.autorun
  "autorun.cljc — tanemaki 種蒔き deterministic heartbeat (ADR-2606122001, inochi/tate pattern).

  One beat: load the grant-stewardship seed graph, build the GROUND EAVT datoms (screen/
  criterion/org/source/instrument/milestone NODES + their :en/* 縁), and APPEND them as ONE
  content-addressed transaction to the append-only STEWARDSHIP LEDGER (kotoba.cljc). prev-cid
  chaining keeps the ledger tamper-evident + resume-safe.

  GROUND ONLY: dd-fit / evidence-coverage / route are DERIVED (computed on read, N1/G4) and are
  NEVER persisted — no stored org score, no stored decision. Deterministic by construction: the
  caller supplies tx-id + as-of (no wall clock); nodes emit in EDN-read order (node-order) +
  edges content-stable → resume-safe. IDEMPOTENT-BY-CONTENT: a beat whose ground datoms equal the
  previous beat's is a NO-OP. No-server-key: appends to a local file only, no network I/O.
  G1: a steward not a sovereign; G2: the fund GIVES, never INVESTS."
  (:require [clojure.string :as str]
            [tanemaki.methods.analyze :as analyze]
            [tanemaki.methods.datom-emit :as de]
            [tanemaki.methods.kotoba :as k]
            #?(:clj [clojure.java.io :as io])))

(defn- node-order [nodes]
  ;; analyze/load-file* attaches :tanemaki.methods.analyze/order; fall back to (keys nodes)
  (or (:tanemaki.methods.analyze/order (meta nodes)) (keys nodes)))

(defn- strip-colon [s] (if (str/starts-with? s ":") (subs s 1) s))

(defn ground-datoms-from
  "Build the durable GROUND EAVT datoms from a loaded {:nodes :edges} graph, in the
  canonical [\":db/add\" e a v] form. Derived readouts excluded by design (N1/G4)."
  [{:keys [nodes edges]}]
  (let [out (transient [])]
    (doseq [nid (node-order nodes)]
      (let [nd (get nodes nid)]
        (doseq [a de/node-attrs :when (and (contains? nd a) (some? (get nd a)))]
          (conj! out (k/add nid a (get nd a))))))
    (doseq [e edges]
      (let [eid (str "en." (get e ":en/from") "."
                     (strip-colon (str (get e ":en/kind"))) "." (get e ":en/to"))]
        (doseq [a de/edge-attrs :when (and (contains? e a) (some? (get e a)))]
          (conj! out (k/add eid a (get e a))))))
    (persistent! out)))

#?(:clj
   (def ^:private here-dir
     (-> *file* io/file .getCanonicalFile .getParentFile .getParentFile)))

#?(:clj
   (defn default-seed-path []
     (str (io/file here-dir "data" "seed-stewardship-graph.kotoba.edn"))))

#?(:clj
   (defn ground-datoms
     ([] (ground-datoms (default-seed-path)))
     ([seed-path] (ground-datoms-from (analyze/load-file* seed-path)))))

(defn beat
  "Run one heartbeat. opts:
     :datoms   ground datoms (optional; defaults to (ground-datoms))
     :tx-id    deterministic tx id (required)
     :as-of    deterministic as-of stamp (required)
     :log-path stewardship-ledger path (required)
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
                        (str (io/file here-dir "data" "persisted" "tanemaki.stewardship.kotoba.edn")))
           r (beat {:tx-id "tanemaki-beat-manual" :as-of "manual" :log-path log-path})]
       (println (str "stewardship ledger head=" (:head r)
                     " datoms=" (:count r)
                     " appended=" (:appended r)
                     (when (:reason r) (str " (" (name (:reason r)) ")"))))
       (println (str "chain=" (k/verify-chain log-path))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
