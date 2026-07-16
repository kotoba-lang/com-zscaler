(ns shiori.methods.analyze
  "shiori 栞 — edge-primary wellbecoming-detraction analyzer over the detraction graph.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606082100).

  Reads a kotoba-EDN wellbecoming graph (:organism/* nodes + :en/* 縁 over the
  wellbecoming-ontology) and surfaces — aggregate-first, at COHORT scale — where structural
  DETRACTION pressure (precarity / overwork / isolation / addictive-design / debt / housing-
  insecurity / chronic-pain / discrimination / care-deprivation …) concentrates on human
  cohorts (the detraction surface), where RELIEF buffers absorb it, the RELIEF GAP that names
  who is most under-served (the routing signal to ossekai), which structural DRIVERS impose the
  most (the 取-holders, routed to transparency), and which detractors still lack a known relief
  route (the intervention-design gap) — all routed to RELIEF / 救い via a TRANSPARENT intervention.

  CONSTITUTIONAL (read before any change):
    N1 / G2 — edge-primary. karma/detraction lives ONLY on edges (:en/load). A cohort's
      wellbecoming-burden is the INTEGRAL of its incident inbound detraction 縁 (load × disclosed
      severity weight) — computed on READ, never a stored per-cohort/per-person score. There is no
      :shiori/score-of-cohort.
    G1 — WELLBECOMING-RELIEF map at AGGREGATE / cohort scale, NEVER a per-person affect / happiness
      / sentiment scoring or manipulation engine. No individual records, no per-person mood/affect
      score, no biometric. The bearer is ALWAYS a cohort. Drivers are structural PATTERNS, never
      named orgs/persons. It routes to relief, never to coercion / dark patterns (anti-addictive).
    N3 — non-adjudicating. Detractor-severity bands + cohort burden patterns are DISCLOSED facts
      (OECD/WHO/public wellbeing research), never shiori verdicts.

  House style: Python ':…' keyword strings stay strings (incl. all :organism/* / :en/* attrs);
  pure fns; file I/O only at edges via clojure.java.io. Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: vectors [], maps {}, :keyword, \"string\", num, bool, nil)
;; Mirrors analyze.py's _TOK / _tokens / _atom / _parse faithfully. Keywords are kept as
;; \":ns/name\" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python port.

(def ^:private tok-re
  ;; _TOK = re.compile(r'[\s,]+|;[^\n]*|(\[|\]|\{|\}|"(?:\\.|[^"\\])*"|[^\s,\[\]{}]+)')
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens
  "Lazy seq of significant tokens (group 1 of each tok-re match that captured)."
  [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t)
              (step)
              (cons t (step))))))))))

(defn atom-of
  "Port of _atom: \"…\" → unescaped string; true/false/nil → bool/nil; \":…\" kept as string;
  int → long; else float; else raw string."
  [t]
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

(defn- parse-step
  "Consume one form from the token vector at index i. Returns [value next-i] or
  [end-marker next-i] when a closing ] or } is hit (matching _parse's _END sentinel)."
  [toks i]
  (let [t (nth toks i)
        i (inc i)]
    (cond
      (= t "[")
      (loop [i i, out []]
        (let [[x i] (parse-step toks i)]
          (if (= x end-marker)
            [out i]
            (recur i (conj out x)))))

      (= t "{")
      (loop [i i, out {}]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (assoc out k v))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── disclosed detractor-severity → representative weight (NOT a verdict; mirrors schema)
(def severity-weight
  {":critical" 1.0 ":severe" 0.8 ":moderate" 0.5 ":mild" 0.25})

(def diminish-kinds #{":diminishes"})
(def drive-kinds #{":drives"})
(def relieve-kinds #{":relieves"})
(def route-kinds #{":routes-to"})

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} from a parsed list of EDN forms.
  (`load` is a clojure.core fn — named load-graph; the host edge reads the file.)
  Insertion order of nodes is preserved (ordered map) to match Python dict order."
  [forms]
  (reduce
   (fn [{:keys [nodes edges] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id") (assoc-in acc [:nodes (get f ":organism/id")] f)
       (and (contains? f ":en/from") (contains? f ":en/to"))
       (update acc :edges conj f)
       :else acc))
   {:nodes (array-map) :edges []}
   forms))

#?(:clj
   (defn load-file*
     "Read + parse a wellbecoming EDN graph file → {:nodes :edges}. File I/O only at this edge."
     [path]
     (load-graph (read-edn (slurp (str path))))))

(defn- ->load
  "float(e.get(':en/load', 0.0) or 0.0) — coerce to double, 0.0 on nil/false/missing."
  [e]
  (let [v (get e ":en/load")]
    (if (or (nil? v) (false? v)) 0.0 (double v))))

(defn- ordered-map
  "Wrap a plain map with the first-touch insertion order of its keys (mirroring a Python
  defaultdict). ::order is a vector of keys in first-touch order."
  []
  ^{::order []} {})

(defn- omap-update
  "update an ordered-map: apply f to the value at k (default 0.0 via fnil), recording k's
  first-touch position in ::order metadata."
  [m k f]
  (let [had? (contains? m k)
        m' (update m k (fnil f 0.0))]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-order [d] (::order (meta d)))

(defn- omap-items
  "Items of an ordered-map in first-touch order (falls back to seq order if no ::order)."
  [d]
  (let [order (omap-order d)]
    (if order
      (map (fn [k] [k (get d k)]) order)
      (seq d))))

(defn analyze
  "Edge-primary integrals (computed on read; transient — N1/G2). Returns
   {\"burden\" {cohort v} \"relief\" {cohort v} \"gap\" {cohort v}
    \"imposed\" {src v} \"route_coverage\" {detr v} \"unrouted_detractors\" [nid …]}.

   burden[cohort]    = Σ incident inbound :diminishes load × disclosed severity weight
   relief[cohort]    = Σ incident inbound :relieves load
   gap[cohort]       = burden − relief   (>0 = under-served; routing signal to ossekai)
   imposed[src]      = Σ outbound :drives + :diminishes load (the 取-holder driver/detractor)
   route_cov[detr]   = Σ outbound :routes-to load (0 = intervention-design gap)

   Accumulation maps carry ::order metadata = first-touch insertion order, so the stable sort
   in `rank` ties exactly the Python defaultdict iteration order."
  [nodes edges]
  (let [[burden relief imposed route-cov]
        (loop [es edges
               burden (ordered-map) relief (ordered-map)
               imposed (ordered-map) route-cov (ordered-map)]
          (if (empty? es)
            [burden relief imposed route-cov]
            (let [e (first es)
                  kind (get e ":en/kind")
                  load- (->load e)
                  src (get e ":en/from")
                  dst (get e ":en/to")]
              (cond
                (contains? diminish-kinds kind)
                (let [sev (get-in nodes [src ":detractor/severity"])
                      w (get severity-weight sev 0.5)]
                  (recur (rest es)
                         (omap-update burden dst #(+ % (* load- w)))
                         relief
                         (omap-update imposed src #(+ % load-))
                         route-cov))

                (contains? drive-kinds kind)
                (recur (rest es)
                       burden relief
                       (omap-update imposed src #(+ % load-))
                       route-cov)

                (contains? relieve-kinds kind)
                (recur (rest es)
                       burden
                       (omap-update relief dst #(+ % load-))
                       imposed route-cov)

                (contains? route-kinds kind)
                (recur (rest es)
                       burden relief imposed
                       (omap-update route-cov src #(+ % load-)))

                :else
                (recur (rest es) burden relief imposed route-cov)))))
        ;; gap follows burden's first-touch insertion order (Python: for cid,b in burden.items())
        gap (reduce (fn [g cid]
                      (omap-update g cid (fn [_] (- (get burden cid)
                                                    (get relief cid 0.0)))))
                    (ordered-map)
                    (omap-order burden))
        ;; detractors with detraction but no relief route = intervention-design gap (sorted)
        unrouted (sort
                  (for [nid (keys nodes)
                        :let [n (get nodes nid)]
                        :when (and (= ":detractor" (get n ":organism/kind"))
                                   (> (get imposed nid 0.0) 0)
                                   (= 0.0 (get route-cov nid 0.0)))]
                    nid))]
    {"burden" burden
     "relief" relief
     "gap" gap
     "imposed" imposed
     "route_coverage" route-cov
     "unrouted_detractors" (vec unrouted)}))

(defn gap-drivers
  "Which structural DETRACTORS' harm lands most on the UNDER-SERVED. `analyze` gives each cohort's
  relief-gap (burden − relief, > 0 = under-served) and `imposed` ranks detractors by total load; this
  WEIGHTS each detractor's :diminishes load by the relief-gap of the cohort it lands on — surfacing
  the detractors whose harm concentrates on the cohorts with the LEAST relief (the highest
  structural-relief priority), which neither the raw imposed-load nor the per-cohort gap shows alone.
  Cohort-aggregate; the ranked 取-holder is a STRUCTURAL detractor (a pattern, never a person/org, G1);
  a relief map routed structural-first (danjo/tsumugi for transparency, ossekai for relief), never
  per-person affect / manipulation. Returns [detractor gap-weighted-harm label] by weighted harm
  descending (only detractors that reach an under-served cohort)."
  ([analysis nodes edges] (gap-drivers analysis nodes edges 20))
  ([analysis nodes edges limit]
   (let [gap (get analysis "gap" {})]
     (->> edges
          (reduce (fn [m e]
                    (if (contains? diminish-kinds (get e ":en/kind"))
                      (let [g (double (get gap (get e ":en/to") 0.0))
                            load (double (or (get e ":en/load") 0.0))]
                        (if (pos? g)
                          (update m (get e ":en/from") (fnil + 0.0) (* load g))
                          m))
                      m))
                  {})
          (filter (fn [[_ v]] (pos? v)))
          (sort-by (fn [[d v]] [(- v) (str d)]))
          (map (fn [[d v]] [d v (get-in nodes [d ":organism/label"] d)]))
          (take limit)
          vec))))

(defn rank
  "Top-`limit` (id, label, value) rows of d, sorted by -value only (STABLE — ties keep
  first-touch insertion order, mirroring Python's `sorted(d.items(), key=lambda kv: -kv[1])`
  on an insertion-ordered dict)."
  ([d nodes] (rank d nodes 20))
  ([d nodes limit]
   (->> (sort-by (fn [[_ v]] (- v)) (omap-items d))
        (take limit)
        (mapv (fn [[nid v]]
                [nid (get-in nodes [nid ":organism/label"] nid) v])))))

;; ── report rendering (matches report_md's f-strings) ────────────────────────

(defn- fmt3 [v] (format "%.3f" (double v)))

(defn- fmt3+
  "Python f'{g:+.3f}' — always-signed, 3 fraction digits."
  [v]
  (let [v (double v)
        s (format "%.3f" (Math/abs v))]
    (str (if (or (neg? v) (and (zero? v) (= (Double/doubleToRawLongBits v)
                                            (Double/doubleToRawLongBits -0.0))))
           "-" "+")
         s)))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn- count-kind [nodes k]
  (count (filter #(= k (get % ":organism/kind")) (vals nodes))))

(defn report-md
  "Render the wellbecoming relief-gap report markdown (1:1 with report_md)."
  [nodes edges res]
  (let [n-coh (count-kind nodes ":cohort")
        n-detr (count-kind nodes ":detractor")
        n-drv (count-kind nodes ":driver")
        n-mit (count-kind nodes ":mitigator")
        auth (count (filter #(= ":authoritative" (get % ":organism/sourcing")) (vals nodes)))
        L (transient [])]
    (conj! L "# shiori 栞 — wellbecoming relief-gap report (cohort-aggregate)\n")
    (conj! L (str "> **G1 — WELLBECOMING-RELIEF map, NEVER a per-person affect/manipulation engine.** "
                  "Cohort scale only; no individual records, no per-person happiness/mood/affect score, "
                  "no biometric. Drivers are structural PATTERNS, never named orgs/persons. The 取-holder "
                  "is the detractor/driver; the bearer is the cohort; the routing is RELIEF (救い) — a "
                  "TRANSPARENT Wellbecoming intervention carried by ossekai, never coercion / dark "
                  "patterns (anti-addictive, §1.13). Severity bands are DISCLOSED (N3). Detraction lives "
                  "only on edges, integrated on read (N1).\n"))
    (conj! L (str "**Graph**: " (count nodes) " nodes (" n-coh " cohorts · " n-detr " detractors · "
                  n-drv " drivers · " n-mit " mitigators) · " (count edges) " 縁 · "
                  auth "/" (count nodes) " :authoritative\n"))

    (conj! L "\n## Relief gap — cohorts where detraction most exceeds relief (→ ossekai)\n")
    (conj! L (str "_burden (Σ inbound :diminishes × disclosed severity) − relief (Σ inbound :relieves); "
                  "the routing signal for a transparent intervention._\n"))
    (conj! L "| rank | cohort | burden | relief | relief-gap |")
    (conj! L "|---:|---|---:|---:|---:|")
    (doseq [[i [nid label g]] (map-indexed vector (rank (get res "gap") nodes))]
      (let [b (get (get res "burden") nid 0.0)
            r (get (get res "relief") nid 0.0)]
        (conj! L (str "| " (inc i) " | " label " | " (fmt3 b) " | " (fmt3 r) " | " (fmt3+ g) " |"))))

    (conj! L "\n## Detraction concentration — drivers + detractors imposing the most\n")
    (conj! L (str "_Σ outbound :drives + :diminishes load; the 取-holders (structural patterns), routed "
                  "to transparency (danjo / tsumugi / keizu), never a target-list._\n"))
    (conj! L "| rank | source | kind | imposed-load |")
    (conj! L "|---:|---|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "imposed") nodes 14))]
      (let [n (get nodes nid {})
            kind0 (or (get n ":driver/kind") (get n ":detractor/kind") "—")
            kind (if (or (nil? kind0) (false? kind0)) "—" kind0)]
        (conj! L (str "| " (inc i) " | " label " | " (lstrip-colon (str kind)) " | " (fmt3 v) " |"))))

    (conj! L "\n## Relief buffers — the 守り (what to scale)\n")
    (conj! L "| rank | cohort | relief-buffer |")
    (conj! L "|---:|---|---:|")
    (doseq [[i [nid label v]] (map-indexed vector (rank (get res "relief") nodes 12))]
      (conj! L (str "| " (inc i) " | " label " | " (fmt3 v) " |")))

    (conj! L "\n## Intervention-design gap — detractions with NO known relief route\n")
    (conj! L (str "_a detractor that burdens a cohort but has no :routes-to edge — the next relief to "
                  "design (never a reason to do nothing)._\n"))
    (let [unrouted (get res "unrouted_detractors")]
      (if (seq unrouted)
        (doseq [nid unrouted]
          (conj! L (str "- **" (get-in nodes [nid ":organism/label"] nid) "** — no relief route")))
        (conj! L "- _(every detraction in the seed has at least one relief route)_")))

    (conj! L (str "\n---\n_shiori 栞 · ADR-2606082100 · mirror-only · relief-routed · non-adjudicating · "
                  "no-person-scoring · anti-addictive · edge-primary. Live intervention routing is "
                  "G7/Council-gated; ossekai carries, the recipient can always see why (G8).\n"))
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main
     "CLI entry: analyze a seed EDN graph → out/relief-gap-report.md (file I/O at the edge)."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* clojure.java.io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-wellbecoming-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (load-file* seed)
           res (analyze nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "relief-gap-report.md") (report-md nodes edges res))
       (println (str "shiori: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "relief-gap-report.md")))
       (when-let [top (first (rank (get res "gap") nodes 1))]
         (println (str "  top relief-gap: " (nth top 1)
                       " (" (fmt3+ (nth top 2)) ")")))
       0)))
