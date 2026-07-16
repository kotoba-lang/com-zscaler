#!/usr/bin/env bb
;; iryo 医療 — claim/レセ電 persistence on the append-only kotoba Datom log.
(ns iryo.methods.kotoba
  "kotoba.cljc — iryo 医療 append-only BILLING HISTORY writer (PHI-free)
  (ADR-2606074000, on ADR-2605262130 + ADR-2605312345; same content-addressed
  commit-DAG machinery as busshi/ugachi/kaname).

  Persists a computed rezept result + its レセ電 records as content-addressed
  Datoms on an append-only commit-DAG — as-of billing history.

  Attribute namespaces:
    :iryo.claim/*   per-claim summary (codes + amounts, PHI-free)
    :iryo.line/*    per-line detail (shikibetsu / code / ten, PHI-free)

  PHI INVARIANT (G2 — STRUCTURAL, DO NOT WEAKEN):
  - NEVER persist 氏名/生年月日/SOAP free-text or any PHI.
  - Accepted entity ID = pseudonymous DID only (never氏名/MRN/stable ID).
  - The persist! fn calls assert-no-phi! on the datoms before writing.
  - PHI-shaped keys (name/kana/dob/birthdate/address/phone/email/soap_*
    /mrn/free_text/note) in the value position are REFUSED at write time.
    Enforced in code and proven by tests.

  No-server-key: no network I/O (live engine bridge is operator-gated, G3).
  data/ is gitignored — local PHI-free audit log only."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── PHI guard (G2) ──────────────────────────────────────────────────────────
(def ^:private phi-patterns
  #{"name" "kana" "dob" "birthdate" "address" "phone" "email"
    "soap_s" "soap_o" "soap_a" "soap_p" "free_text" "note" "mrn"
    "hihokensha"}) ;; insurer member number is scoped-PHI; keep off the public log

(defn phi? [s]
  (let [low (str/lower-case (str s))]
    (or (contains? phi-patterns low)
        ;; flag any attribute whose local name matches a PHI keyword
        (some #(str/includes? low (str "/" %)) phi-patterns))))

(defn assert-no-phi!
  "Raise if any datom carries a PHI-shaped attribute or value."
  [datoms]
  (doseq [[_op _e a v] datoms]
    (when (phi? a)
      (throw (ex-info (str "PHI attribute refused in billing log: " a) {:error :phi-leak :attr a})))
    (when (and (string? v) (phi? v))
      (throw (ex-info (str "PHI value refused in billing log (attr=" a "): " v)
                      {:error :phi-leak :attr a :value v})))))

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

(defn make-tx [datoms tx-id as-of prev-cid]
  {":tx/id" tx-id ":tx/as-of" as-of ":tx/prev" prev-cid
   ":tx/cid" (tx-cid datoms prev-cid) ":tx/count" (count datoms) ":tx/datoms" datoms})

;; ── Claim → EAVT Datoms (PHI-free) ──────────────────────────────────────────
(defn claim-datoms
  "Convert a rezept result + pseudonymous-did to PHI-free EAVT datoms.
  claim-id should be a stable pseudonymous identifier (e.g. sha256 of DID+month).
  `rezept-result` is the map returned by iryo.methods.rezept/compute.
  `receden-rows` is the seq of レセ電 row vectors from iryo.methods.receden/build-receden.

  PHI-free contract:
  - entity = iryo-claim:<claim-id>  (pseudonymous, no 氏名/MRN)
  - stored attrs: codes, ten (points), yen amounts, kubun, futan-kubun, shikibetsu
  - NO patient name, dob, hihokensha number, SOAP notes, or any free-text PHI."
  [claim-id pseudonym-did rezept-result receden-rows]
  (let [r rezept-result
        e (str "iryo-claim:" claim-id)
        ;; ── claim-level datoms ──────────────────────────────────────────
        base-datoms
        [(add e ":iryo.claim/id" claim-id)
         (add e ":iryo.claim/patient-did" pseudonym-did)
         (add e ":iryo.claim/total-ten" (int (or (:total-ten r) 0)))
         (add e ":iryo.claim/total-iryohi-yen" (int (or (:total-iryohi-yen r) 0)))
         (add e ":iryo.claim/patient-pay-yen" (int (or (:patient-pay-yen r) 0)))
         (add e ":iryo.claim/total-futan-yen" (int (or (:total-futan-yen r) 0)))
         (add e ":iryo.claim/futan-wari" (double (or (:futan-wari r) 0.0)))
         (add e ":iryo.claim/kogaku-applied" (boolean (:kogaku-applied r)))
         (add e ":iryo.claim/nyuin" (boolean (:nyuin r)))
         (add e ":iryo.claim/line-count" (count (:lines r [])))
         (add e ":iryo.claim/receden-row-count" (count receden-rows))]
        ;; ── kogaku datoms (optional) ────────────────────────────────────
        kogaku-datoms
        (cond-> []
          (:kogaku-kubun r)
          (conj (add e ":iryo.claim/kogaku-kubun" (:kogaku-kubun r)))
          (:kogaku-limit-yen r)
          (conj (add e ":iryo.claim/kogaku-limit-yen" (int (:kogaku-limit-yen r)))))
        ;; ── kubun-totals datoms ─────────────────────────────────────────
        kubun-datoms
        (mapv (fn [[kubun ten]]
                (add e (str ":iryo.claim/kubun-" kubun "-ten") (int ten)))
              (:kubun-totals r))
        ;; ── line datoms (code + ten only, no free-text) ─────────────────
        line-datoms
        (mapcat (fn [i line]
                  (let [le (str "iryo-line:" claim-id ":" i)]
                    [(add le ":iryo.line/claim-id" claim-id)
                     (add le ":iryo.line/seq" i)
                     (add le ":iryo.line/kind" (or (:kind line) ""))
                     (add le ":iryo.line/code" (or (:code line) ""))
                     (add le ":iryo.line/shikibetsu" (or (:shikibetsu line) ""))
                     (add le ":iryo.line/ten" (int (or (:ten line) 0)))
                     (add le ":iryo.line/count" (int (or (:count line) 1)))]))
                (range) (:lines r []))
        ;; ── receden record-type summary ─────────────────────────────────
        rec-summary-datoms
        (mapv (fn [[rtype cnt]]
                (add e (str ":iryo.claim/receden-" rtype "-count") cnt))
              (reduce (fn [acc row] (update acc (first row) (fnil inc 0))) {} receden-rows))]
    (vec (concat base-datoms kogaku-datoms kubun-datoms line-datoms rec-summary-datoms))))

;; ── EDN log serialization ────────────────────────────────────────────────────
(defn- edn-val [v]
  (cond
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (py-float-str v)
    (string? v) (if (str/starts-with? v ":") v (str \" (json-escape v) \"))
    (sequential? v) (str "[" (str/join " " (map edn-val v)) "]")
    :else (str v)))

(defn tx->edn [tx]
  (let [datoms (str/join " " (map (fn [d] (str "[" (str/join " " (map edn-val d)) "]"))
                                  (get tx ":tx/datoms")))]
    (str "{:tx/id " (get tx ":tx/id") " :tx/as-of " (get tx ":tx/as-of")
         " :tx/prev " (str \" (json-escape (get tx ":tx/prev")) \")
         " :tx/cid " (str \" (json-escape (get tx ":tx/cid")) \")
         " :tx/count " (get tx ":tx/count") " :tx/datoms [" datoms "]}")))

;; ── minimal EDN reader (subset), self-contained ──────────────────────────────
(def ^:private tok-re #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (let [m (re-matcher tok-re s)]
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
           (spit f (str ";; iryo 医療 — BILLING HISTORY (append-only, content-addressed "
                        "EAVT commit-DAG of PHI-free claim records). Generated; DO NOT hand-edit. "
                        "PHI-free: pseudonymous DID + codes + amounts only. ADR-2606074000.\n")))
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

     (defn persist!
       "Persist a claim (rezept result + receden rows) to the append-only billing log.
       Idempotent: identical datom content already persisted = no-op (busshi pattern).
       PHI invariant: asserts-no-phi! before any write; raises on violation.
       No-server-key: local file only (G3); live engine bridge = operator step."
       [claim-id pseudonym-did rezept-result receden-rows log-path tx-id as-of]
       (let [datoms (claim-datoms claim-id pseudonym-did rezept-result receden-rows)
             _ (assert-no-phi! datoms)                ;; G2 — raises on PHI
             txs (read-log log-path)
             prev (if (seq txs) (get (last txs) ":tx/cid") "")
             last-ds (when (seq txs) (get (last txs) ":tx/datoms"))]
         (if (= datoms last-ds)
           {:persisted false :reason :no-change :cid prev}
           (let [tx (make-tx datoms tx-id as-of prev)
                 cid (append-tx tx log-path)]
             {:persisted true :cid cid :datom-count (count datoms)}))))))
