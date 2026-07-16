(ns hinagata.wasm.app
  "hinagata 雛形 — kotoba pywasm component entry (ADR-2606111954). 1:1 Clojure port of
  `wasm/app.py`.

  Implements the four `etzhayyim:hinagata/hinagata-actor` world exports (wit/world.wit) as
  module-level functions: analyze / datoms / coverage / envelope. The bounded PUBLIC
  template-commons seed is loaded read-only; G1 (a commons of public templates, never advice)
  holds in WASM, and no-server-key holds (the `envelope` export builds an UNSIGNED record only).

  Reuses the actor's real method siblings (analyze / datom-emit / coverage-report / esign).
  `analyze` + `envelope` return JSON strings (ensure_ascii=False parity — UTF-8 kept literal);
  `datoms` returns EDN, `coverage` returns markdown. Self-contained tiny JSON encoder (no
  cheshire/data.json)."
  (:require [clojure.string :as str]
            [hinagata.methods.analyze :as analyze]
            [hinagata.methods.datom-emit :as datom-emit]
            [hinagata.methods.coverage-report :as coverage]
            [hinagata.methods.esign :as esign]
            #?(:clj [clojure.java.io :as io])))

;; ── tiny JSON encoder (ensure_ascii=False parity: non-ASCII kept literal) ───────
(defn- json-escape ^String [^String s]
  (str/escape s {\" "\\\"" \\ "\\\\"
                 \backspace "\\b" \tab "\\t" \newline "\\n" \formfeed "\\f" \return "\\r"}))

(declare ->json)

(defn- json-entries [pairs]
  (str "{" (str/join "," (map (fn [[k v]] (str "\"" (json-escape (str k)) "\":" (->json v))) pairs)) "}"))

(defn ->json
  "Encode Clojure data → a JSON string. Maps keep their seq/iteration order (use array-map to
  control key order); doubles that are integral print without a decimal point."
  [v]
  (cond
    (nil? v) "null"
    (true? v) "true"
    (false? v) "false"
    (string? v) (str "\"" (json-escape v) "\"")
    (integer? v) (str v)
    (double? v) (if (and (== v (Math/rint v)) (< (Math/abs (double v)) 1e15))
                  (str (long v))
                  (str v))
    (number? v) (str v)
    (map? v) (json-entries (seq v))
    (sequential? v) (str "[" (str/join "," (map ->json v)) "]")
    :else (str "\"" (json-escape (str v)) "\"")))

;; ── tiny JSON reader (used by tests to parse the export JSON) ──────────────────
(declare json-value)

(defn- skip-ws [^String s i]
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

;; ── Python round(v, 4) — banker's rounding (HALF_EVEN) to 4 decimal places ─────
(defn- round4 [^double v]
  (-> (java.math.BigDecimal. v)
      (.setScale 4 java.math.RoundingMode/HALF_EVEN)
      .doubleValue))

;; ── seed loading (host edge) ──────────────────────────────────────────────────
;; *file* is only bound during namespace load (it derefs to nil at call time), so capture the
;; actor dir at load and read the on-disk seed lazily from it (dev/host mode; no embedded module
;; in the .cljc port).
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))

#?(:clj
   (defn- seed-text []
     (slurp (io/file actor-dir "data" "seed-legal-template-graph.kotoba.edn"))))

#?(:clj
   (defn- load* []
     (analyze/load-graph (analyze/read-edn (seed-text)))))

;; ── world exports ──────────────────────────────────────────────────────────────
(defn- rows
  "Mirror app.py rows(d, n): top-n (id, label, round(score,4)) sorted by (-score, id)."
  ([nodes d] (rows nodes d 20))
  ([nodes d n]
   (->> (sort-by (fn [[nid v]] [(- (double v)) nid]) d)
        (take n)
        (mapv (fn [[nid v]]
                (array-map "id" nid
                           "label" (get-in nodes [nid ":lt/label"] nid)
                           "score" (round4 (double v))))))))

(defn analyze
  "JSON export: top-ranked groundedness / reuse / statute_pull rows. Mirrors app.analyze."
  []
  (let [{:keys [nodes edges]} (load*)
        res (analyze/analyze nodes edges)]
    (->json (array-map
             "grounded" (rows nodes (get res "grounded"))
             "reuse" (rows nodes (get res "reuse"))
             "statute_pull" (rows nodes (get res "statute_pull"))))))

(defn datoms
  "EDN export: the kotoba Datom log at tx. Mirrors app.datoms."
  ([] (datoms 1))
  ([tx]
   (let [{:keys [nodes edges]} (load*)
         res (analyze/analyze nodes edges)]
     ;; emit expects the nodes MAP (it calls analyze/node-vals internally for EDN-read
     ;; ordering) — mirror datom_emit/-main, which passes nodes straight through. A prior
     ;; copy passed a [id node] pair-vector here → (vals vector) cast error.
     (datom-emit/emit nodes edges res (long tx)))))

(defn coverage
  "Markdown export: the legal-template-commons coverage report. Mirrors app.coverage."
  []
  (let [{:keys [nodes edges]} (load*)]
    (coverage/report nodes edges)))

(defn envelope
  "JSON export: render a template + build an UNSIGNED esign envelope (no-server-key).
  Mirrors app.envelope."
  [template-id requester-did signers-csv]
  (let [{:keys [nodes edges]} (load*)
        signers (->> (str/split (str signers-csv) #",")
                     (map str/trim)
                     (filter seq)
                     vec)
        doc (esign/render-document template-id nodes edges)
        ;; build-envelope is positional (subject signing-order created-at) — mirror its
        ;; documented arity; a prior copy passed a {:subject …} map as a 4th arg (arity err).
        env (esign/build-envelope doc requester-did signers
                                  (get-in nodes [template-id ":template/title"] "")
                                  "parallel" "1970-01-01T00:00:00Z")]
    (->json (array-map "document" doc "envelope" env))))
