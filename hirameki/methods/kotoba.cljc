(ns hirameki.methods.kotoba
  "hirameki 閃き — content-addressed append-only OBSERVATION LEDGER (commit-DAG).

  Same content-addressed commit-DAG machinery as busshi/ugachi (ADR-2606171000 /
  2606170900, on ADR-2605262130 + ADR-2605312345). Persists release observations to a
  local append-only EDN log of content-addressed transactions. Deterministic: caller
  supplies tx-id + as-of (no wall clock) → resume-safe. No-server-key: appends to a local
  file only, no network I/O. prev-cid chaining makes the log tamper-evident + verifiable.
  The ledger is auditable (a record of release OBSERVATIONS, NEVER a target-list)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

;; ── canonical content-address ────────────────────────────────────────────────
(defn- json-val [v]
  (cond
    (string? v) (str "\"" (-> v (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) "\"")
    (keyword? v) (str "\"" (str v) "\"")
    (boolean? v) (str v)
    (number? v) (str v)
    (nil? v) "null"
    (vector? v) (str "[" (str/join "," (map json-val v)) "]")
    :else (str "\"" v "\"")))

(defn canonical [datoms prev-cid]
  (str "{\"datoms\":[" (str/join "," (map json-val datoms)) "],\"prev\":" (json-val prev-cid) "}"))

#?(:clj
   (defn- sha256-hex [^String s]
     (let [b (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
       (apply str (map #(format "%02x" (bit-and (int %) 0xff)) b)))))

(defn tx-cid
  "Transaction CID = 'b' + sha256-hex over canonical {\"datoms\":[...],\"prev\":<cid>}."
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (sha256-hex (canonical datoms prev-cid)))))

(defn add [entity attr value] [":db/add" entity attr value])

(defn make-tx [datoms tx-id as-of prev-cid]
  {":tx/id" tx-id ":tx/as-of" as-of ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid) ":tx/count" (count datoms) ":tx/datoms" datoms})

;; ── minimal EDN reader (one tx map per line) ────────────────────────────────
(defn parse-edn [s]
  #?(:clj (edn/read-string s)
     :cljs (throw (ex-info "parse-edn is :clj-only" {}))))

;; ── file I/O (:clj only, no-server-key local append) ────────────────────────
#?(:clj
   (defn append-tx [tx log-path]
     (let [f (io/file log-path)]
       (io/make-parents f)
       (when-not (.exists f)
         (spit f ";; hirameki 閃き — append-only OBSERVATION LEDGER (content-addressed commit-DAG)\n;; ADR-2606212200 · tamper-evident (verify-chain) · no-server-key · idempotent-by-content\n"))
       (spit f (str (pr-str tx) "\n") :append true)
       (get tx ":tx/cid"))))

#?(:clj
   (defn read-log [log-path]
     (let [f (io/file log-path)]
       (if (.exists f)
         (->> (str/split-lines (slurp f))
              (remove #(or (str/blank? %) (str/starts-with? (str/triml %) ";;")))
              (mapv parse-edn))
         []))))

#?(:clj
   (defn head-cid [log-path]
     (let [txs (read-log log-path)]
       (if (seq txs) (get (last txs) ":tx/cid") ""))))

#?(:clj
   (defn verify-chain
     "Re-derive every tx CID from its datoms + prev-cid; returns
     {:ok bool :length n :broken-at idx} (broken-at -1 when intact)."
     [log-path]
     (let [txs (read-log log-path) n (count txs)]
       (loop [i 0 prev "" ts txs]
         (if (empty? ts)
           {:ok true :length n :broken-at -1}
           (let [tx (first ts)
                 expect (tx-cid (get tx ":tx/datoms") prev)]
             (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
               {:ok false :length n :broken-at i}
               (recur (inc i) (get tx ":tx/cid") (rest ts)))))))))
