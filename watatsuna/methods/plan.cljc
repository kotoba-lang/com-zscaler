(ns watatsuna.methods.plan
  "plan.cljc — watatsuna 綿津綱 → watatsumi cable-laying mission planner.
  1:1 Clojure port of `methods/plan.py` (ADR-2606012600).

  The 'watatsuna knows → watatsumi acts' link. Reads the cable graph, computes resilience, and
  emits a watatsumi cable-laying MISSION PLAN that tasks specific robot classes to make the network
  MORE robust:

    :lay-diverse-route   for single-cable landing stations (redundancy gaps).
    :pre-stage-repair    for high chokepoint-load straits.
    :monitor             for brittle systems (single charted chokepoint).

  CONSTITUTIONAL (G2 + watatsumi N8): every recommendation is REDUNDANCY or REPAIR or MONITOR.
  There is NO interdiction/cut output by construction — the plan can only ADD resilience.

  House style: pure fns over the analyze.analyze() result map (string keys); Python ':…' keyword
  strings stay literal strings; file I/O only behind #?(:clj …). Reuses the GOOD sibling .cljc
  ports (analyze + _edn). fmt-num reproduces Python str(float). (The Python `__main__` argparse
  printer is preserved behind #?(:clj …) as -main.)"
  (:require [watatsuna.methods.analyze :as analyze]
            [watatsuna.methods._edn :as edn]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

;; watatsumi cable-laying fleet (data/cable-laying-fleet.kotoba.edn), N8-bound
(def lay-route ["watatsumi.hibiki.survey" "watatsumi.tsuna-suki" "watatsumi.horinuki"
                "watatsumi.tsugite" "watatsumi.funamori.cable-ship"])
(def pre-stage ["watatsumi.tedori" "watatsumi.tsugite" "watatsumi.funamori.cable-ship"])
(def monitor ["watatsumi.kikimimi"])

(def top-chokes 4)  ; pre-stage repair at the N most loaded straits

;; ── helpers ───────────────────────────────────────────────────────────────────

(defn- last-seg
  "Python s.split('.')[-1] — last dotted segment."
  [^String s]
  (last (str/split s #"\." -1)))

(defn- lstrip-colon
  "Python s.lstrip(':') — strip leading colons."
  [^String s]
  (loop [i 0] (if (and (< i (count s)) (= \: (nth s i))) (recur (inc i)) (subs s i))))

(defn- pystr
  "Python str() of a numeric value: a float keeps its shortest repr (324.0), an integer is bare."
  [v]
  (cond
    (integer? v) (str v)
    (number? v)  (analyze/fmt-num v)
    :else        (str v)))

(defn- get-meta
  "Like Python st.get(key, default) where st may be a map (or absent)."
  [m k default]
  (if (and (map? m) (contains? m k)) (get m k) default))

;; ── insertion-order accessors (mirror Python dict iteration order over analyze's maps) ──
(def ^:private analyze-corder :watatsuna.methods.analyze/corder)
(def ^:private analyze-sorder :watatsuna.methods.analyze/sorder)
(def ^:private analyze-choke-order :watatsuna.methods.analyze/choke-order)

(defn- corder [m] (or (get (meta m) analyze-corder) (keys m)))
(defn- sorder [m] (or (get (meta m) analyze-sorder) (keys m)))
(defn- choke-order [m] (or (get (meta m) analyze-choke-order) (keys m)))

;; ── build the plan ──────────────────────────────────────────────────────────

(defn build-plan
  "Compute the watatsumi cable-laying recommendations (1:1 with build_plan). recs are ordered:
  lay-diverse-route (redundancy gaps, by -station_capacity) then pre-stage-repair (top chokes by
  -choke_load) then monitor (cables by cable_diversity, only diversity==1)."
  [cables stations a]
  (let [station-cap (get a "station_capacity")
        choke-load  (get a "choke_load")
        choke-count (get a "choke_count")
        cable-div   (get a "cable_diversity")
        red-gap     (get a "redundancy_gap")
        recs (transient [])
        P (fn [r] (conj! recs r))]
    ;; 1) redundancy gaps → lay a diverse route (priority = capacity at risk)
    (doseq [s (sort-by (fn [k] (- (double (get station-cap k)))) red-gap)]
      (let [st (get stations s)
            cap (get station-cap s)]
        (P {":plan/id" (str "plan.lay." (last-seg s))
            ":plan/kind" ":lay-diverse-route"
            ":plan/target-station" s
            ":plan/priority" cap
            ":plan/rationale" (str (get-meta st ":station/name" s) " is served by a single cable — "
                                   "one fault isolates " (pystr cap) " Tbps. Lay a "
                                   "geographically diverse second landing.")
            ":plan/robots" lay-route
            ":plan/n8-note" "ADD a route; never remove one (N8)."})))

    ;; 2) high chokepoint-load → pre-stage repair (+ note diverse-route precedent)
    (let [top (take top-chokes (sort-by (fn [k] (- (double (get choke-load k)))) (choke-order choke-load)))]
      (doseq [cp top]
        (let [load (get choke-load cp)
              cnt  (get choke-count cp)
              cps  (lstrip-colon cp)]
          (P {":plan/id" (str "plan.repair." cps)
              ":plan/kind" ":pre-stage-repair"
              ":plan/target-chokepoint" cps
              ":plan/priority" load
              ":plan/rationale" (str (pystr load) " Tbps across " cnt " cables "
                                     "depend on " cps ". Pre-stage repair capability for fast "
                                     "restoration; route new builds diversely around it (cf. Bifrost "
                                     "avoiding the South China Sea).")
              ":plan/robots" pre-stage
              ":plan/n8-note" "REPAIR-ONLY staging; Tedori recovers faulted cable under logged G4 quorum."}))))

    ;; 3) brittle systems (single charted chokepoint) → DAS monitor
    (doseq [c (sort-by (fn [k] (get cable-div k)) (corder cable-div))]
      (when (= 1 (get cable-div c))
        (let [cb (get cables c)
              cap (get-meta cb ":cable/design-capacity-tbps" 0.0)]
          (P {":plan/id" (str "plan.monitor." (last-seg c))
              ":plan/kind" ":monitor"
              ":plan/target-cable" c
              ":plan/priority" cap
              ":plan/rationale" (str (get-meta cb ":cable/name" c) " depends on a single charted chokepoint "
                                     "(brittle). Passive DAS watch on its own fibre for early warning.")
              ":plan/robots" monitor
              ":plan/n8-note" "Monitoring only; no location export beyond the cable's own route (G1)."}))))
    (persistent! recs)))

;; ── render markdown (matches render_md's f-strings) ──────────────────────────

(defn render-md
  "Render the plan markdown (1:1 with render_md). Returns text with a trailing newline."
  [recs]
  (let [L (transient [])
        P (fn [s] (conj! L s))]
    (P "# watatsuna 綿津綱 → watatsumi 綿津見 — resilience fleet plan")
    (P "")
    (P (str "> ADR-2606012600 · **redundancy + repair + monitor ONLY** (G2 + watatsumi N8). "
            "No interdiction output by construction. watatsuna knows; watatsumi acts."))
    (P "")
    (P (str "- recommendations: **" (count recs) "**"))
    (P "")
    (doseq [[kind title] [[":lay-diverse-route" "Lay diverse route (close redundancy gaps)"]
                          [":pre-stage-repair" "Pre-stage repair (high chokepoint-load)"]
                          [":monitor" "Monitor brittle systems (DAS)"]]]
      (let [group (filterv (fn [r] (= (get r ":plan/kind") kind)) recs)]
        (when (seq group)
          (P (str "## " title))
          (P "")
          (doseq [r (sort-by (fn [r] (- (double (get r ":plan/priority")))) group)]
            (let [tgt (or (get r ":plan/target-station") (get r ":plan/target-chokepoint")
                          (get r ":plan/target-cable"))
                  robots (str/join ", " (map last-seg (get r ":plan/robots")))]
              (P (str "- **" tgt "** _(priority " (pystr (get r ":plan/priority")) ")_ — "
                      (get r ":plan/rationale")))
              (P (str "  - fleet: `" robots "` · " (get r ":plan/n8-note")))))
          (P ""))))
    (P "---")
    (P (str "*Generated by `watatsuna/methods/plan.py`. HONEST: R0/R2 design-only — "
            "recommendations over a bounded `:representative` seed; no live tasking; real "
            "deployment is Council + operator gated. Fleet acts lay/bury/splice/repair/monitor only.*"))
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── render EDN (matches render_edn's v() helper) ─────────────────────────────

(defn- v
  "render_edn's inner v(): a list → bracketed quoted strings; a :kw-string kept; another string
  quoted; else str()."
  [x]
  (cond
    (sequential? x) (str "[" (str/join " " (map (fn [i] (str "\"" i "\"")) x)) "]")
    (string? x)     (if (str/starts-with? x ":") x (str "\"" x "\""))
    (boolean? x)    (str x)
    (integer? x)    (str x)
    (number? x)     (analyze/fmt-num x)
    :else           (str x)))

;; record-key emission order, mirroring the insertion order of the Python dict literals
(def ^:private plan-key-order
  [":plan/id" ":plan/kind" ":plan/target-station" ":plan/target-chokepoint" ":plan/target-cable"
   ":plan/priority" ":plan/rationale" ":plan/robots" ":plan/n8-note"])

(defn render-edn
  "Render the plan recommendations EDN (1:1 with render_edn). Returns text with a trailing newline.
  Each record emits its keys in the literal-dict insertion order (Python dict.items())."
  [recs]
  (let [L (transient [])
        P (fn [s] (conj! L s))]
    (P ";; watatsuna → watatsumi resilience fleet plan (GENERATED). :plan/* recommendations.")
    (P ";; G2/N8: redundancy + repair + monitor ONLY. ADR-2606012600. DO NOT hand-edit.")
    (P "[")
    (doseq [r recs]
      (let [ks (filter #(contains? r %) plan-key-order)]
        (P (str " {" (str/join " " (map (fn [k] (str k " " (v (get r k)))) ks)) "}"))))
    (P "]")
    (str (str/join "\n" (persistent! L)) "\n")))

;; ── -main (mirror `main`; offline default) ──────────────────────────────────

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (io/file (System/getProperty "user.dir") "20-actors" "watatsuna")
           graph (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                   (io/file (first argv))
                   (let [m (io/file here "data" "cable-graph.merged.kotoba.edn")]
                     (if (.exists m) m (io/file here "data" "seed-cable-graph.kotoba.edn"))))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           rows (edn/load-edn graph)
           {:keys [cables stations links segs faults]} (#'analyze/attach-orders (analyze/classify rows))
           a (analyze/analyze cables stations links segs faults)
           recs (build-plan cables stations a)]
       (.mkdirs outdir)
       (spit (io/file outdir "resilience-plan.md") (render-md recs))
       (spit (io/file outdir "resilience-plan.kotoba.edn") (render-edn recs))
       (let [by (fn [k] (count (filter #(= (get % ":plan/kind") k) recs)))]
         (println (str "watatsuna plan from " (.getName graph) ": " (count recs) " recommendations "
                       "(" (by ":lay-diverse-route") " lay-route · " (by ":pre-stage-repair")
                       " pre-stage-repair · " (by ":monitor") " monitor)"))
         (println (str "wrote " (io/file outdir "resilience-plan.md") " + "
                       (io/file outdir "resilience-plan.kotoba.edn"))))
       0)))
