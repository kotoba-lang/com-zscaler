#!/usr/bin/env bb
;; iryo 医療 — masters as EAVT Datoms (content-addressed kotoba log).
(ns iryo.methods.datoms
  "datoms.cljc — iryo 医療 masters-as-EAVT-Datoms layer (ADR-2606074000).

  Converts loaded master tables (診療行為/医薬品/特定器材/傷病名/修飾語/コメント)
  into EAVT Datoms `[:db/add entity attr value]` keyed on the item CODE, using
  the busshi/ugachi/kaname content-addressed commit-DAG machinery.

  Attribute namespaces:
    :iryo.shinryo/*   診療行為 (procedure)
    :iryo.drug/*      医薬品   (drug / iyaku)
    :iryo.material/*  特定器材 (material / tokutei)
    :iryo.shobyo/*    傷病名   (disease / diagnosis)
    :iryo.shushokugo/* 修飾語  (modifier)
    :iryo.comment/*   コメント (comment)

  Datom-backed lookup: `resolve-shinryo` / `resolve-drug` / `resolve-material`
  / `resolve-shobyo` / `resolve-shushokugo` / `resolve-comment` return records
  identical in shape to iryo.methods.masters lookups, so the pure calc fns
  (rezept/receden) are decoupled from the backing store.

  PHI invariant (G2): master items are CODES + reference names only — no patient
  identity, no PHI. This namespace enforces that by construction (it never accepts
  a patient/karte arg).

  No-server-key: pure functions + local state only."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [iryo.methods.masters :as masters]))

;; ── EAVT add helper ──────────────────────────────────────────────────────────
(defn add [entity attr value] [":db/add" entity attr value])

;; ── canonical JSON (family-consistent with busshi/ugachi/kaname) ─────────────
(defn- py-float-str [x] (str (double x)))

(defn- json-escape [s]
  (let [sb (StringBuilder.)]
    (doseq [c (str s)]
      (let [code (int c)]
        (cond
          (= c \") (.append sb "\\\"")
          (= c \\) (.append sb "\\\\")
          (= c \newline) (.append sb "\\n")
          (= c \return) (.append sb "\\r")
          (= c \tab) (.append sb "\\t")
          (< code 0x20) (.append sb (format "\\u%04x" code))
          :else (.append sb c))))
    (str sb)))

(defn- json-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (nil? v) "null"
    (integer? v) (str v)
    (float? v) (py-float-str v)
    (string? v) (str \" (json-escape v) \")
    (sequential? v) (str "[" (str/join "," (map json-val v)) "]")
    :else (str v)))

(defn- canonical [datoms prev-cid]
  (str "{\"datoms\":[" (str/join "," (map json-val datoms)) "],\"prev\":" (json-val prev-cid) "}"))

(defn- sha256-hex [^String s]
  (let [b (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) b))))

(defn tx-cid
  ([datoms] (tx-cid datoms ""))
  ([datoms prev-cid] (str "b" (sha256-hex (canonical datoms prev-cid)))))

;; ── master → EAVT Datom converters ──────────────────────────────────────────
(defn shinryo-datoms
  "Convert a single 診療行為 record to EAVT datoms."
  [item]
  (let [e (str "iryo-shinryo:" (:code item))]
    (cond-> [(add e ":iryo.shinryo/code" (:code item))
             (add e ":iryo.shinryo/name" (or (:name item) ""))
             (add e ":iryo.shinryo/ten" (int (or (:ten item) 0)))]
      (seq (:shikibetsu item))
      (conj (add e ":iryo.shinryo/shikibetsu" (:shikibetsu item))))))

(defn drug-datoms
  "Convert a single 医薬品 (iyaku) record to EAVT datoms."
  [item]
  (let [e (str "iryo-drug:" (:code item))]
    [(add e ":iryo.drug/code" (:code item))
     (add e ":iryo.drug/name" (or (:name item) ""))
     (add e ":iryo.drug/yakka" (double (or (:yakka item) 0.0)))
     (add e ":iryo.drug/unit" (or (:unit item) ""))]))

(defn material-datoms
  "Convert a single 特定器材 (tokutei) record to EAVT datoms."
  [item]
  (let [e (str "iryo-material:" (:code item))]
    [(add e ":iryo.material/code" (:code item))
     (add e ":iryo.material/name" (or (:name item) ""))
     (add e ":iryo.material/yakka" (double (or (:yakka item) 0.0)))
     (add e ":iryo.material/unit" (or (:unit item) ""))]))

(defn shobyo-datoms
  "Convert a single 傷病名 record to EAVT datoms."
  [item]
  (let [e (str "iryo-shobyo:" (:code item))]
    (cond-> [(add e ":iryo.shobyo/code" (:code item))
             (add e ":iryo.shobyo/name" (or (:name item) ""))]
      (seq (:icd10 item))
      (conj (add e ":iryo.shobyo/icd10" (:icd10 item))))))

(defn shushokugo-datoms
  "Convert a single 修飾語 record to EAVT datoms."
  [item]
  (let [e (str "iryo-shushokugo:" (:code item))]
    [(add e ":iryo.shushokugo/code" (:code item))
     (add e ":iryo.shushokugo/name" (or (:name item) ""))]))

(defn comment-datoms
  "Convert a single コメント record to EAVT datoms."
  [item]
  (let [e (str "iryo-comment:" (:code item))]
    [(add e ":iryo.comment/code" (:code item))
     (add e ":iryo.comment/pattern" (or (:pattern item) ""))
     (add e ":iryo.comment/name" (or (:name item) ""))]))

(defn masters->datoms
  "Convert all master tables to a flat vector of EAVT datoms."
  [m]
  (vec (concat
        (mapcat shinryo-datoms (vals (:shinryo m)))
        (mapcat drug-datoms (vals (:iyaku m)))
        (mapcat material-datoms (vals (:tokutei m)))
        (mapcat shobyo-datoms (vals (:shobyo m)))
        (mapcat shushokugo-datoms (vals (:shushokugo m)))
        (mapcat comment-datoms (vals (:comment m))))))

;; ── In-memory Datom store (EAVT / AVET index over the datoms) ────────────────
;; A lightweight in-process index so resolve-* fns look up by [entity attr] pair.

(defn build-store
  "Build an in-memory EAVT lookup store from a list of EAVT datoms.
  Returns a map {[entity attr] => value} (first-write wins)."
  [datoms]
  (reduce (fn [acc [_op e a v]]
            (let [k [e a]]
              (if (contains? acc k) acc (assoc acc k v))))
          {} datoms))

(defn- entity-attrs
  "Return a map of {attr => value} for a given entity from the store."
  [store entity-prefix]
  (into {} (for [[[e a] v] store :when (str/starts-with? e entity-prefix)] [a v])))

;; ── Datom-backed resolve fns (same shape as iryo.methods.masters lookups) ────

(defn- not-found! [kind code]
  (throw (ex-info (str kind " コード not in Datom store: " code)
                  {:error :not-found :code code})))

(defn resolve-shinryo
  "Resolve a 診療行為 code from the Datom store. Returns same shape as masters/shinryo."
  [store code]
  (let [e (str "iryo-shinryo:" code)
        attrs (entity-attrs store e)]
    (if (empty? attrs)
      (not-found! "診療行為" code)
      {:code (get attrs ":iryo.shinryo/code" code)
       :name (get attrs ":iryo.shinryo/name" "")
       :ten (int (or (get attrs ":iryo.shinryo/ten") 0))
       :shikibetsu (get attrs ":iryo.shinryo/shikibetsu" "")})))

(defn resolve-drug
  "Resolve an 医薬品 code from the Datom store. Returns same shape as masters/drug."
  [store code]
  (let [e (str "iryo-drug:" code)
        attrs (entity-attrs store e)]
    (if (empty? attrs)
      (not-found! "医薬品" code)
      {:code (get attrs ":iryo.drug/code" code)
       :name (get attrs ":iryo.drug/name" "")
       :yakka (double (or (get attrs ":iryo.drug/yakka") 0.0))
       :unit (get attrs ":iryo.drug/unit" "")})))

(defn resolve-material
  "Resolve a 特定器材 code from the Datom store. Returns same shape as masters/material."
  [store code]
  (let [e (str "iryo-material:" code)
        attrs (entity-attrs store e)]
    (if (empty? attrs)
      (not-found! "特定器材" code)
      {:code (get attrs ":iryo.material/code" code)
       :name (get attrs ":iryo.material/name" "")
       :yakka (double (or (get attrs ":iryo.material/yakka") 0.0))
       :unit (get attrs ":iryo.material/unit" "")})))

(defn resolve-shobyo
  "Resolve a 傷病名 code from the Datom store. Returns same shape as masters/shobyo."
  [store code]
  (let [e (str "iryo-shobyo:" code)
        attrs (entity-attrs store e)]
    (if (empty? attrs)
      (not-found! "傷病名" code)
      {:code (get attrs ":iryo.shobyo/code" code)
       :name (get attrs ":iryo.shobyo/name" "")
       :icd10 (get attrs ":iryo.shobyo/icd10" "")})))

(defn resolve-shushokugo
  "Resolve a 修飾語 code from the Datom store. Returns same shape as masters/shushokugo."
  [store code]
  (let [e (str "iryo-shushokugo:" code)
        attrs (entity-attrs store e)]
    (if (empty? attrs)
      (not-found! "修飾語" code)
      {:code (get attrs ":iryo.shushokugo/code" code)
       :name (get attrs ":iryo.shushokugo/name" "")})))

(defn resolve-comment
  "Resolve a コメント code from the Datom store. Returns same shape as masters/comment."
  [store code]
  (let [e (str "iryo-comment:" code)
        attrs (entity-attrs store e)]
    (if (empty? attrs)
      (not-found! "コメント" code)
      {:code (get attrs ":iryo.comment/code" code)
       :pattern (get attrs ":iryo.comment/pattern" "")
       :name (get attrs ":iryo.comment/name" "")})))

(defn store-from-masters
  "Convenience: load masters → emit datoms → build AVET-indexed store."
  [m]
  (build-store (masters->datoms m)))

;; ── EDN log serialization (family-consistent) ────────────────────────────────
(defn- edn-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (py-float-str v)
    (string? v) (if (str/starts-with? v ":") v (str \" (json-escape v) \"))
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (str v)))

(defn make-tx [datoms tx-id as-of prev-cid]
  {":tx/id" tx-id ":tx/as-of" as-of ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid) ":tx/count" (count datoms) ":tx/datoms" datoms})

(defn tx->edn [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of")
         " :tx/prev " (str \" (json-escape (get tx ":tx/prev")) \")
         " :tx/cid " (str \" (json-escape (get tx ":tx/cid")) \")
         " :tx/count " (get tx ":tx/count") " :tx/datoms [" datoms "]}")))

(defn- tok-re [] #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (let [m (re-matcher (tok-re) s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)] (if (nil? t) (step) (cons t (step))))))))))

(defn- atom-of [t]
  (cond
    (str/starts-with? t "\"") (-> (subs t 1 (dec (count t)))
                                  (str/replace "\\\"" "\"") (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else (let [l (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
            (if (not= l ::nan) l
                (let [d (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
                  (if (not= d ::nan) d t))))))

(def ^:private end-marker ::end)

(defn- parse-step [toks i]
  (let [t (nth toks i) i (inc i)]
    (cond
      (= t "[") (loop [i i out []] (let [[x i] (parse-step toks i)]
                                     (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{") (loop [i i out {}]
                  (let [[k i] (parse-step toks i)]
                    (if (= k end-marker) [out i]
                        (let [[v i] (parse-step toks i)] (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn parse-edn [s] (first (parse-step (vec (tokens s)) 0)))

#?(:clj
   (do
     (defn append-tx [tx log-path]
       (let [f (io/file log-path)]
         (when-let [p (.getParentFile f)] (.mkdirs p))
         (when-not (.exists f)
           (spit f (str ";; iryo 医療 — MASTER DATOM LOG (append-only, content-addressed "
                        "EAVT commit-DAG of master ingest snapshots). Generated; DO NOT hand-edit. "
                        "PHI-free: codes + reference names only. ADR-2606074000.\n")))
         (spit f (str (tx->edn tx) "\n") :append true)
         (get tx ":tx/cid")))

     (defn read-log [log-path]
       (let [f (io/file log-path)]
         (if-not (.exists f) []
                 (->> (str/split-lines (slurp f))
                      (map str/trim)
                      (remove #(or (empty? %) (str/starts-with? % ";")))
                      (mapv parse-edn)))))

     (defn head-cid [log-path]
       (let [txs (read-log log-path)] (if (seq txs) (get (last txs) ":tx/cid") "")))

     (defn verify-chain [log-path]
       (let [txs (read-log log-path) n (count txs)]
         (loop [i 0 prev "" ts txs]
           (if (empty? ts) {:ok true :length n :broken-at -1}
               (let [tx (first ts) expect (tx-cid (get tx ":tx/datoms") prev)]
                 (if (or (not= (get tx ":tx/cid") expect) (not= (get tx ":tx/prev") prev))
                   {:ok false :length n :broken-at i}
                   (recur (inc i) (get tx ":tx/cid") (rest ts))))))))

     (defn persist-masters!
       "Emit masters as EAVT datoms and append to a content-addressed log.
       Idempotent: re-persisting the identical masters is a no-op (returns :no-change).
       Uses datom-vector equality (busshi pattern) — identical content = no new tx."
       [m log-path tx-id as-of]
       (let [datoms (masters->datoms m)
             txs (read-log log-path)
             prev (if (seq txs) (get (last txs) ":tx/cid") "")
             last-ds (when (seq txs) (get (last txs) ":tx/datoms"))]
         (if (= datoms last-ds)
           {:appended false :reason :no-change :cid prev}
           (let [tx (make-tx datoms tx-id as-of prev)
                 cid (append-tx tx log-path)]
             {:appended true :cid cid :datom-count (count datoms)}))))))
