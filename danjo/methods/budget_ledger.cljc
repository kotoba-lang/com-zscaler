;; ported from 20-actors/danjo/methods/budget_ledger.py — real port replacing the
;; unit_refactor stage-0 "TODO: port-failed" stubs. NS fixed (root.* -> danjo.*) and the
;; file is now .cljc (matching the sibling analyze.cljc/autorun.cljc/kotoba.cljc convention).
;; Self-contained (own sha-256 + JSON reader, no cheshire/data.json, no dependency on the
;; sibling analyze namespace).
(ns danjo.methods.budget-ledger
  "budget_ledger.py — 弾正 (danjo) budget_ledger ingest method (the coded R0 method).
  1:1 Clojure port of `methods/budget_ledger.py`.

  Ingests gov.dataset.budgetRecord rows → a budget ledger grouped by
  (programCode, fiscalYear); each line carries its own deterministic CID (G5 provenance).
  Pure; file I/O only at the load-seed/load-json edge.

  House style: gov.dataset.* records stay string-keyed maps, byte-for-byte the same shapes
  Python json.loads produced; keywords are kept as ':ns/name' strings."
  (:require [clojure.string :as str]))

;; ── sha-256 ──────────────────────────────────────────────────────────────────
(defn- sha256-hex
  "String → lowercase hex sha-256 digest (UTF-8)."
  ^String [^String s]
  #?(:clj (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
            (apply str (map #(format "%02x" (bit-and % 0xff)) d)))
     :default (throw (ex-info "bind a sha-256 impl on this host" {}))))

;; ── minimal JSON reader (subset sufficient for gov.dataset seeds) ─────────────
;; maps string-keyed, integers → long, literals → true/false/nil — Python json.loads shapes.
(declare json-value)

(defn- skip-ws
  "Skip JSON insignificant whitespace ONLY. Commas are explicit separators, NOT skipped here."
  [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i)) i)))

