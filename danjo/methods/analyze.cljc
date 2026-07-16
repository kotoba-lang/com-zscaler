(ns danjo.methods.analyze
  "danjo 弾正 — NON-adjudicating discrepancy-observation analyzer (ADR-2605301600).
  1:1 Clojure port of `methods/analyze.py` (R0/R1, offline, stdlib-only).

  Runs the OPEN detector heuristics in the method-pack (v1-jp-seed.json) over a
  PUBLIC procurement corpus and emits danjo.discrepancyObservation records —
  FACTUAL cross-reference patterns over the public record, NEVER a finding of
  wrongdoing. The censor's EYE, never the censor's SWORD. Every observation, by
  construction:
    G4 — :danjo.obs/non-adjudicating = true (no verdict / guilt / wrongdoing
         field is representable; build-observation RAISES if a verdict token
         creeps into a key);
    G5 — sourceRecordCids ≥ 2 (a primary-public-record citation is mandatory);
    G6 — methodNoteCid present (the public audits the open detector).

  House style (mirrors inochi/rasen/tsugite ports): Python ':…' keyword strings
  stay literal strings; map keys are the JSON string keys verbatim; pure fns;
  file/JSON I/O only at #?(:clj) edges. The method content-id reproduces Python's
  `hashlib.sha256(json.dumps(method, sort_keys=True, separators=(',',':'))).hexdigest()[:12]`
  byte-for-byte (canonical JSON with ensure_ascii=True, the Python default)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── sha-256 host seam (mirrors kotoba.datom/*sha256-hex*) ─────────────────────

(def ^:dynamic *sha256-hex*
  "String → lowercase hex sha-256 digest. Rebind on hosts without MessageDigest."
  #?(:clj (fn [^String s]
            (let [d (.digest (java.security.MessageDigest/getInstance "SHA-256")
                             (.getBytes s "UTF-8"))]
              (str/join (map #(let [h (Integer/toHexString (bit-and % 0xff))]
                                (if (= 1 (count h)) (str "0" h) h))
                             d))))
     :default (fn [_] (throw (ex-info "bind danjo.methods.analyze/*sha256-hex* on this host" {})))))

;; ── minimal JSON reader (subset used by the seed corpus + method-pack) ────────
;; Self-contained (no cheshire/data.json dep), so the port runs identically on
;; JVM Clojure / babashka / a pywasm host. Object keys + string values are kept as
;; plain strings; arrays → vectors; objects → maps; numbers → long/double; the
;; literals true/false/null → true/false/nil — exactly Python json.loads' shapes.

(defn- json-tokenize
  "Skip whitespace at index i; returns i at the next significant char."
  [^String s i]
  (loop [i i]
    (if (and (< i (count s)) (contains? #{\space \tab \newline \return} (nth s i)))
      (recur (inc i))
      i)))

(declare json-value)

(defn- json-string
  "Parse a JSON string starting at the opening quote (index i). Returns [str next-i]."
  [^String s i]
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
            \u (let [hex (subs s (+ i 2) (+ i 6))
                     cp #?(:clj (Integer/parseInt hex 16) :cljs (js/parseInt hex 16))]
                 (.append sb (char cp))
                 (recur (+ i 6) sb))
            (do (.append sb e) (recur (+ i 2) sb))))
        :else (do (.append sb c) (recur (inc i) sb))))))

(defn- json-number
  "Parse a JSON number at index i. Returns [num next-i]; long if integral else double."
  [^String s i]
  (let [end (loop [j i]
              (if (and (< j (count s))
                       (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \+ \- \. \e \E} (nth s j)))
                (recur (inc j))
                j))
        tok (subs s i end)]
    [(if (some #{\. \e \E} tok)
       #?(:clj (Double/parseDouble tok) :cljs (js/parseFloat tok))
       #?(:clj (Long/parseLong tok) :cljs (js/parseInt tok 10)))
     end]))

(defn- json-array
  "Parse a JSON array starting at '[' (index i). Returns [vector next-i]."
  [^String s i]
  (loop [i (json-tokenize s (inc i)), out []]
    (if (= (nth s i) \])
      [out (inc i)]
      (let [[v i] (json-value s i)
            out (conj out v)
            i (json-tokenize s i)]
        (if (= (nth s i) \,)
          (recur (json-tokenize s (inc i)) out)
          [out (inc i)])))))

(defn- json-object
  "Parse a JSON object starting at '{' (index i). Returns [map next-i]."
  [^String s i]
  (loop [i (json-tokenize s (inc i)), out {}]
    (if (= (nth s i) \})
      [out (inc i)]
      (let [[k i] (json-string s i)
            i (json-tokenize s i)            ;; skip to ':'
            [v i] (json-value s (json-tokenize s (inc i)))
            out (assoc out k v)
            i (json-tokenize s i)]
        (if (= (nth s i) \,)
          (recur (json-tokenize s (inc i)) out)
          [out (inc i)])))))

(defn- json-value
  "Parse one JSON value at index i. Returns [value next-i]."
  [^String s i]
  (let [i (json-tokenize s i)
        c (nth s i)]
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
     (parse-json (slurp (io/file (str path))))))

;; ── canonical JSON (Python json.dumps(sort_keys=True, separators=(',',':')) parity)
;; ensure_ascii=True (the Python default in analyze.method_cid) → non-ASCII chars are
;; \uXXXX-escaped, exactly reproducing the CID preimage byte-for-byte.

(defn- json-escape-ascii ^String [^String s]
  (str/join
   (mapcat
    (fn [c]
      (let [i #?(:clj (int c) :cljs (.charCodeAt c 0))]
        (cond
          (= c \") (list \\ \")
          (= c \\) (list \\ \\)
          (= c \backspace) (list \\ \b)
          (= c \tab) (list \\ \t)
          (= c \newline) (list \\ \n)
          (= c \formfeed) (list \\ \f)
          (= c \return) (list \\ \r)
          (< i 0x20) (let [h #?(:clj (Integer/toHexString i) :cljs (.toString i 16))]
                       (concat [\\ \u] (seq (str (subs "0000" 0 (- 4 (count h))) h))))
          (> i 0x7e) (let [h #?(:clj (Integer/toHexString i) :cljs (.toString i 16))]
                       (concat [\\ \u] (seq (str (subs "0000" 0 (- 4 (count h))) h))))
          :else (list c))))
    s)))

(defn canonical-json
  "Deterministic JSON for the CID preimage (sort_keys, compact, ensure_ascii=True)."
  ^String [v]
  (cond
    (string? v)     (str "\"" (json-escape-ascii v) "\"")
    (boolean? v)    (if v "true" "false")
    (nil? v)        "null"
    (integer? v)    (str v)
    (number? v)     (str v)
    (map? v)        (str "{" (str/join "," (map (fn [k] (str "\"" (json-escape-ascii (str k)) "\":"
                                                             (canonical-json (get v k))))
                                                (sort (keys v)))) "}")
    (sequential? v) (str "[" (str/join "," (map canonical-json v)) "]")
    :else (throw (ex-info "canonical-json: unsupported value" {:value v}))))

;; ── analyzer ─────────────────────────────────────────────────────────────────

(def forbidden-verdict-fields
  "Fields that would make an observation a VERDICT — must NEVER appear (G4, structural)."
  ["verdict" "guilt" "guilty" "wrongdoing" "finding"
   "culprit" "illegal" "crime" "sanction"])

(defn method-cid
  "Deterministic content id for an open method note (G6 reference).
  Reproduces Python `'method:' + methodId + ':' + sha256(canonical-json)[:12]`."
  [method]
  (let [blob (canonical-json method)]
    (str "method:" (get method "methodId" "?") ":" (subs (*sha256-hex* blob) 0 12))))

(defn- months-between [d1 d2]
  (let [y1 #?(:clj (Long/parseLong (subs d1 0 4)) :cljs (js/parseInt (subs d1 0 4) 10))
        m1 #?(:clj (Long/parseLong (subs d1 5 7)) :cljs (js/parseInt (subs d1 5 7) 10))
        y2 #?(:clj (Long/parseLong (subs d2 0 4)) :cljs (js/parseInt (subs d2 0 4) 10))
        m2 #?(:clj (Long/parseLong (subs d2 5 7)) :cljs (js/parseInt (subs d2 5 7) 10))]
    #?(:clj (Math/abs (long (+ (* (- y2 y1) 12) (- m2 m1))))
       :cljs (js/Math.abs (+ (* (- y2 y1) 12) (- m2 m1))))))

(defn detect-single-bidder-streak
  "Find (authority, awardee) pairs with ≥minConsecutive consecutive single-bid awards
  inside a rolling windowMonths. Returns hit maps {\"authority\" \"awardee\" \"cids\" \"count\"}.
  A FACT about the public record — single-bid procurement is lawful (see false positives).

  Pair iteration follows first-touch insertion order of `by_pair` (Python dict order),
  preserved here by an explicit order vector so hit ordering matches the Python port."
  [records params]
  (let [min-consec (long (get params "minConsecutive" 5))
        window (long (get params "windowMonths" 24))
        require-flag (boolean (get params "requireSingleBidFlag" true))
        ;; group records by [authority awardee], preserving first-touch key order
        {:keys [by-pair order]}
        (reduce (fn [{:keys [by-pair order] :as acc} r]
                  (let [k [(get r "contractingAuthority") (get r "awardeeLei")]]
                    {:by-pair (update by-pair k (fnil conj []) r)
                     :order (if (contains? by-pair k) order (conj order k))}))
                {:by-pair {} :order []}
                records)
        flush-fn
        (fn [auth awardee run-recs]
          (when (and (>= (count run-recs) min-consec)
                     (<= (months-between (get (first run-recs) "awardDate")
                                         (get (last run-recs) "awardDate"))
                         window))
            {"authority" auth "awardee" awardee
             "cids" (mapv #(get % "cid") run-recs) "count" (count run-recs)}))]
    (reduce
     (fn [hits [auth awardee :as key]]
       (let [recs (sort-by #(get % "awardDate" "") (get by-pair key))]
         (loop [rs recs, run [], acc hits]
           (if (empty? rs)
             (if-let [h (flush-fn auth awardee run)] (conj acc h) acc)
             (let [r (first rs)
                   is-single (and (= (get r "bidCount") 1)
                                  (if require-flag (boolean (get r "singleBidFlag" false)) true))]
               (if is-single
                 (recur (rest rs) (conj run r) acc)
                 (let [acc (if-let [h (flush-fn auth awardee run)] (conj acc h) acc)]
                   (recur (rest rs) [] acc))))))))
     []
     order)))

(defn build-observation
  "Assemble a danjo.discrepancyObservation. RAISES (ex-info) if the structural invariants
  (≥2 source cids, method ref present) are not met — non-adjudication is structural."
  [hit method]
  (let [cids (get hit "cids")]
    (when (< (count cids) 2)
      (throw (ex-info "G5: discrepancyObservation requires ≥2 sourceRecordCids"
                      {:gate "G5"})))
    (let [mcid (method-cid method)]
      (when (str/blank? mcid)
        (throw (ex-info "G6: discrepancyObservation requires a methodNoteCid" {:gate "G6"})))
      (let [obs {"type" "danjo.discrepancyObservation"
                 "category" (get method "appliesToCategory" (get method "methodId"))
                 "nonAdjudicatingNotice" true                ;; G4 — always, never a verdict
                 "observedPattern" (str (get hit "count")
                                        " consecutive single-bid awards from "
                                        (get hit "authority") " to " (get hit "awardee")
                                        " within the method window")
                 "sourceRecordCids" cids                     ;; G5 — ≥2
                 "methodNoteCid" mcid                         ;; G6
                 "knownFalsePositiveModes" (get method "knownFalsePositiveModes" []) ;; G4 honesty
                 "sourcing" ":representative"}]
        ;; G4 structural self-check: no verdict field may have crept in.
        (doseq [k (keys obs)]
          (when (some #(str/includes? (str/lower-case k) %) forbidden-verdict-fields)
            (throw (ex-info (str "G4: verdict field " (pr-str k)
                                 " is unrepresentable in a discrepancyObservation")
                            {:gate "G4" :key k}))))
        obs))))

(defn run-all
  "Run every IMPLEMENTED detector over the corpus. (R0/R1: single-bidder-streak.)"
  [corpus methodpack]
  (let [records (get corpus "procurementRecords" [])
        by-id (reduce (fn [m mth] (assoc m (get mth "methodId") mth))
                      {} (get methodpack "methods" []))]
    (if-let [m (get by-id "single-bidder-streak")]
      (let [params (parse-json (get m "thresholdParams" "{}"))]
        (mapv #(build-observation % m) (detect-single-bidder-streak records params)))
      [])))

(defn render-edn
  "Render the danjo-observations.kotoba.edn report (1:1 with render_edn)."
  [observations]
  (let [header [";; danjo-observations.kotoba.edn — danjo.discrepancyObservation records."
                ";; G4 nonAdjudicatingNotice=true (FACT, never a verdict) · G5 ≥2 sourceRecordCids"
                ";; · G6 methodNoteCid. The censor's EYE, never the SWORD. Named-party publication"
                ";; G10 + 1 SBT=1 vote gated. DERIVED :representative. ADR-2605301600."
                "" "["]
        rows (map (fn [o]
                    (let [cids (str/join " " (map #(str "\"" % "\"") (get o "sourceRecordCids")))]
                      (str " {:danjo.obs/category :" (get o "category")
                           " :danjo.obs/non-adjudicating true "
                           ":danjo.obs/pattern \"" (get o "observedPattern") "\" "
                           ":danjo.obs/source-record-cids [" cids "] "
                           ":danjo.obs/method-note-cid \"" (get o "methodNoteCid") "\" "
                           ":danjo.obs/sourcing :representative}")))
                  observations)
        L (concat header rows ["]"])]
    (str (str/join "\n" L) "\n")))

#?(:clj
   (defn -main
     "CLI entry: analyze the seed corpus + method-pack → out/danjo-observations.kotoba.edn.
     Mirrors analyze.main: optional --corpus / --methods / --out (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           ;; actor dir = the parent-of-parent of this file (methods/) when *file* is set
           ;; (script invocation); else fall back to ./20-actors/danjo relative to cwd.
           ;; actor dir = parent-of-parent of this file (methods/) when *file* is a
           ;; resolvable script path; else fall back to ./20-actors/danjo (cwd-relative),
           ;; so the CLI works under `bb analyze.cljc`, `-m`, and require+invoke alike.
           here (let [f (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data")))
                    pp
                    (io/file "20-actors" "danjo")))
           arg-after (fn [flag dflt]
                       (let [i (.indexOf argv flag)]
                         (if (>= i 0) (io/file (nth argv (inc i))) dflt)))
           corpus-f (arg-after "--corpus" (io/file here "data" "corpus.seed.json"))
           methods-f (arg-after "--methods" (io/file here "methods" "v1-jp-seed.json"))
           corpus (load-json corpus-f)
           methods (load-json methods-f)
           obs (run-all corpus methods)]
       (when (some #{"--out"} argv)
         (let [outdir (io/file (nth argv (inc (.indexOf argv "--out"))))]
           (.mkdirs outdir)
           (spit (io/file outdir "danjo-observations.kotoba.edn") (render-edn obs))))
       (println (str "danjo: " (count (get corpus "procurementRecords" []))
                     " procurement records, " (count (get methods "methods" []))
                     " open methods → " (count obs) " discrepancy observation(s)"))
       (doseq [o obs]
         (println (str "  [" (get o "category") "] " (get o "observedPattern")
                       " (" (count (get o "sourceRecordCids")) " sources, non-adjudicating)")))
       0)))
