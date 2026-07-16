(ns tsumugi.methods.analyze-banner
  "tsumugi 紡ぎ — (B / 旗 hata) ideology / faction (意識的 power-camp) analyzer.
  1:1 Clojure port of `methods/analyze_banner.py` (ADR-2606092000).

  Reads a kotoba-EDN banner graph (:banner/* standards + :ent/* public entities + :flies/*
  alignment 縁) and computes, EDGE-PRIMARY and AGGREGATE-FIRST, the present-day camps —
  which public entities openly fly which banners, how banners descend from historical
  thought-streams, and which entities BRIDGE camps (pluralism). A mirror of declared public
  alignment, never a ranking of conviction and never a thought-registry.

  THE THOUGHT-POLICING GUARD (read banner-ontology header) — enforced here as ex-info:
    H1 public-declared basis only — :flies/basis ∈ {self-declared,public-stated,voting-record,
       formal-membership}; :inferred/:suspected/:imputed/:alleged RAISE.
    H2 non-adjudicating — threat tokens (:extremist/:過激/…) unrepresentable; RAISE.
    H3 edge-primary — alignment on :flies/* only; no :ent/ideology-score (RAISE if present).
    H4 person-excluded — :flies/who must be institutional/public-seat/self; private RAISE.
    H6 plural — entities may fly many banners; multi-banner entities are BRIDGES, not anomalies.
    H7 sourcing — every :flies carries ≥2 public citations; under-sourced RAISES.

  House style (mirrors inochi/danjo ports): Python ':…' keyword strings stay literal strings
  (incl. all :banner/* / :ent/* / :flies/* attrs + record keys); pure fns; file I/O only at
  the #?(:clj) edge. The minimal EDN reader is ported faithfully from analyze_banner.py's own
  _TOK / _tokens / _atom / _parse. Python round(x,4) = BigDecimal HALF_EVEN over the exact
  binary value (py-round); Python str(float) shortest-repr = Java Double/toString with integral
  → 'N.0' (fmt-num) — together they reproduce the f-string bytes. Dict first-insertion order is
  preserved (::order metadata) so STABLE sorts tie exactly the Python dict iteration order."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── minimal EDN reader ────────────────────────────────────────────────────────────────────
;; Faithful port of analyze_banner.py's _TOK / _tokens / _atom / _parse. Keywords are kept as
;; ":ns/name" strings (NOT clojure keywords) so the whole pipeline stays string-keyed,
;; byte-for-byte the same as the Python.

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
      (loop [i i, out (with-meta {} {::order []})]
        (let [[k i] (parse-step toks i)]
          (if (= k end-marker)
            [out i]
            (let [[v i] (parse-step toks i)]
              (recur i (vary-meta (assoc out k v) update ::order conj k))))))

      (or (= t "]") (= t "}"))
      [end-marker i]

      :else
      [(atom-of t) i])))

(defn read-edn
  "Parse the first top-level form from EDN text (matches read_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── number formatting: Python round(x,4) + str(float) ────────────────────────

(defn py-round
  "Python round(x, ndigits): round-half-to-even over the exact binary value of x."
  [x ndigits]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int ndigits) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (let [f (Math/pow 10 ndigits)
                 y (* (double x) f)
                 r (Math/round y)
                 r (if (and (== (Math/abs (- y (Math/floor y))) 0.5)
                            (odd? (long r)))
                     (dec r) r)]
             (/ r f))))

(defn fmt-num
  "Python str() of a float: shortest round-tripping decimal; an integral value keeps one
  trailing zero (str(1.0) → \"1.0\"). Java Double/toString already yields the shortest repr;
  we only special-case the integral form to add the '.0'."
  [d]
  (let [d (double d)]
    (if (and #?(:clj (not (Double/isInfinite d)) :cljs (not (infinite? d)))
             #?(:clj (not (Double/isNaN d)) :cljs (not (js/isNaN d)))
             (== d (Math/floor d)))
      (str (long d) ".0")
      #?(:clj (Double/toString d) :cljs (str d)))))

;; ── closed vocabs (mirror the ontology :db/allowed) ───────────────────────────────────────
(def banner-kinds [":political-platform" ":doctrinal" ":school-of-thought" ":policy-stance"])
(def ent-standings [":institutional" ":public-seat" ":self"])
(def flies-bases [":self-declared" ":public-stated" ":voting-record" ":formal-membership"])
;; H2 threat tokens + H1 inference tokens + H3 forbidden node attrs
(def threat-tokens #{":extremist" ":radical" ":dangerous" ":過激" ":危険思想"
                     ":反社会" ":terrorist" ":テロ"})
(def infer-tokens #{":inferred" ":suspected" ":imputed" ":alleged"})
(def forbidden-ent-attrs #{":ent/ideology-score" ":ent/conviction" ":ent/leaning"
                           ":ent/loyalty" ":banner/threat-level" ":banner/verdict"})

;; ── python repr() of a value for error messages (k!r / r!r) ──────────────────
;; Only strings and nil flow into the {!r} slots in this module; mirror Python repr.

(defn- py-repr
  "Python repr() for the values that reach error f-strings: str → single-quoted, nil → None."
  [v]
  (cond
    (nil? v) "None"
    (string? v) (str "'" v "'")
    :else (str v)))

;; ── ordered-keys helper (Python dict iteration order) ────────────────────────

(defn- ordered-keys
  "Keys of a map in first-insertion order (via ::order metadata recorded at parse-time;
  falls back to (keys m) if no metadata — e.g. a runtime-built map)."
  [m]
  (or (::order (meta m)) (keys m)))

(defn- validate-banner [b]
  (let [k (get b ":banner/kind")]
    (when (contains? threat-tokens k)
      (throw (ex-info (str "H2 breach: threat token " k " as :banner/kind on " (get b ":banner/id")
                           " (a banner is a standard flown, never a danger-classification)")
                      {})))
    (when-not (some #{k} banner-kinds)
      (throw (ex-info (str "closed-vocab breach: :banner/kind " (py-repr k) " on " (get b ":banner/id")) {})))
    (doseq [a (ordered-keys b)]
      (when (contains? forbidden-ent-attrs a)
        (throw (ex-info (str "H2/H3 breach: forbidden attr " a " on " (get b ":banner/id")) {}))))))

(defn- validate-ent [e]
  (doseq [a (ordered-keys e)]
    (when (contains? forbidden-ent-attrs a)
      (throw (ex-info (str "H3 breach: per-entity score attr " a " on " (get e ":ent/id")
                           " (alignment is edge-primary; no score-of-conviction)")
                      {}))))
  (let [st (get e ":ent/standing")]
    (when-not (some #{st} ent-standings)
      (throw (ex-info (str "H4 breach: :ent/standing " (py-repr st) " on " (get e ":ent/id") " not in "
                           (str "[" (str/join ", " (map #(str "'" % "'") ent-standings)) "]")
                           " (private persons unrepresentable; not a belief registry)")
                      {})))))

(defn- validate-flies [f banners ents]
  (let [basis (get f ":flies/basis")]
    (when (contains? infer-tokens basis)
      (throw (ex-info (str "H1 breach: inference basis " basis " on " (get f ":flies/id")
                           " (only on-the-record public declarations; no imputed ideology)")
                      {})))
    (when-not (some #{basis} flies-bases)
      (throw (ex-info (str "closed-vocab breach: :flies/basis " (py-repr basis) " on " (get f ":flies/id")) {})))
    (let [who (get f ":flies/who")]
      (when-not (contains? ents who)
        (throw (ex-info (str "dangling :flies/who " (py-repr who) " on " (get f ":flies/id")) {})))
      (when-not (some #{(get (get ents who) ":ent/standing")} ent-standings)
        (throw (ex-info (str "H4 breach: " who " is not institutional/public-seat/self") {})))
      (when-not (contains? banners (get f ":flies/banner"))
        (throw (ex-info (str "dangling :flies/banner " (py-repr (get f ":flies/banner")) " on " (get f ":flies/id")) {})))
      (let [srcs (or (get f ":flies/sources") [])]
        (when-not (and (sequential? srcs) (>= (count srcs) 2))
          (throw (ex-info (str "H7 breach: :flies/sources on " (get f ":flies/id") " has <2 public citations") {}))))
      (let [w (get f ":flies/weight")]
        (when-not (and (number? w) (<= 0.0 (double w)) (<= (double w) 1.0))
          (throw (ex-info (str "range breach: :flies/weight " (py-repr w) " on " (get f ":flies/id") " ∉ [0,1]") {})))))))

(defn load-validate
  "load() — read + validate, returning {:banners :ents :flies}. Raises on breach."
  [records]
  (let [{:keys [banners ents flies border eorder]}
        (reduce
         (fn [acc r]
           (cond
             (not (map? r)) acc
             (contains? r ":banner/id")
             (do (validate-banner r)
                 (-> acc (assoc-in [:banners (get r ":banner/id")] r)
                     (update :border conj (get r ":banner/id"))))
             (contains? r ":ent/id")
             (do (validate-ent r)
                 (-> acc (assoc-in [:ents (get r ":ent/id")] r)
                     (update :eorder conj (get r ":ent/id"))))
             (contains? r ":flies/id")
             (update acc :flies conj r)
             :else acc))
         {:banners {} :ents {} :flies [] :border [] :eorder []}
         records)
        banners (with-meta banners {::order border})
        ents (with-meta ents {::order eorder})]
    (doseq [f flies] (validate-flies f banners ents))
    {:banners banners :ents ents :flies flies}))

#?(:clj
   (defn load-file*
     "Read + parse + validate a banner EDN graph file → {:banners :ents :flies}.
     File I/O only at this edge."
     [path]
     (load-validate (read-edn (slurp (str path))))))

(defn- ->w
  "float(f.get(':flies/weight', 0.0)) — coerce to double, 0.0 on missing."
  [f]
  (let [v (get f ":flies/weight")]
    (if (nil? v) 0.0 (double v))))

(defn analyze
  "Compute aggregate-first camps + bridges + genealogy. Deterministic. Returns a map with
   string keys mirroring the Python dict: camps / bridges / genealogy / banner_count /
   ent_count / flies_count."
  [banners ents flies]
  ;; per-banner reach = integral of incident :flies weights (H3 edge-primary). H5: a self
  ;; node's inbound-only banner is disclosure, excluded from the projected-over-others camps.
  (let [banner-order (ordered-keys banners)
        ;; reach = {bid {"weight" w "fliers" [...]}}, ent-banners = {who #{bids}}
        ;; fold flies preserving fliers order; ent-banners records who first-touch order
        init-reach (reduce (fn [m bid] (assoc m bid {"weight" 0.0 "fliers" []}))
                           (array-map) banner-order)
        {:keys [reach ent-banners eb-order]}
        (reduce
         (fn [{:keys [reach ent-banners eb-order]} f]
           (let [bid (get f ":flies/banner")
                 who (get f ":flies/who")
                 inbound-self (and (boolean (get f ":flies/inbound-only"))
                                   (= (get (get ents who) ":ent/standing") ":self"))
                 w (->w f)
                 flier {"who" who
                        "label" (get (get ents who) ":ent/label")
                        "basis" (get f ":flies/basis")
                        "weight" w
                        "inbound_only" inbound-self}
                 reach (-> reach
                           (update-in [bid "weight"] + w)
                           (update-in [bid "fliers"] conj flier))]
             (if inbound-self
               {:reach reach :ent-banners ent-banners :eb-order eb-order}
               (let [had? (contains? ent-banners who)]
                 {:reach reach
                  :ent-banners (update ent-banners who (fnil conj #{}) bid)
                  :eb-order (if had? eb-order (conj eb-order who))}))))
         {:reach init-reach :ent-banners {} :eb-order []}
         flies)
        camps
        (->> banner-order
             (map (fn [bid]
                    (let [d (get reach bid)
                          b (get banners bid)
                          reach-v (py-round (get d "weight") 4)]
                      {"banner" bid
                       "label" (get b ":banner/label")
                       "kind" (get b ":banner/kind")
                       "thought_stream" (get b ":banner/thought-stream")
                       "reach" reach-v
                       "member_count" (count (filter #(not (get % "inbound_only")) (get d "fliers")))
                       "fliers" (sort-by (fn [x] [(- (double (get x "weight"))) (get x "who")])
                                         (get d "fliers"))})))
             ;; key=lambda x: (-x["reach"], x["banner"]) — stable sort by (-reach, banner)
             (sort-by (fn [x] [(- (double (get x "reach"))) (get x "banner")])))
        ;; H6 — bridges: entities flying ≥2 banners (pluralism, surfaced positively)
        bridges
        (->> eb-order
             (keep (fn [who]
                     (let [bs (get ent-banners who)]
                       (when (>= (count bs) 2)
                         {"ent" who
                          "label" (get (get ents who) ":ent/label")
                          "banners" (vec (sort bs))
                          "span" (count bs)}))))
             (sort-by (fn [x] [(- (long (get x "span"))) (get x "ent")])))
        ;; genealogy — banner ← historical thought-stream (ADR-2606061500)
        genealogy
        (->> banner-order
             (keep (fn [bid]
                     (let [b (get banners bid)]
                       (when (get b ":banner/thought-stream")
                         {"banner" bid
                          "label" (get b ":banner/label")
                          "stream" (get b ":banner/thought-stream")}))))
             (sort-by (fn [x] (get x "banner"))))]
    {"camps" (vec camps)
     "bridges" (vec bridges)
     "genealogy" (vec genealogy)
     "banner_count" (count banner-order)
     "ent_count" (count (ordered-keys ents))
     "flies_count" (count flies)}))

;; ── report rendering (matches render_report's f-strings) ─────────────────────

(defn render-report
  "Render the banner-report.md (1:1 with render_report). Returns string with trailing newline."
  [result]
  (let [L (transient
           ["# tsumugi (B / 旗 hata) — declared ideology / faction camps"
            ""
            "> **Mirror of DECLARED public alignment. Non-adjudicating. Person-excluded. Plural.**"
            "> Alignment is EDGE-PRIMARY (no score-of-conviction). Basis is always on-the-record"
            "> (self-declared / public-stated / vote / membership) — never imputed. A banner is a"
            "> standard flown, NEVER a danger-classification. etzhayyim discloses its OWN banner."
            ""
            (str "banners: " (get result "banner_count") " · entities: " (get result "ent_count") " · "
                 "alignments: " (get result "flies_count"))
            ""
            "## Camps by reach (Σ declared-alignment weight)"
            ""
            "| banner | kind | ← stream | reach | members |"
            "|---|---|---|---|---|"])]
    (doseq [c (get result "camps")]
      (conj! L (str "| " (get c "label") " | " (get c "kind") " | "
                    (or (get c "thought_stream") "—") " | "
                    "**" (fmt-num (get c "reach")) "** | " (get c "member_count") " |")))
    (conj! L "")
    (conj! L "## Bridges (H6 — entities flying ≥2 banners = pluralism, not anomaly)")
    (conj! L "")
    (conj! L "| entity | banners | span |")
    (conj! L "|---|---|---|")
    (doseq [b (get result "bridges")]
      (conj! L (str "| " (get b "label") " | " (str/join " · " (get b "banners")) " | " (get b "span") " |")))
    (when (empty? (get result "bridges"))
      (conj! L "| (none) | | |"))
    (conj! L "")
    (conj! L "## Genealogy — present banner ← historical thought-stream (ADR-2606061500)")
    (conj! L "")
    (conj! L "| banner | ← stream |")
    (conj! L "|---|---|")
    (doseq [g (get result "genealogy")]
      (conj! L (str "| " (get g "label") " | " (get g "stream") " |")))
    (str (str/join "\n" (persistent! L)) "\n")))

(defn render-graph-edn
  "Render the banner-graph.kotoba.edn (1:1 with render_graph_edn). Trailing newline."
  [result]
  (let [L (transient
           [";; tsumugi (B / 旗) banner-graph — GENERATED; computed-on-read readouts (H3)."
            ";; reach is a per-BANNER aggregate of edge weights, NOT a per-entity score. No hand-edit."
            "["])]
    (doseq [c (get result "camps")]
      (conj! L (str " {:banner.camp/banner \"" (get c "banner") "\" "
                    ":banner.camp/reach " (fmt-num (get c "reach")) " "
                    ":banner.camp/members " (get c "member_count") "}")))
    (conj! L "]")
    (str (str/join "\n" (persistent! L)) "\n")))

#?(:clj
   (defn -main
     "CLI entry: mirrors analyze_banner.main — optional [seed.edn] [--out OUTDIR].
     args = argv without --flags; seed = args[0] or default; out = ACTOR_DIR/out unless --out."
     [& argv]
     (let [argv (vec argv)
           args (vec (remove #(str/starts-with? % "--") argv))
           here (let [f (when (and *file* (not (str/blank? *file*))) (io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (io/file pp "data")))
                    pp
                    (io/file "20-actors" "tsumugi")))
           seed (if (seq args)
                  (io/file (first args))
                  (io/file here "data" "seed-banner.kotoba.edn"))
           out (if (some #{"--out"} argv)
                 (io/file (nth argv (inc (.indexOf argv "--out"))))
                 (io/file here "out"))
           {:keys [banners ents flies]} (load-file* seed)
           result (analyze banners ents flies)]
       (.mkdirs out)
       (spit (io/file out "banner-report.md") (render-report result))
       (spit (io/file out "banner-graph.kotoba.edn") (render-graph-edn result))
       (let [top (first (get result "camps"))]
         (when top
           (println (str "[tsumugi/banner] top camp: " (get top "label")
                         " (reach " (fmt-num (get top "reach")) ", "
                         (get top "member_count") " members) · "
                         (count (get result "bridges")) " bridges "
                         "→ out/banner-report.md"))))
       0)))