(defn- json-string [^String s i]
  (loop [i (inc i), sb (StringBuilder.)]
    (let [c (nth s i)]
      (cond
        (= c \") [(.toString sb) (inc i)]
        (= c \\)
        (let [e (nth s (inc i))]
          (case e
            \" (do (.append sb \") (recur (+ i 2) sb))
            \\ (do (.append sb \\) (recur (+ i 2) sb))
            \/ (do (.append sb \/) (recur (+ i 2) sb))
            \b (do (.append sb \backspace) (recur (+ i 2) sb))
            \f (do (.append sb \formfeed) (recur (+ i 2) sb))
            \n (do (.append sb \newline) (recur (+ i 2) sb))
            \r (do (.append sb \return) (recur (+ i 2) sb))
            \t (do (.append sb \tab) (recur (+ i 2) sb))
            \u (let [cp (Integer/parseInt (subs s (+ i 2) (+ i 6)) 16)]
                 (.append sb (char cp)) (recur (+ i 6) sb))
            (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))

(defn- json-number [^String s i]
  (let [end (loop [j i]
              (if (and (< j (count s))
                       (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                (recur (inc j)) j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok) (Double/parseDouble tok) (Long/parseLong tok)) end]))

(defn- json-array [^String s i]
  (loop [i (skip-ws s (inc i)), out []]
    (if (= (nth s i) \])
      [out (inc i)]
      (let [[v i] (json-value s i)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) (conj out v))
          [(conj out v) (inc i)])))))

(defn- json-object [^String s i]
  (loop [i (skip-ws s (inc i)), out {}]
    (if (= (nth s i) \})
      [out (inc i)]
      (let [[k i] (json-string s i)
            i (skip-ws s i)
            [v i] (json-value s (skip-ws s (inc i)))
            out (assoc out k v)
            i (skip-ws s i)]
        (if (= (nth s i) \,)
          (recur (skip-ws s (inc i)) out)
          [out (inc i)])))))

(defn- json-value [^String s i]
  (let [i (skip-ws s i), c (nth s i)]
    (cond
      (= c \{) (json-object s i)
      (= c \[) (json-array s i)
      (= c \") (json-string s i)
      (= c \t) [true (+ i 4)]
      (= c \f) [false (+ i 5)]
      (= c \n) [nil (+ i 4)]
      :else (json-number s i))))

(defn parse-json
  "Parse the first JSON value in text → Clojure data (maps string-keyed)."
  [text]
  (first (json-value text 0)))

#?(:clj
   (defn load-json
     "Read + parse a JSON file (file I/O only at this edge)."
     [path]
     (parse-json (slurp (str path)))))

;; ── canonical JSON for the CID preimage (json.dumps sort_keys, compact, ensure_ascii=False)
(defn- json-escape-utf8 ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(defn- canonical-json-utf8 ^String [v]
  (cond
    (string? v)     (str "\"" (json-escape-utf8 v) "\"")
    (boolean? v)    (if v "true" "false")
    (nil? v)        "null"
    (integer? v)    (str v)
    (number? v)     (str v)
    (map? v)        (str "{" (str/join "," (map (fn [k] (str "\"" (json-escape-utf8 (str k)) "\":"
                                                             (canonical-json-utf8 (get v k))))
                                                (sort (keys v)))) "}")
    (sequential? v) (str "[" (str/join "," (map canonical-json-utf8 v)) "]")
    :else (throw (ex-info "canonical-json-utf8: unsupported value" {:value v}))))

(defn record-cid
  "Deterministic gov.dataset record CID: locator + sha256 content digest (G5 provenance)."
  [rec]
  (let [digest (subs (sha256-hex (canonical-json-utf8 rec)) 0 24)
        fy     (get rec "fiscalYear" "0")
        rid    (get rec "recordId" "unknown")
        sensor (get rec "sourceSensor" "unknown")]
    (str "gov.dataset.budgetRecord:" sensor ":" fy ":" rid "#" digest)))

(def ^:private valid-record-kinds #{"appropriation" "obligation" "outlay" "subaward"})

(defn- as-long
  "Coerce a String or Number fiscalYear value to a long. `(long ...)` alone throws
  ClassCastException on a String (e.g. a budgetRecord where fiscalYear arrived as \"2024\"
  rather than 2024) -- gov.dataset.budgetRecord sources are not guaranteed to type it
  consistently, so normalize-record must accept either."
  [v]
  (long (if (string? v) #?(:clj (Long/parseLong v) :cljs (js/parseInt v 10)) v)))

(defn normalize-record
  "One ledger line from a budgetRecord. Pure; carries its own CID (G5)."
  [rec]
  (let [kind (get rec "recordKind")]
    (when-not (contains? valid-record-kinds kind)
      (throw (ex-info (str "unknown recordKind " (pr-str kind) " (budgetRecord lexicon enum)") {:kind kind})))
    (let [amount (get rec "amountLocal")]
      (when-not (and (integer? amount) (>= amount 0))
        (throw (ex-info (str "amountLocal must be a non-negative integer (minor units), got " (pr-str amount))
                        {:amount amount})))
      {"cid"              (record-cid rec)
       "recordKind"       kind
       "jurisdiction"     (get rec "jurisdiction" "jpn")
       "programName"      (get rec "programName" "")
       "programCode"      (get rec "programCode" "")
       "amountLocal"      amount
       "currencyIso4217"  (get rec "currencyIso4217" "JPY")
       "fiscalYear"       (as-long (get rec "fiscalYear" 0))
       "recipientName"    (get rec "recipientName" "")
       "recipientLocalId" (get rec "recipientLocalId" "")
       "recipientLei"     (get rec "recipientLei" "")
       "awardDateUtc"     (get rec "awardDateUtc" "")
       "sourceUrl"        (get rec "sourceUrl" "")
       "stateAlignedFlag" (boolean (get rec "stateAlignedFlag" false))})))

(defn build-ledger
  "Ingest budgetRecords → a budget ledger grouped by (programCode, fiscalYear).
  Group metadata is from the FIRST line that creates the group; appropriations/outlays keep
  record (append) order."
  [records]
  (let [lines (mapv normalize-record records)
        groups (reduce
                (fn [groups ln]
                  (let [key (str (get ln "programCode") "|" (get ln "fiscalYear"))
                        g   (get groups key
                                 {"programCode"    (get ln "programCode")
                                  "programName"    (get ln "programName")
                                  "fiscalYear"     (get ln "fiscalYear")
                                  "jurisdiction"   (get ln "jurisdiction")
                                  "appropriations" []
                                  "outlays"        []})
                        kind (get ln "recordKind")
                        g   (cond
                              (= kind "appropriation") (update g "appropriations" conj ln)
                              (contains? #{"outlay" "obligation" "subaward"} kind) (update g "outlays" conj ln)
                              :else g)]
                    (assoc groups key g)))
                {}
                lines)]
    {"lines" lines "groups" groups}))

(defn load-seed
  "Read a gov-fiscal seed JSON file → its budgetRecords list (file I/O edge)."
  [path]
  (let [doc (load-json path)]
    (cond
      (and (map? doc) (contains? doc "records")) (get doc "records")
      (sequential? doc)                          doc
      :else                                      [])))
