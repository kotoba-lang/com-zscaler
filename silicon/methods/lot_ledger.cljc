(ns silicon.methods.lot-ledger
  "silicon 珪 — wafer-lot traceability ledger (content-addressed EAVT Datom log).

  G8 lot-traceability: every completed fab-flow lot record (silicon.methods.fab-flow)
  is lowered to append-only `:db/add` datoms — one `:silicon.lot/*` entity plus a
  `:silicon.step/*` entity per process step — and bundled into a content-addressed
  transaction whose CID = sha256(prev-cid, datoms). Same commit-DAG shape and
  canonicalization as meisai.methods.kotoba, so CIDs are byte-stable + cross-language.

  Per ADR-2605242500 + 2605262130 (kotoba Datom log = canonical state). No silent
  truncation: every step in the lot record is emitted. JVM-only (SHA-256)."
  (:require [clojure.string :as str])
  #?(:clj (:import [java.security MessageDigest])))

;; ── content-addressing (byte-identical to meisai.methods.kotoba) ─────────────

(defn- json-escape [s]
  (-> s
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\r" "\\r")
      (str/replace "\t" "\\t")))

(defn- json-str [s] (str "\"" (json-escape s) "\""))

(defn- json-val [v]
  (cond
    (true? v) "true"
    (false? v) "false"
    (number? v) (str v)
    (string? v) (json-str v)
    :else (json-str (str v))))

(defn- canonical [datoms prev-cid]
  (let [datoms-json (str "["
                         (str/join ","
                                   (map (fn [d]
                                          (str "[" (str/join "," (map json-val d)) "]"))
                                        datoms))
                         "]")]
    (str "{\"datoms\":" datoms-json ",\"prev\":" (json-str prev-cid) "}")))

(defn tx-cid
  "Content address = sha256 over (prev-cid, datoms) → a commit-DAG CID."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid]
   #?(:clj
      (let [md (MessageDigest/getInstance "SHA-256")
            ^bytes bs (.getBytes (canonical datoms prev-cid) "UTF-8")]
        (.update md bs)
        (str "b" (apply str (map #(format "%02x" (bit-and % 0xFF)) (.digest md)))))
      :cljs
      (throw (ex-info "tx-cid requires SHA-256 on the JVM" {})))))

;; ── datom emit ──────────────────────────────────────────────────────────────

(defn- add [e a v] [":db/add" e a v])

(defn- step-eid [lot-id i step]
  (str "silicon-step:" lot-id ":" i ":" step))

(defn lot->datoms
  "Lower a completed fab-flow lot record into append-only EAVT datoms.

  Emits one `silicon-lot:<lot-id>` entity (route, defect density, yield, good-die,
  packaged-units, all-pass) and one `silicon-step:<lot-id>:<i>:<step>` entity per
  step (ordinal, pass flag, dual-use flag, and each measured scalar as
  `:silicon.step/m.<key>`). Returns a flat vector of datoms in route order."
  [lot-record]
  (let [lot-id (:lot-id lot-record)
        lot-eid (str "silicon-lot:" lot-id)
        steps (:steps lot-record)
        lot-datoms
        [(add lot-eid ":silicon.lot/id" lot-id)
         (add lot-eid ":silicon.lot/route" (str/join "," (:route lot-record)))
         (add lot-eid ":silicon.lot/defect-density" (:defect-density lot-record))
         (add lot-eid ":silicon.lot/yield" (:yield lot-record))
         (add lot-eid ":silicon.lot/good-die" (:good-die lot-record))
         (add lot-eid ":silicon.lot/packaged-units" (:packaged-units lot-record))
         (add lot-eid ":silicon.lot/all-pass" (boolean (:all-pass lot-record)))
         (add lot-eid ":silicon.lot/step-count" (count steps))]
        step-datoms
        (mapcat
          (fn [i s]
            (let [eid (step-eid lot-id i (:step s))]
              (into
                [(add eid ":silicon.step/lot" lot-eid)
                 (add eid ":silicon.step/index" i)
                 (add eid ":silicon.step/name" (:step s))
                 (add eid ":silicon.step/pass" (boolean (:pass s)))
                 (add eid ":silicon.step/dual-use" (boolean (:dual-use s)))
                 (add eid ":silicon.step/added-defects" (:added-defects s))]
                ;; every measured scalar, lossless (G8 no truncation)
                (for [[k v] (sort-by (comp name key) (:measured s))]
                  (add eid (str ":silicon.step/m." (name k)) v)))))
          (range)
          steps)]
    (vec (concat lot-datoms step-datoms))))

(defn commit-lot
  "Bundle a lot record's datoms into a content-addressed transaction map chained
  onto `prev-cid` (default genesis \"\"). Returns
  {:tx/cid … :tx/prev … :tx/count … :tx/datoms […]}."
  ([lot-record] (commit-lot lot-record ""))
  ([lot-record prev-cid]
   (let [datoms (lot->datoms lot-record)
         prev (or prev-cid "")]
     {:tx/cid (tx-cid datoms prev)
      :tx/prev prev
      :tx/count (count datoms)
      :tx/datoms datoms})))

(defn verify-chain
  "Recompute every CID from its datoms + prev; verify the DAG is intact.
  `txs` is a seq of commit-lot maps. Returns {:ok bool :length int :broken-at int}."
  [txs]
  (loop [prev "" i 0]
    (if (>= i (count txs))
      {:ok true :length (count txs) :broken-at -1}
      (let [tx (nth txs i)
            expect (tx-cid (:tx/datoms tx) prev)]
        (if (and (= (:tx/cid tx) expect) (= (:tx/prev tx) prev))
          (recur expect (inc i))
          {:ok false :length (count txs) :broken-at i})))))
