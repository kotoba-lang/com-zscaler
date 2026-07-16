(ns kasa.methods.ingest
  "kasa 嵩 — ingest cell. 1:1 Clojure port of methods/ingest.py (ADR-2606072000).

  Bridges PUBLIC compute-capacity data points into the `:compute.series/*` + `:compute.obs/*`
  vocabulary, gating every row through the G1 admissibility layer (sources/admissible?). Two
  shapes are accepted offline (data/ingest/*.json):

    • \"rows\"  : {\"source\": \"src.epoch\", \"publisher\": \"epoch-ai\", \"access\": \"open-dataset\",
                \"rows\": [ {\"series\": \"cap.flops.frontier-training.world\", \"year\": 2025,
                           \"value\": 1.0e26, \"sourcing\": \"estimated\",
                           \"method\": \"Epoch AI largest-model training FLOP\"} ]}
    • \"series\": optional new :compute.series definitions (same file, key \"series\": [ {...} ]).

  NETWORK DISCIPLINE (G7 + ADR-2605262400 §7 passive-only):
    - DEFAULT = OFFLINE. Reads pre-downloaded files from data/ingest/*.json (no network).
    - LIVE fetch requires BOTH `KASA_OPERATOR_GATE=1` AND an explicit `--fetch-epoch`. Even
      then it is a single polite request to the public CC-BY Epoch AI dataset, never a scrape.
    - Real reported rows are `:authoritative`; the seed stays `:representative`. Merge keeps the
      more-authoritative source on id collision (authoritative > estimated/representative).

  House style: Python ':…' keyword strings stay strings; pure fns; file/network I/O only at the
  #?(:clj) edges. JSON ingest files are read with a self-contained reader (no third-party dep);
  the seed/output is read/written via the kasa EDN reader sibling."
  (:require [clojure.string :as str]
            [kasa.methods.kasa-edn :as kasa-edn]
            [kasa.methods.sources :as sources]
            [kasa.methods.analyze :as analyze]))

;; RANK = the merge precedence on id collision (1:1 with ingest.py RANK).
(def ^:private RANK
  {":representative" 0 ":estimated" 1 ":synthesized" 0 ":authoritative" 2})

;; ── minimal JSON reader (subset sufficient for rows-shaped ingest files) ───────
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
     "Read + parse a JSON ingest file (file I/O only at this edge)."
     [path]
     (parse-json (slurp (str path)))))

;; ── rows → observations (G1-gated) ─────────────────────────────────────────────

(defn rows-to-obs
  "A \"rows\" ingest file → list of :compute.obs maps. G1-gated by sources/admissible?.

  Mirrors ingest.py rows_to_obs: refuses (throws, the SystemExit analogue) when the
  (publisher, access) is not an admissible public source (Charter Rider §2(e)+§2(c))."
  [obj]
  (let [source (get obj "source")
        publisher (get obj "publisher" "")
        access (get obj "access")]
    (when-not (sources/admissible? publisher access)
      (throw (ex-info (str "refused (G1): publisher " (pr-str publisher) "/access " (pr-str access)
                           " is not an admissible public source (Charter Rider §2(e)+§2(c)). "
                           "Read the press release, never the terminal.")
                      {:publisher publisher :access access})))
    (mapv (fn [r]
            (let [sid (get r "series")
                  year (long (get r "year"))
                  sourcing (str ":" (str/replace (get r "sourcing" "authoritative") #"^:+" ""))]
              (array-map
               ":compute.obs/id" (str "obs." sid "." year)
               ":compute.obs/series" sid
               ":compute.obs/year" year
               ":compute.obs/value" (double (get r "value"))
               ":compute.obs/source" source
               ":compute.obs/method" (get r "method" "")
               ":compute.obs/sourcing" sourcing)))
          (get obj "rows" []))))

;; ── offline bridge ─────────────────────────────────────────────────────────────

#?(:clj
   (defn offline-ingest
     "Bridge any data/ingest/*.json (\"rows\"-shaped) under `here`; collect new series + obs.

     Returns [series obs]. Files are read in sorted name order (1:1 with sorted(os.listdir))."
     [here]
     (let [ingest-dir (clojure.java.io/file here "data" "ingest")]
       (if (.isDirectory ingest-dir)
         (let [files (->> (.listFiles ingest-dir)
                          (map #(.getName %))
                          (filter #(str/ends-with? % ".json"))
                          sort)]
           (reduce (fn [[series obs] fn-]
                     (let [obj (load-json (clojure.java.io/file ingest-dir fn-))]
                       [(into series (get obj "series" []))
                        (into obs (rows-to-obs obj))]))
                   [[] []]
                   files))
         [[] []]))))

;; ── live fetch (G7-gated, single polite request) ───────────────────────────────

(defn fetch-epoch-gate
  "Pure G7 gate check: given the KASA_OPERATOR_GATE env value, nil (gate open) when it's
  exactly \"1\", else a refusal string naming the required env var. Kept pure/host-neutral
  and separate from fetch-epoch's actual System/getenv read + throw so the gate logic is
  testable without env-var mocking."
  [gate-value]
  (when (not= gate-value "1")
    (str "refused: live fetch requires KASA_OPERATOR_GATE=1 (G7 Council+operator). "
         "Offline mode reads data/ingest/*.json.")))

#?(:clj
   (defn fetch-epoch
     "LIVE Epoch AI notable-models CSV fetch — G7-gated, single polite request, CC-BY source.

     Refuses (throws) unless KASA_OPERATOR_GATE=1. Persists raw CSV to data/ingest/; the
     column-schema parse into rows-JSON is R1. Returns [series obs] = [[] []]."
     [here]
     (when-let [refusal (fetch-epoch-gate (System/getenv "KASA_OPERATOR_GATE"))]
       (throw (ex-info refusal {})))
     (let [url "https://epoch.ai/data/notable_ai_models.csv"
           text (let [conn (.openConnection (java.net.URL. url))]
                  (.setRequestProperty conn "User-Agent"
                                       "etzhayyim-kasa research jun@etzhayyim.group")
                  (.setConnectTimeout conn 30000)
                  (.setReadTimeout conn 30000)
                  (with-open [in (.getInputStream conn)]
                    (slurp in :encoding "UTF-8")))
           raw (clojure.java.io/file here "data" "ingest" "epoch-notable-models.csv")]
       (.mkdirs (.getParentFile raw))
       (spit raw text)
       (println (str "kasa ingest: fetched Epoch AI CC-BY dataset (" (count text) " bytes) → " raw " "
                     "(parse into rows-JSON is R1; place a rows-shaped file in data/ingest/ to bridge)."))
       [[] []])))

;; ── merge with the :representative/:estimated seed ─────────────────────────────

(defn merge-with-seed
  "Merge ingested over the seed; more-authoritative wins on id (1:1 with merge_with_seed).

  Group metadata: the seed defines the base graph; each ingested row either creates a new id
  or replaces an existing one when its sourcing rank ≥ the incumbent's. Preserves seed-then-
  ingest first-touch order so the merged vector is byte-stable vs the Python dict iteration."
  [seed series obs]
  (let [seed-pairs (map (fn [row]
                          [(or (get row ":compute.series/id")
                               (get row ":compute.obs/id")
                               (get row ":compute.source/id"))
                           row])
                        seed)
        ;; by-id starts as an insertion-ordered map of the seed.
        by-id0 (reduce (fn [m [rid row]] (assoc m rid row)) (array-map) seed-pairs)]
    (-> (reduce
         (fn [m row]
           (let [rid (or (get row ":compute.series/id") (get row ":compute.obs/id"))
                 old (get m rid)
                 new-rank (RANK (or (get row ":compute.obs/sourcing")
                                    (get row ":compute.series/sourcing")) 0)
                 old-rank (RANK (or (get old ":compute.obs/sourcing")
                                    (get old ":compute.series/sourcing")) -1)]
             (if (or (nil? old) (>= new-rank old-rank))
               (assoc m rid row)
               m)))
         by-id0
         (concat series obs))
        vals
        vec)))

;; ── _v value renderer for the generated merged EDN ──────────────────────────────

(defn- v-render
  "Python _v helper: keyword strings pass through; bool to true/false; str quoted; else repr(v)."
  [v]
  (cond
    (string? v) (if (str/starts-with? v ":") v (str "\"" (str/replace v "\"" "\\\"") "\""))
    (boolean? v) (if v "true" "false")
    (integer? v) (str v)
    (float? v) (analyze/py-repr-float (double v))
    :else (str v)))

(defn render-merged
  "Render the merged graph as the generated kotoba EDN vector (1:1 with ingest.py main's writer)."
  [merged]
  (str ";; kasa — merged compute-capacity graph (seed ⊕ ingested; authoritative wins). GENERATED by ingest.py.\n["
       (str/join "\n"
                 (map (fn [row]
                        (str " {"
                             (str/join " " (map (fn [k] (str k " " (v-render (get row k))))
                                                (or (::order (meta row)) (keys row))))
                             "}"))
                      merged))
       "\n]\n"))

;; ── CLI entry (port of main(); the __main__ demo maps to -main) ────────────────

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed-path (clojure.java.io/file here "data" "seed-compute-capacity.kotoba.edn")
           [series obs] (if (some #{"--fetch-epoch"} argv)
                          (fetch-epoch here)
                          (let [[series obs] (offline-ingest here)
                                n (count obs)]
                            (println (str "kasa ingest (offline): bridged " (count series) " series · "
                                          n " obs from data/ingest/"
                                          (if (zero? n)
                                            " (none present — seed is the graph; drop rows-JSON in data/ingest/)"
                                            "")))
                            [series obs]))
           seed (kasa-edn/read-file seed-path)
           merged (merge-with-seed seed series obs)
           out (clojure.java.io/file here "data" "capacity.merged.kotoba.edn")]
       (spit out (render-merged merged))
       (println (str "  → data/capacity.merged.kotoba.edn (" (count merged) " rows). "
                     "Run analyze.py on it for growth."))
       0)))
