(ns watari.methods.analyze
  "watari 渡り — live moving-craft (ship + aircraft) situational analyzer.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606041827).

  Reads a kotoba-EDN moving-craft graph (:craft/* identities, :craft.fix/* append-only
  position fixes, :craft.leg/* voyages/flights, :lane/* density units) and emits an
  AGGREGATE-FIRST situational report (intel-report.md) framed toward SAFETY +
  collision-avoidance + congestion-easing + resilience, plus derived movement datoms
  (movement-situation.kotoba.edn), flagged :derived — never re-ingested as fact.

  The canonical 'current position' of a craft is the LATEST :craft.fix (max observed-at).
  The full set of fixes IS the trajectory (非終末論 — appended, never overwritten).
  ISO-8601 UTC timestamps sort lexically, so 'latest' = max string.

  CONSTITUTIONAL (Charter Rider §2(a) force-separation + §2(d); mirrors watatsuna G2):
  this is a SITUATIONAL-AWARENESS map, NEVER a person-surveillance feed and NEVER a
  targeting feed. A craft is a craft, not a person (G4) — the seed carries no person
  identity and the analyzer surfaces none.

  House style (mirrors inochi/danjo ports): Python ':…' keyword strings stay literal
  strings; map keys are the EDN string keys verbatim; pure fns; file I/O only at #?(:clj)
  edges. Accumulation maps carry ::order metadata = first-touch insertion order so the
  stable sort-by ties exactly the Python dict iteration order (byte-parity)."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset: [] {} :kw "str" num bool nil) — mirrors analyze.py's
;; _TOK / _tokens / _atom / _parse faithfully. Keywords kept as ":ns/name" strings (NOT
;; clojure keywords) so the pipeline stays string-keyed, byte-for-byte the same.

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
  "Parse the first top-level form from EDN text (matches load_edn → _parse(_tokens(text)))."
  [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

#?(:clj
   (defn load-edn
     "Read + parse a craft-graph EDN file → vector of forms. File I/O only at this edge."
     [path]
     (read-edn (slurp (str path)))))

;; ── ordered-map: a plain map carrying ::order = first-touch key order (mirrors a Python
;; dict's insertion order, which array-map only preserves ≤8 keys). Used so the stable
;; sort-by ties exactly the Python dict iteration order.

(defn- ordered-map [] ^{::order []} {})

(defn- omap-assoc
  "assoc k→v into an ordered-map, recording k's first-touch position in ::order."
  [m k v]
  (let [had? (contains? m k)
        m' (assoc m k v)]
    (if had?
      (with-meta m' (meta m))
      (with-meta m' (update (meta m) ::order conj k)))))

(defn- omap-keys
  "Keys of an ordered-map in first-touch order (falls back to seq order if no ::order)."
  [m]
  (or (::order (meta m)) (keys m)))

;; ── classify the flat datom vector into entity buckets (1:1 with classify) ───────────

(defn classify
  "Partition rows into [craft fixes legs lanes]: craft + lanes are ordered maps keyed by
  id (first-touch order); fixes + legs are vectors in seed order."
  [rows]
  (loop [rs rows
         craft (ordered-map) fixes [] legs [] lanes (ordered-map)]
    (if (empty? rs)
      [craft fixes legs lanes]
      (let [r (first rs)]
        (cond
          (not (map? r)) (recur (rest rs) craft fixes legs lanes)
          (contains? r ":craft/id")
          (recur (rest rs) (omap-assoc craft (get r ":craft/id") r) fixes legs lanes)
          (contains? r ":craft.fix/id")
          (recur (rest rs) craft (conj fixes r) legs lanes)
          (contains? r ":craft.leg/id")
          (recur (rest rs) craft fixes (conj legs r) lanes)
          (contains? r ":lane/id")
          (recur (rest rs) craft fixes legs (omap-assoc lanes (get r ":lane/id") r))
          :else (recur (rest rs) craft fixes legs lanes))))))

;; ── analyze (1:1 with analyze) ──────────────────────────────────────────────────────

(defn analyze
  "Aggregate roll-ups over the craft graph. Returns a string-keyed result map. Ordered maps
  (latest / lane-craft / lane-kind / lane-load / choke-transit / approach) carry ::order so
  downstream sorts tie in Python dict order."
  [craft fixes legs lanes]
  (let [;; latest fix per craft (max observed-at; ISO-8601 sorts lexically)
        latest
        (reduce
         (fn [m fx]
           (let [c (get fx ":craft.fix/craft")
                 ts (get fx ":craft.fix/observed-at" "")
                 cur (get m c)]
             (if (or (nil? cur) (> (compare ts (get cur ":craft.fix/observed-at" "")) 0))
               (omap-assoc m c fx)
               m)))
         (ordered-map) fixes)

        dataset-latest
        (reduce (fn [acc fx] (let [ts (get fx ":craft.fix/observed-at" "")]
                               (if (> (compare ts acc) 0) ts acc)))
                "" fixes)

        kind-of
        (reduce (fn [m c] (omap-assoc m c (get-in craft [c ":craft/kind"])))
                (ordered-map) (omap-keys craft))

        ;; per-lane live load = distinct craft whose LATEST fix transits the lane, by kind
        {:keys [lane-craft lane-kind]}
        (reduce
         (fn [acc c]
           (let [fx (get latest c)
                 ln (get fx ":craft.fix/lane")]
             (if (nil? ln)
               acc
               (let [k (get kind-of c ":unknown")]
                 (-> acc
                     (update-in [:lane-craft ln] (fnil conj #{}) c)
                     (update-in [:lane-kind ln k] (fnil conj #{}) c))))))
         {:lane-craft {} :lane-kind {}}
         (omap-keys latest))

        ;; first-touch lane order over latest iteration (for lane-load ::order)
        lane-order
        (loop [cs (omap-keys latest), seen #{}, order []]
          (if (empty? cs)
            order
            (let [ln (get-in latest [(first cs) ":craft.fix/lane"])]
              (if (or (nil? ln) (contains? seen ln))
                (recur (rest cs) seen order)
                (recur (rest cs) (conj seen ln) (conj order ln))))))

        lane-load
        (with-meta
          (reduce (fn [m ln] (assoc m ln (count (get lane-craft ln)))) {} lane-order)
          {::order lane-order})

        ;; chokepoint transit: lanes carrying a watatsuna chokepoint keyword
        choke-transit
        (reduce
         (fn [ct ln]
           (let [meta (get lanes ln)
                 cp (get meta ":lane/chokepoint")]
             (if (and cp (contains? lane-craft ln))
               (omap-assoc ct cp (+ (get ct cp 0) (count (get lane-craft ln))))
               ct)))
         (ordered-map) (omap-keys lanes))

        ;; approach congestion: lanes of kind :approach
        approach
        (reduce
         (fn [ap ln]
           (let [meta (get lanes ln)]
             (if (and (= (get meta ":lane/kind") ":approach") (contains? lane-load ln))
               (omap-assoc ap ln (get lane-load ln))
               ap)))
         (ordered-map) (omap-keys lanes))

        ;; fix-count per craft (trail richness)
        trail
        (reduce (fn [m fx] (update m (get fx ":craft.fix/craft") (fnil inc 0))) {} fixes)

        ;; stale tail: latest fix older than dataset latest
        stale
        (sort (for [c (omap-keys latest)
                    :when (< (compare (get-in latest [c ":craft.fix/observed-at"] "")
                                      dataset-latest) 0)]
                c))

        kind-count
        (reduce (fn [m c] (update m (get kind-of c ":unknown") (fnil inc 0)))
                {} (omap-keys craft))]
    {"latest" latest "dataset_latest" dataset-latest "kind_of" kind-of
     "lane_craft" lane-craft "lane_kind" lane-kind "lane_load" lane-load
     "choke_transit" choke-transit "approach" approach "trail" trail
     "stale" stale "kind_count" kind-count}))

;; ── float formatting: Python f"{float}" parity. Parsed Doubles render identically via str
;; for the seed's one-decimal values; ints stay ints; '?' / '—' fall through verbatim.

(defn- pyval
  "Render a value the way Python's f-string would (str of the parsed atom)."
  [v]
  (str v))

(defn- sort-desc-by-val
  "Keys of an ordered-map sorted by -value (STABLE — ties keep first-touch order, mirroring
  Python's sorted(d, key=lambda k: -d[k]) on an insertion-ordered dict)."
  [m]
  (sort-by (fn [k] (- (get m k))) (omap-keys m)))

;; ── report rendering (1:1 with render_report's f-strings) ────────────────────────────

(defn render-report
  [craft fixes legs lanes a]
  (let [P (fn [L s] (conj L s))
        kind-count (get a "kind_count")
        latest (get a "latest")
        stale (get a "stale")
        choke (get a "choke_transit")
        lane-load (get a "lane_load")
        lane-kind (get a "lane_kind")
        kind-of (get a "kind_of")
        approach (get a "approach")
        nv (get kind-count ":vessel" 0)
        na (get kind-count ":aircraft" 0)
        L (-> []
              (P "# watari 渡り — live moving-craft (ship + aircraft) situational report")
              (P "")
              (P (str "> ADR-2606041827 · **aggregate-first** · SITUATIONAL-AWARENESS map (NOT a "
                      "person-surveillance feed, NOT a target-list; Charter Rider §2(a) force-separation "
                      "+ §2(d); mirrors watatsuna G2). A craft is a craft, never a person (G4). "
                      "All sourcing `:representative` — bounded illustrative seed, NOT live coverage."))
              (P "")
              (P (str "- craft: **" (count craft) "** (" nv " vessels · " na " aircraft)  ·  position fixes: "
                      "**" (count fixes) "**  ·  lanes: **" (count lanes) "**  ·  legs: **" (count legs) "**"))
              (P (str "- dataset latest observation: **" (get a "dataset_latest") "**  ·  "
                      "craft current as-of this instant: **" (- (count latest) (count stale)) "** / "
                      (count latest) " (freshness tail: " (count stale) ")"))
              (P "")
              (P "## Chokepoint transit — live vessel/craft concentration")
              (P "")
              (P (str "Distinct craft whose LATEST fix transits each maritime chokepoint. Composes with "
                      "watatsuna's STATIC submarine-cable chokepoint load over the same keywords "
                      "(ADR-2606012600) → one maritime resilience picture. **Routed to safety + "
                      "redundancy, never to interdiction.**"))
              (P "")
              (P "| chokepoint | craft transiting now |")
              (P "|---|---:|"))
        L (reduce (fn [L cp] (P L (str "| `" cp "` | " (get choke cp) " |")))
                  L (sort-desc-by-val choke))
        L (-> L
              (P "")
              (P "## Lane / corridor load — live concentration")
              (P "")
              (P "| lane | kind | craft | vessels | aircraft |")
              (P "|---|---|---:|---:|---:|"))
        L (reduce
           (fn [L ln]
             (let [meta (get lanes ln {})
                   vk (count (get-in lane-kind [ln ":vessel"] #{}))
                   ak (count (get-in lane-kind [ln ":aircraft"] #{}))]
               (P L (str "| " (get meta ":lane/name" ln) " | `" (get meta ":lane/kind" "?") "` "
                         "| " (get lane-load ln) " | " vk " | " ak " |"))))
           L (sort-desc-by-val lane-load))
        L (-> L
              (P "")
              (P "## Port / airport approach congestion")
              (P "")
              (P (str "Craft holding in an approach lane — routed to congestion-easing + arrival "
                      "sequencing + safety. NEVER a targeting output."))
              (P ""))
        L (if (seq approach)
            (let [L (-> L (P "| approach | craft holding |") (P "|---|---:|"))]
              (reduce (fn [L ln]
                        (P L (str "| " (get-in lanes [ln ":lane/name"] ln) " | " (get approach ln) " |")))
                      L (sort-desc-by-val approach)))
            (P L "- (none in seed)"))
        L (-> L
              (P "")
              (P "## Current position snapshot (latest as-of fix per craft)")
              (P "")
              (P "| craft | kind | lat | lon | alt (m) | speed (kn) | as-of |")
              (P "|---|---|---:|---:|---:|---:|---|"))
        snap-order (sort-by (fn [k] [(get kind-of k "") k]) (omap-keys latest))
        L (reduce
           (fn [L c]
             (let [fx (get latest c)
                   cm (get craft c {})
                   label (or (get cm ":craft/name") (get cm ":craft/callsign") c)
                   alt (get fx ":craft.fix/alt-m")]
               (P L (str "| " label " | `" (get cm ":craft/kind" "?") "` | " (pyval (get fx ":craft.fix/lat" "?"))
                         " | " (pyval (get fx ":craft.fix/lon" "?")) " | " (if (nil? alt) "—" (pyval alt))
                         " | " (pyval (get fx ":craft.fix/speed-kn" "?")) " | " (get fx ":craft.fix/observed-at" "?") " |"))))
           L snap-order)
        L (-> L
              (P "")
              (P "## Freshness tail — craft NOT seen in the latest wave (honest gaps)")
              (P "")
              (P (str "Live coverage is never complete. These craft's latest fix predates the dataset "
                      "latest; their position is stale, not current. No fabricated live coverage (G5)."))
              (P ""))
        L (if (seq stale)
            (reduce (fn [L c]
                      (let [fx (get latest c)
                            cm (get craft c {})
                            label (or (get cm ":craft/name") (get cm ":craft/callsign") c)]
                        (P L (str "- " label " — last seen " (get fx ":craft.fix/observed-at" "?")
                                  " (dataset latest " (get a "dataset_latest") ")"))))
                    L stale)
            (P L "- (all craft current in seed)"))
        L (-> L
              (P "")
              (P "---")
              (P (str "*Generated by `watari/methods/analyze.py`. HONEST: R0 bounded `:representative` "
                      "seed; coordinates rounded to ~0.1°; timestamps illustrative, NOT a live capture; "
                      "lane membership is seed-tagged. Live AIS (AISStream) + ADS-B (OpenSky/adsb.fi) "
                      "ingest is G7 Council+operator gated. Public transponder broadcasts only; no "
                      "person-tracking (G4).*")))]
    (str (str/join "\n" L) "\n")))

;; ── derived datoms (1:1 with render_datoms) ──────────────────────────────────────────

(defn render-datoms
  [craft lanes a]
  (let [P (fn [L s] (conj L s))
        choke (get a "choke_transit")
        lane-load (get a "lane_load")
        lane-kind (get a "lane_kind")
        latest (get a "latest")
        stale (get a "stale")
        L (-> []
              (P ";; watari — DERIVED movement-situation datoms (ADR-2606041827). :derived — NOT fact.")
              (P ";; Recomputed from the seed graph; do not re-ingest as :authoritative.")
              (P "["))
        L (reduce (fn [L cp]
                    (P L (str " {:movement/chokepoint \"" cp "\" :movement/chokepoint-transit "
                              (get choke cp) " :movement/derived true}")))
                  L (sort-desc-by-val choke))
        L (reduce (fn [L ln]
                    (let [vk (count (get-in lane-kind [ln ":vessel"] #{}))
                          ak (count (get-in lane-kind [ln ":aircraft"] #{}))]
                      (P L (str " {:movement/lane \"" ln "\" :movement/lane-load " (get lane-load ln)
                                " :movement/vessels " vk " :movement/aircraft " ak " :movement/derived true}"))))
                  L (sort-desc-by-val lane-load))
        L (reduce (fn [L c]
                    (let [fx (get latest c)]
                      (P L (str " {:movement/craft \"" c "\" :movement/stale true "
                                ":movement/last-seen \"" (get fx ":craft.fix/observed-at" "") "\" :movement/derived true}"))))
                  L (sort stale))
        L (P L "]")]
    (str (str/join "\n" L) "\n")))

#?(:clj
   (defn -main
     "CLI entry: analyze seed EDN → out/intel-report.md + out/movement-situation.kotoba.edn."
     [& argv]
     (let [argv (vec argv)
           here (let [f (when (and *file* (not (str/blank? *file*))) (clojure.java.io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (clojure.java.io/file pp "data")))
                    pp
                    (clojure.java.io/file "20-actors" "watari")))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-craft-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           rows (load-edn seed)
           [craft fixes legs lanes] (classify rows)
           a (analyze craft fixes legs lanes)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "intel-report.md") (render-report craft fixes legs lanes a))
       (spit (clojure.java.io/file outdir "movement-situation.kotoba.edn") (render-datoms craft lanes a))
       (println (str "watari: " (count craft) " craft "
                     "(" (get-in a ["kind_count" ":vessel"] 0) " vessels, "
                     (get-in a ["kind_count" ":aircraft"] 0) " aircraft), " (count fixes) " fixes, "
                     (count lanes) " lanes; latest " (get a "dataset_latest")))
       (let [top (take 3 (sort-desc-by-val (get a "choke_transit")))]
         (when (seq top)
           (println (str "top chokepoint transit: "
                         (str/join ", " (map #(str % " " (get-in a ["choke_transit" %])) top))))))
       (println (str "freshness tail: " (count (get a "stale")) " craft stale"))
       (println (str "wrote " (clojure.java.io/file outdir "intel-report.md") " + "
                     (clojure.java.io/file outdir "movement-situation.kotoba.edn")))
       0)))
