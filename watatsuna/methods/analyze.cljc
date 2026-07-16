(ns watatsuna.methods.analyze
  "analyze.cljc — watatsuna 綿津綱 submarine-cable network resilience analyzer.
  1:1 Clojure port of `methods/analyze.py` (ADR-2606012600).

  Reads a kotoba-EDN cable graph (:cable/* systems, :station/* landing stations,
  :cable.link/* incidence, :cable.seg/* chokepoint-traversing segments, :cable.fault/*
  observed bulletins) and emits, AGGREGATE-FIRST:

    1. a resilience report (intel-report.md) — where the world's submarine capacity
       concentrates onto a chokepoint / single point of failure, framed toward
       redundancy + diversity-routing + faster repair.
    2. the derived resilience datoms (cable-criticality.kotoba.edn), flagged :derived —
       never re-ingested as authoritative fact.

  CONSTITUTIONAL framing (watatsumi N8 + Charter Rider §2(d)): this is a RESILIENCE map,
  NEVER a target-list. Output ranks chokepoints by fragility so the network can be made
  MORE robust; it does NOT identify where to cut. Sabotage intent is never asserted;
  fault :kind mirrors only the public bulletin's own classification.

  CONVENTIONS (root CLAUDE.md): kebab keyword keys in Clojure code; Python ':…' keyword
  strings (incl. all :cable/* / :station/* / :resilience/* attrs + record keys) stay
  STRINGS; pure fns; file I/O only at the #?(:clj) edge. The minimal EDN reader is the
  ALREADY-PORTED `watatsuna.methods._edn` (keywords kept as ':ns/name' strings) — reused,
  NOT reimplemented.

  PORT FIDELITY NOTES:
   - Python `round(x, n)` = BigDecimal HALF_EVEN over the exact binary value (`py-round`),
     and Python `str(float)` shortest-repr = Java `Double/toString` with integral → 'N.0'
     (`fmt-num`) — together they reproduce the f-string `{round(sum, 2)}` bytes.
   - `cables` / `stations` / `choke_cables` are Python dicts / defaultdicts: first-insertion
     order is preserved so the STABLE sort-by ties exactly the Python iteration order."
  (:require [watatsuna.methods._edn :as edn]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; ── number formatting: Python `str(round(x, n))` ────────────────────────────

(defn py-round
  "Python round(x, ndigits): round-half-to-even over the exact binary value of x."
  [x ndigits]
  #?(:clj (-> (java.math.BigDecimal. (double x))
              (.setScale (int ndigits) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :cljs (let [f (Math/pow 10 ndigits)
                 y (* (double x) f)
                 r (Math/round y)            ;; (cljs lacks exact half-even; adequate for parity tests on :clj)
                 r (if (and (== (Math/abs (- y (Math/floor y))) 0.5)
                            (odd? (long r)))
                     (dec r) r)]
             (/ r f))))

(defn fmt-num
  "Python str() of a float: shortest round-tripping decimal; an integral value keeps one
  trailing zero (str(324.0) → \"324.0\"). Java Double/toString already yields the shortest
  repr; we only special-case the integral form to add the '.0'."
  [d]
  (let [d (double d)]
    (if (and #?(:clj (not (Double/isInfinite d)) :cljs (not (infinite? d)))
             #?(:clj (not (Double/isNaN d)) :cljs (not (js/isNaN d)))
             (== d (Math/floor d)))
      (str (long d) ".0")
      #?(:clj (Double/toString d) :cljs (str d)))))

;; ── classify the flat datom vector into entity buckets ──────────────────────
;; cables / stations are insertion-ordered maps (first-touch on id); links / segs /
;; faults are vectors. Mirrors classify().

(defn classify
  "Bucket a parsed row vector → {:cables :stations :links :segs :faults}. cables / stations
  keep first-insertion order (ordered via ::corder meta = key vector). Mirrors classify()."
  [rows]
  (reduce
   (fn [acc r]
     (if-not (map? r)
       acc
       (cond
         (contains? r ":cable/id")
         (let [k (get r ":cable/id")]
           (-> acc (assoc-in [:cables k] r)
               (update-in [:corder] conj k)))
         (contains? r ":station/id")
         (let [k (get r ":station/id")]
           (-> acc (assoc-in [:stations k] r)
               (update-in [:sorder] conj k)))
         (contains? r ":cable.link/id") (update acc :links conj r)
         (contains? r ":cable.seg/id")  (update acc :segs conj r)
         (contains? r ":cable.fault/id") (update acc :faults conj r)
         :else acc)))
   {:cables {} :stations {} :links [] :segs [] :faults []
    :corder [] :sorder []}
   rows))

;; insertion-order helpers --------------------------------------------------

(defn- corder [c] (::corder (meta c) (keys c)))
(defn- sorder [s] (::sorder (meta s) (keys s)))

(defn- attach-orders
  "Move the classify order vectors into metadata on the cables / stations maps so they can
  travel like Python dict insertion order."
  [{:keys [cables stations links segs faults corder sorder]}]
  {:cables (with-meta cables {::corder corder})
   :stations (with-meta stations {::sorder sorder})
   :links links :segs segs :faults faults})

;; ── analyze ────────────────────────────────────────────────────────────────
;; All accumulators that are iterated for output (choke-*) preserve first-touch order.

(defn analyze
  "Compute the resilience roll-ups. Returns a map mirroring the Python `dict(...)` keys
  (string keys), plus order metadata used by the renderers:
    cap station-cables station-degree station-capacity
    choke-cables choke-load choke-count cable-diversity redundancy-gap
  choke-* maps carry ::choke-order (first-touch chokepoint order); cable-diversity is keyed
  in cables-insertion order; station maps in stations-insertion order."
  [cables stations links segs faults]
  (let [cap-of (fn [c] (let [v (get-in cables [c ":cable/design-capacity-tbps"])]
                         (if (or (nil? v) (false? v)) 0.0 (double v))))
        cap (into {} (map (fn [c] [c (cap-of c)]) (corder cables)))

        ;; per-station: which cables land here (defaultdict set, insertion order irrelevant)
        station-cables (reduce (fn [m lk]
                                 (update m (get lk ":cable.link/station")
                                         (fnil conj #{}) (get lk ":cable.link/cable")))
                               {} links)
        sc (fn [s] (get station-cables s #{}))

        station-degree (into {} (map (fn [s] [s (count (sc s))]) (sorder stations)))
        station-capacity (into {} (map (fn [s]
                                         [s (py-round (reduce + 0.0 (map #(get cap % 0.0) (sc s))) 2)])
                                       (sorder stations)))

        ;; per-chokepoint distinct cables (via segments), then fold station chokepoint tags.
        ;; choke-cables is an ordered defaultdict; track first-touch chokepoint order.
        add-choke (fn [[m order] cp c]
                    (let [seen? (contains? m cp)]
                      [(update m cp (fnil conj #{}) c)
                       (if seen? order (conj order cp))]))
        ;; segments
        [cc1 ord1]
        (reduce (fn [acc sg]
                  (reduce (fn [a cp] (add-choke a cp (get sg ":cable.seg/cable")))
                          acc
                          (or (get sg ":cable.seg/traverses") [])))
                [{} []]
                segs)
        ;; station chokepoint tags: a cable landing behind a chokepoint depends on it
        [choke-cables choke-order]
        (reduce (fn [acc s]
                  (let [meta (get stations s)]
                    (reduce (fn [a cp]
                              (reduce (fn [a2 c] (add-choke a2 cp c)) a (sc s)))
                            acc
                            (or (get meta ":station/chokepoint") []))))
                [cc1 ord1]
                (sorder stations))

        choke-load (into {} (map (fn [cp]
                                   [cp (py-round (reduce + 0.0 (map #(get cap % 0.0) (get choke-cables cp))) 2)])
                                 choke-order))
        choke-count (into {} (map (fn [cp] [cp (count (get choke-cables cp))]) choke-order))

        ;; per-cable diversity: # distinct chokepoints it depends on
        cable-chokes (reduce (fn [m cp]
                               (reduce (fn [m2 c] (update m2 c (fnil conj #{}) cp))
                                       m (get choke-cables cp)))
                             {} choke-order)
        cable-diversity (into {} (map (fn [c] [c (count (get cable-chokes c #{}))]) (corder cables)))

        ;; redundancy gap: landing stations served by a single cable (sorted)
        redundancy-gap (sort (filter (fn [s] (<= (get station-degree s) 1)) (sorder stations)))]
    {"cap" cap
     "station_cables" station-cables
     "station_degree" station-degree
     "station_capacity" station-capacity
     "choke_cables" choke-cables
     "choke_load" (with-meta choke-load {::choke-order choke-order})
     "choke_count" (with-meta choke-count {::choke-order choke-order})
     "cable_diversity" (with-meta cable-diversity {::corder (corder cables)})
     "redundancy_gap" (vec redundancy-gap)}))

;; ── stable sort helpers (mirror Python sorted over insertion-ordered dicts) ──

(defn- choke-order-of [d] (::choke-order (meta d)))

(defn- sorted-choke
  "sorted(a['choke_load'], key=lambda k: -load[k]) — stable over first-touch chokepoint order."
  [a]
  (let [load (get a "choke_load")
        order (choke-order-of load)]
    (sort-by (fn [cp] (- (double (get load cp)))) order)))

;; ── name / lookup helpers ────────────────────────────────────────────────────

(defn- get-meta [d key namekey fallback]
  (let [m (get d key)]
    (if (map? m) (get m namekey fallback) fallback)))

;; ── report rendering (matches render_report's f-strings) ─────────────────────

(defn render-report
  "Render the resilience report markdown (1:1 with render_report). Returns the text with a
  trailing newline."
  [cables stations links segs faults a]
  (let [L (transient [])
        P (fn [s] (conj! L s))]
    (P "# watatsuna 綿津綱 — submarine-cable network resilience report")
    (P "")
    (P (str "> ADR-2606012600 · **aggregate-first** · RESILIENCE map (NOT a target-list; "
            "watatsumi N8 + Charter Rider §2(d)). Sabotage intent never asserted. "
            "All sourcing `:representative` — bounded illustrative seed, not exhaustive coverage."))
    (P "")
    (P (str "- cable systems: **" (count cables) "**  ·  landing stations: **" (count stations) "**  "
            "·  cable↔station links: **" (count links) "**  ·  chokepoint segments: **" (count segs) "**  "
            "·  observed faults: **" (count faults) "**"))
    (let [total-design (py-round (reduce + 0.0 (vals (get a "cap"))) 1)]
      (P (str "- total seeded design capacity: **" (fmt-num total-design) " Tbps**")))
    (P "")

    ;; ── chokepoint concentration ──
    (P "## Chokepoint concentration — single-point-of-failure surface")
    (P "")
    (P (str "Capacity (Σ design Tbps of distinct cables) that depends on each maritime "
            "chokepoint. Higher = more of the world's traffic shares one geographic fate. "
            "**Routed to redundancy + pre-staged repair, never to interdiction.**"))
    (P "")
    (P "| chokepoint | cables | dependent capacity (Tbps) |")
    (P "|---|---:|---:|")
    (doseq [cp (sorted-choke a)]
      (P (str "| `" cp "` | " (get (get a "choke_count") cp) " | " (fmt-num (get (get a "choke_load") cp)) " |")))
    (P "")

    ;; ── landing-station hubs ──
    (P "## Landing-station hubs — convergence points")
    (P "")
    (P "| station | country | cables | landed capacity (Tbps) |")
    (P "|---|---|---:|---:|")
    (let [sd (get a "station_degree")
          scap (get a "station_capacity")
          ordered (sort-by (fn [k] [(- (get sd k)) (- (double (get scap k)))]) (sorder stations))]
      (doseq [s ordered]
        (when-not (= 0 (get sd s))
          (let [st (get stations s)]
            (P (str "| " (get st ":station/name" s) " | " (get st ":station/country" "?")
                    " | " (get sd s) " | " (fmt-num (get scap s)) " |"))))))
    (P "")

    ;; ── brittle cables (low route diversity) ──
    (P "## Most brittle systems — low chokepoint diversity")
    (P "")
    (P (str "Cables depending on the fewest distinct chokepoints carry the highest "
            "correlated-failure risk; candidates for diverse-route augmentation."))
    (P "")
    (P "| cable | chokepoints depended on | design (Tbps) | RFS |")
    (P "|---|---:|---:|---:|")
    (let [div (get a "cable_diversity")
          cap (get a "cap")
          ordered (sort-by (fn [k] [(get div k) (- (double (get cap k)))]) (corder cables))]
      (doseq [c ordered]
        (let [d (get div c)]
          (when-not (= 0 d)
            (let [cb (get cables c)]
              (P (str "| " (get cb ":cable/name" c) " | " d " | " (fmt-num (get cap c)) " | "
                      (get cb ":cable/rfs-year" "?") " |")))))))
    (P "")

    ;; ── redundancy gaps ──
    (P "## Redundancy gaps — single-cable landing stations")
    (P "")
    (let [gap (get a "redundancy_gap")]
      (if (seq gap)
        (doseq [s gap]
          (P (str "- " (get-meta stations s ":station/name" s) " "
                  "(" (get-meta stations s ":station/country" "?") ") — routed to redundancy")))
        (P "- (none in seed)")))
    (P "")

    ;; ── observed faults ──
    (P "## Observed fault bulletins (Datom as-of history; 非終末論 — appended, never overwritten)")
    (P "")
    (P "| fault | cable | kind (bulletin's own) | detected | restored |")
    (P "|---|---|---|---|---|")
    (doseq [f faults]
      (let [cb (get cables (get f ":cable.fault/cable") {})
            restored (get f ":cable.fault/restored-at")]
        (P (str "| `" (get f ":cable.fault/id") "` | " (get cb ":cable/name" (get f ":cable.fault/cable"))
                " | `" (get f ":cable.fault/kind") "` | " (get f ":cable.fault/detected-at" "?")
                " | " (if (or (nil? restored) (false? restored)) "open" restored) " |"))))
    (P "")
    (P "---")
    (P (str "*Generated by `watatsuna/methods/analyze.py`. HONEST: R0 bounded seed; "
            "coordinates rounded to landing town; capacities are public design figures; "
            "chokepoint dependency is charted only for seeded segments. Live planet-scale "
            "ingest is G7 Council+operator gated.*"))
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── derived-datom rendering (matches render_datoms's f-strings) ──────────────

(defn render-datoms
  "Render the DERIVED resilience datoms EDN (1:1 with render_datoms). Returns text with a
  trailing newline."
  [cables stations a]
  (let [L (transient [])
        P (fn [s] (conj! L s))
        sd (get a "station_degree")
        scap (get a "station_capacity")
        div (get a "cable_diversity")]
    (P ";; watatsuna — DERIVED resilience datoms (ADR-2606012600). :derived — NOT authoritative fact.")
    (P ";; Recomputed from the seed graph; do not re-ingest as :authoritative.")
    (P "[")
    ;; chokepoints sorted by -load (stable over first-touch order)
    (doseq [cp (sorted-choke a)]
      (P (str " {:resilience/chokepoint \"" cp "\" :resilience/chokepoint-load " (fmt-num (get (get a "choke_load") cp)) " "
              ":resilience/cable-count " (get (get a "choke_count") cp) " :resilience/derived true}")))
    ;; stations sorted by -degree (stable over stations-insertion order); skip degree 0
    (doseq [s (sort-by (fn [k] (- (get sd k))) (sorder stations))]
      (when-not (= 0 (get sd s))
        (P (str " {:resilience/station \"" s "\" :resilience/station-degree " (get sd s) " "
                ":resilience/station-capacity-tbps " (fmt-num (get scap s)) " :resilience/derived true}"))))
    ;; cables sorted by diversity (stable over cables-insertion order)
    (doseq [c (sort-by (fn [k] (get div k)) (corder cables))]
      (P (str " {:resilience/cable \"" c "\" :resilience/cable-diversity " (get div c) " "
              ":resilience/derived true}")))
    (P "]")
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── -main (mirror `main`; offline default) ──────────────────────────────────

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (io/file (System/getProperty "user.dir") "20-actors" "watatsuna")
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-cable-graph.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           rows (edn/load-edn seed)
           {:keys [cables stations links segs faults]} (attach-orders (classify rows))
           a (analyze cables stations links segs faults)]
       (.mkdirs outdir)
       (spit (io/file outdir "intel-report.md")
             (render-report cables stations links segs faults a))
       (spit (io/file outdir "cable-criticality.kotoba.edn")
             (render-datoms cables stations a))
       (println (str "watatsuna: " (count cables) " cables, " (count stations) " stations, "
                     (count links) " links, " (count segs) " segments, " (count faults) " faults"))
       (let [top (take 3 (sorted-choke a))]
         (println (str "top chokepoints by dependent capacity: "
                       (str/join ", " (map (fn [cp] (str cp " " (fmt-num (get (get a "choke_load") cp)) "Tbps")) top)))))
       (println (str "wrote " (io/file outdir "intel-report.md") " + "
                     (io/file outdir "cable-criticality.kotoba.edn")))
       0)))
