(ns minori.ledger
  "Minimal content-addressed append-only ledger for minori's react beats.
   Pattern mirrors the actor kotoba.cljc commit-DAGs (verify-chain tamper-evident,
   idempotent-by-content, resume-safe, no-server-key) but is self-contained so it
   runs under bb with zero deps. State lives in an EDN file (gitignored).
   No wall-clock / no randomness — beat index = ledger length (deterministic)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(defn sha256-hex [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bs))))

(defn content-cid
  "CID of an entry's content (everything except chain fields)."
  [entry]
  (sha256-hex (pr-str (dissoc entry :cid :parent :beat))))

(defn load-ledger [path]
  (if (.exists (io/file path))
    (or (edn/read-string (slurp path)) [])
    []))

(defn head [ledger] (last ledger))

(defn append!
  "Append entry. Idempotent-by-content: if head holds the same content-cid, no-op.
   Chains :parent = head :cid; :cid = sha256(content-cid + parent + beat)."
  [path entry]
  (let [ledger (load-ledger path)
        h      (head ledger)
        ccid   (content-cid entry)]
    (if (and h (= ccid (content-cid h)))
      {:ledger ledger :head h :appended? false}      ; no-change → resume-safe no-op
      (let [parent (:cid h)
            beat   (count ledger)
            cid    (sha256-hex (str ccid "|" parent "|" beat))
            e      (assoc entry :cid cid :parent parent :beat beat)
            led'   (conj ledger e)]
        (io/make-parents path)
        (spit path (pr-str led'))
        {:ledger led' :head e :appended? true}))))

(defn verify-chain
  "Tamper-evident: each entry's :cid must equal sha256(content-cid|parent|beat)
   and :parent must equal the previous entry's :cid."
  [ledger]
  (loop [prev nil, [e & more] ledger, i 0]
    (cond
      (nil? e) {:ok true :n i}
      (not= (:parent e) (:cid prev))
        {:ok false :at i :why :broken-parent}
      (not= (:cid e) (sha256-hex (str (content-cid e) "|" (:cid prev) "|" (:beat e))))
        {:ok false :at i :why :bad-cid}
      :else (recur e more (inc i)))))
