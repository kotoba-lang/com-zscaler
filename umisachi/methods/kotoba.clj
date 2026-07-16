#!/usr/bin/env bb
;; umisachi 海幸 — kotoba Datom-log writer (local, content-addressed commit-DAG).
(ns umisachi.methods.kotoba
  "kotoba.clj — umisachi marine-bounty kotoba Datom-log writer (ADR-2606074200 / 2605312345).

  Canonical state is the kotoba Datom log — content-addressed EAVT assertions, append-only. This
  is the LOCAL autonomous-loop write path (the watatsuna/shionome shape, Family-A canonical form
  `{:datoms <pr-str> :prev <pr-str>}` — so umisachi's commit-DAG shares the b752d9f3… empty-tx
  invariant with kabuto/watatsuna/watari/kanjo).

    graph-datoms   → EAVT for every organism node + 縁 edge (reuses datom_emit's attr vocab)
    derived-datoms → EAVT for the derived :bond/* readouts (flagged :bond/derived true — the
                     edge-primary integrals of analyze, computed on read, N1/G2, never re-ingested
                     as durable fact and NEVER a catch-target list, G1)
    make-tx / append-tx / read-log / head-cid / verify-chain — content-addressed commit-DAG

  :db/add only (append-only, 非終末論). Deterministic: caller supplies tx-id + as-of; derived rows
  are emitted in a canonical sort so the CID is reproducible.

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/kotoba.clj"
  (:require [umisachi.methods.datom-emit :as de]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private this-file *file*)
(defn log-default []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "umisachi.datoms.kotoba.edn")))

(defn- add-datom [e a v] [:db/add e a v])

(defn- edge-id [e]
  (keyword (str "en." (name (:en/from e)) "." (name (:en/kind e)) "." (name (:en/to e)))))

(defn graph-datoms
  "Flatten the marine-bounty graph (nodes-by-id map + edge vector) into append-only EAVT
  assertions. Node E = :organism/id; edge E = en.<from>.<kind>.<to>. List values fan out."
  [nodes edges]
  (vec
   (concat
    (for [[nid n] nodes
          a de/node-attrs
          :when (some? (get n a))
          item (let [v (get n a)] (if (sequential? v) v [v]))]
      (add-datom nid a item))
    (for [e edges
          :let [eid (edge-id e)]
          a de/edge-attrs
          :when (some? (get e a))]
      (add-datom eid a (get e a))))))

(defn derived-datoms
  "Flatten analyze's edge-primary integrals into EAVT :bond/* readouts, flagged
  :bond/derived true. `res` = analyze/analyze {:nourishment :depletion :depletion-out}.
  Sorted (value desc, then id) so the CID is reproducible regardless of map-iteration order."
  [res]
  (let [rows (fn [m attr]
               (mapcat (fn [[nid v]]
                         [(add-datom nid attr (double v))
                          (add-datom nid :bond/derived true)])
                       (sort-by (juxt (comp - val) (comp str key)) m)))]
    (vec (concat (rows (:nourishment res) :bond/provisioning-nourishment)
                 (rows (:depletion res) :bond/depletion-load)
                 (rows (:depletion-out res) :bond/depletion-imposed)))))

;; ── content-addressed commit-DAG (Family-A canonical form) ─────────────────────
(defn- sha256-hex [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (apply str (map #(format "%02x" (bit-and % 0xff)) (.digest md (.getBytes s "UTF-8"))))))

(defn- canonical [datoms prev] (str "{:datoms " (pr-str datoms) " :prev " (pr-str prev) "}"))

(defn tx-cid
  "Content address = 'b' + sha256 over (prev, datoms) → a commit-DAG."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev] (str "b" (sha256-hex (canonical datoms prev)))))

(defn make-tx [datoms & {:keys [tx-id as-of prev-cid] :or {prev-cid ""}}]
  {:tx/id tx-id :tx/as-of as-of :tx/prev prev-cid
   :tx/cid (tx-cid datoms prev-cid) :tx/count (count datoms) :tx/datoms datoms})

(defn append-tx
  ([tx] (append-tx tx (log-default)))
  ([tx log-path]
   (let [f (io/file log-path)]
     (.mkdirs (.getParentFile (.getAbsoluteFile f)))
     (when-not (.exists f)
       (spit f (str ";; umisachi kotoba Datom log — append-only EAVT transactions "
                    "(content-addressed DAG). Restoration map, never a catch-target list (G1). "
                    "DO NOT hand-edit. ADR-2606074200.\n")))
     (spit f (str (pr-str tx) "\n") :append true)
     (:tx/cid tx))))

(defn read-log
  ([] (read-log (log-default)))
  ([log-path]
   (let [f (io/file log-path)]
     (if-not (.exists f) []
       (->> (str/split-lines (slurp f)) (map str/trim)
            (remove #(or (empty? %) (str/starts-with? % ";"))) (mapv edn/read-string))))))

(defn head-cid
  ([] (head-cid (log-default)))
  ([log-path] (let [txs (read-log log-path)] (if (seq txs) (:tx/cid (last txs)) ""))))

(defn verify-chain
  "Recompute every CID from its datoms + prev; verify the DAG is intact. {:ok :length :broken-at}."
  ([] (verify-chain (log-default)))
  ([log-path]
   (let [txs (read-log log-path)]
     (loop [i 0 prev "" xs txs]
       (if (empty? xs)
         {:ok true :length (count txs) :broken-at -1}
         (let [tx (first xs) expect (tx-cid (:tx/datoms tx []) prev)]
           (if (or (not= (:tx/cid tx) expect) (not= (:tx/prev tx) prev))
             {:ok false :length (count txs) :broken-at i}
             (recur (inc i) (:tx/cid tx) (rest xs)))))))))
