(ns kamado.methods.analyze
  "kamado 竈 — refining-graph analyzer (observation + transition + carbon).
  1:1 Clojure port of `methods/analyze.py` (ADR-2606051500).

  Reads a kotoba-EDN refining graph (:refinery/* observed assets, :decommission/* §2(d)
  robotics plans, :synthesis/* closed-loop designs) and emits an aggregate-first report:

    A. observation — refinery/unit/outage registry + transition-readiness rollup (the
       kotoba-native successor to the legacy `oil-refining` Cypher actor). A resilience +
       transition map, NEVER a target-list (G4).
    B. decommission — §2(d) wind-down/convert plans, each screened by the G3 intervention
       guard (convert/decommission/remediate/monitor only).
    C. synthesis — closed-loop designs, each screened by the G1 feedstock guard and scored
       against D3 (net atmospheric carbon Δ ≤ tolerance) via carbon-balance.

  CONSTITUTIONAL gates (mirrored, test-enforced):
    G1 — every :synthesis/feedstock-class is closed-loop; a fossil feedstock raises (ex-info,
         feedstock-guard) — kamado cannot render a fossil-fed design.
    G2 — every synthesis design must pass D3 (carbon-balance). G3 — interventions are
         wind-down/convert only. G4 — non-adjudicating, aggregate-first, never a target-list.

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O at the #?(:clj)
  edge; the embedded EDN reader is shionome's edn.cljc family (same regex tokenizer).
  Byte-parity: `-main` writes the SAME bytes analyze.py writes to out/intel-report.md."
  (:require [clojure.string :as str]
            [kamado.methods.carbon-balance :as cb]
            [kamado.methods.feedstock-guard :refer [screen-feedstock screen-intervention]]))

;; ── minimal EDN reader (subset) — ported from nusa/watatsuna, mirror of analyze.py ──────────
(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn- tokens [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn- atom-of [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{")
      (loop [i i, out (array-map)]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

#?(:clj
   (defn load-edn
     "Read + parse an EDN file at `path`. File I/O only at this edge."
     [path]
     (read-edn (slurp (str path)))))

;; ── classify the flat datom vector ───────────────────────────────────────────
;; Each returned bucket is an insertion-ordered map keyed by id (Python dict order).
(defn classify
  "Return [refineries units outages decoms synths] as ordered maps keyed by id."
  [rows]
  (loop [rows rows
         refineries (array-map) units (array-map) outages (array-map)
         decoms (array-map) synths (array-map)]
    (if (empty? rows)
      [refineries units outages decoms synths]
      (let [r (first rows)]
        (if-not (map? r)
          (recur (rest rows) refineries units outages decoms synths)
          (cond
            (contains? r ":refinery/id")
            (recur (rest rows) (assoc refineries (get r ":refinery/id") r) units outages decoms synths)
            (contains? r ":unit/id")
            (recur (rest rows) refineries (assoc units (get r ":unit/id") r) outages decoms synths)
            (contains? r ":outage/id")
            (recur (rest rows) refineries units (assoc outages (get r ":outage/id") r) decoms synths)
            (contains? r ":decommission/id")
            (recur (rest rows) refineries units outages (assoc decoms (get r ":decommission/id") r) synths)
            (contains? r ":synthesis/id")
            (recur (rest rows) refineries units outages decoms (assoc synths (get r ":synthesis/id") r))
            :else
            (recur (rest rows) refineries units outages decoms synths)))))))

;; ── Counter: a frequency map over a seq of values (preserving first-touch order) ─────────────
(defn- counter
  "Mirror collections.Counter over an iterable: {value count}, first-touch insertion order."
  [vals]
  (reduce (fn [m v] (update m v (fnil inc 0))) (array-map) vals))

;; map the EDN synthesis record → a carbon-balance Pathway
(defn- pathway [sid s]
  (let [apc (or (= (get s ":synthesis/control") ":supervised-autonomy")
                (= (get s ":synthesis/control") ":teleop"))]
    (cb/->pathway sid
                  (get s ":synthesis/feedstock-class")
                  (get s ":synthesis/energy")
                  (get s ":synthesis/product-fate")
                  :apc apc)))

(defn analyze
  "Screen + aggregate. Returns the analysis map (string keys matching the Python dict)."
  [refineries units outages decoms synths]
  ;; G3: every decommission plan is a permitted wind-down/convert intervention
  (doseq [[did d] decoms]
    (screen-intervention (get d ":decommission/intervention") did))
  ;; G5 invariants on every plan
  (let [decom-keyless (every? (fn [d]
                                (and (false? (get d ":decommission/server-held-key"))
                                     (true? (get d ":decommission/outward-gated"))))
                              (vals decoms))
        ;; G1 + G2: every synthesis design has a representable feedstock AND passes D3
        syn-results (reduce (fn [acc [sid s]]
                              (screen-feedstock (get s ":synthesis/feedstock-class") sid) ;; G1, raises on fossil
                              (assoc acc sid (cb/balance (pathway sid s))))
                            (array-map) synths)
        units-by-ref (reduce (fn [acc [uid u]]
                               (update acc (get u ":unit/refinery") (fnil conj []) uid))
                             (array-map) units)]
    {"readiness" (counter (map #(get % ":refinery/transition-readiness") (vals refineries)))
     "status" (counter (map #(get % ":refinery/status") (vals refineries)))
     "units_by_ref" units-by-ref
     "unit_kinds" (counter (map #(get % ":unit/kind") (vals units)))
     "decom_keyless" decom-keyless
     "convert_targets" (counter (map #(get % ":decommission/convert-to") (vals decoms)))
     "syn_results" syn-results
     "syn_pass" (count (filter #(get % "passes_d3") (vals syn-results)))}))

;; ── float formatting (Python f-strings :+.2f) ────────────────────────────────
(defn- fmt-sf [x n]
  (let [s #?(:clj (-> (java.math.BigDecimal. (double x))
                      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
                      (.toPlainString))
             :cljs (.toFixed (double x) n))]
    (if (str/starts-with? s "-") s (str "+" s))))

(defn- get-or
  "r.get(k, default) — Python dict.get."
  [m k default]
  (let [v (get m k ::missing)]
    (if (= v ::missing) default v)))

(defn render
  "Render the intel-report markdown byte-for-byte with analyze.py's render."
  [refineries units outages decoms synths a]
  (let [L (transient [])
        P #(conj! L %)]
    (P "# kamado 竈 — refining observation + transition + carbon report")
    (P "")
    (P (str "> ADR-2606051500 · **aggregate-first** · a resilience + **transition** map, NEVER a "
            "target-list (G4). Observation ≠ operation. The kotoba-native successor to the legacy "
            "`oil-refining` Cypher actor (no RisingWave). All sourcing `:representative`."))
    (P "")
    (P (str "- observed refineries: **" (count refineries) "**  ·  units: **" (count units) "**  "
            "·  outages: **" (count outages) "**  ·  §2(d) plans: **" (count decoms) "**  "
            "·  closed-loop synthesis designs: **" (count synths) "**"))
    (P "")

    (P "## A. Observed assets — status + transition-readiness (face A)")
    (P "")
    (P "| readiness | refineries |")
    (P "|---|---:|")
    (let [readiness (get a "readiness")]
      (doseq [k (sort-by #(or % "") (keys readiness))]
        (P (str "| `" k "` | " (get readiness k) " |"))))
    (P "")
    (P "| refinery | country | operator | status | readiness | units |")
    (P "|---|---|---|---|---|---:|")
    (doseq [rid (sort (keys refineries))]
      (let [r (get refineries rid)]
        (P (str "| " (get-or r ":refinery/name" rid) " | " (get r ":refinery/country") " | "
                "`" (get r ":refinery/operator") "` | `" (get r ":refinery/status") "` | "
                "`" (get r ":refinery/transition-readiness") "` | "
                (count (get-or (get a "units_by_ref") rid [])) " |"))))
    (P "")

    (P "## B. §2(d) decommission / transition robotics (face B)")
    (P "")
    (P (str "Existing fossil assets may only be wound down or converted — the G3 intervention "
            "guard refuses `:expand` / `:restart-fossil`. Every plan is server-keyless (G5) and "
            "outward-gated (G8)."))
    (P "")
    (P (str "- server-keyless + outward-gated on all plans: **"
            (if (get a "decom_keyless") "True" "False") "**"))
    (P "")
    (P "| plan | refinery | intervention | robot | convert-to | principal |")
    (P "|---|---|---|---|---|---|")
    (doseq [did (sort (keys decoms))]
      (let [d (get decoms did)]
        (P (str "| `" did "` | " (get d ":decommission/refinery") " | `"
                (get d ":decommission/intervention") "` | "
                "`" (get d ":decommission/robot-class") "` | `"
                (get d ":decommission/convert-to") "` | "
                "`" (get d ":decommission/principal") "` |"))))
    (P "")

    (P "## C. Closed-loop synthesis — D3 carbon ledger (face C)")
    (P "")
    (P (str "Every design's feedstock is closed-loop carbon (G1; a fossil feedstock is not "
            "representable). D3 = net atmospheric Δ ≤ " (#'cb/fmt-g cb/D3-TOLERANCE) " tCO2e/t. "
            "**" (get a "syn_pass") "/" (count synths) "** designs pass D3."))
    (P "")
    (P "| design | feedstock | energy | fate | net tCO2e/t | D3? |")
    (P "|---|---|---|---|---:|:---:|")
    (doseq [sid (sort (keys synths))]
      (let [s (get synths sid)
            b (get (get a "syn_results") sid)]
        (P (str "| `" sid "` | `" (get s ":synthesis/feedstock-class") "` | "
                "`" (get s ":synthesis/energy") "` | `" (get s ":synthesis/product-fate") "` | "
                "**" (fmt-sf (get b "net") 2) "** | " (if (get b "passes_d3") "✅" "❌") " |"))))
    (P "")
    (P (str "> The fossil baseline (+3.50 tCO2e/t) is **not in this table** — it is not a "
            "representable kamado design. See `carbon_balance.py` for why robotics/control cannot "
            "close that gap (it only trims the ~11% process slice); the feedstock change does."))
    (P "")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI: load seed → classify → analyze → write out/intel-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           methods-dir (delay (-> *file* clojure.java.io/file .getParentFile))
           here (delay (-> @methods-dir .getParentFile))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file @here "data" "seed-refinery-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file @methods-dir "out"))
           rows (load-edn seed)
           [refineries units outages decoms synths] (classify rows)
           a (analyze refineries units outages decoms synths)
           report (render refineries units outages decoms synths a)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md") report)
       (println (str "kamado: " (count refineries) " refineries, " (count decoms) " §2(d) plans, "
                     (count synths) " synthesis designs (" (get a "syn_pass") " pass D3) → " outdir))
       0)))
