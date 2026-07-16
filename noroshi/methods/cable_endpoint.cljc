(ns noroshi.methods.cable-endpoint
  "noroshi (烽) ↔ watatsuna (綿津綱) optical-network resilience join (ADR-2606051600 §R1c).
  1:1 Clojure port of methods/cable_endpoint.py. Stdlib only.

  watatsuna maps the submarine-cable **medium** (systems, landing stations, the
  chokepoints they sit behind); noroshi designs the **CPO transceiver chips at the
  cable's ends**. This joins the two into **one optical-network resilience picture**:
  every in-service cable terminates on a CPO transceiver at each landing station, so the
  per-cable design capacity becomes a concrete count of noroshi photonic-IC lanes — and
  that transceiver demand **concentrates behind the same maritime chokepoints** watatsuna
  already ranks. The lens is **resilience** (where transceiver capacity piles up behind a
  single chokepoint → diversify routes + pre-stage redundant endpoints), inheriting
  watatsuna's constitutional framing: a resilience map, **NEVER a target-list**
  (watatsumi N8 + Charter Rider §2(d)).

  Reads the watatsuna seed (\":cable/* :station/* :cable.link/*\") and sizes the CPO
  transceiver fleet per cable, per station, and per chokepoint, using the noroshi CPO
  reference link (line rate + energy/bit from link_budget). Deterministic; no live data,
  no hardware (G8/G10).

  CONSTITUTIONAL gates (noroshi CLAUDE.md), ported 1:1 + test-enforced:
    G3 civilian-force-separation — this is sizing arithmetic over a civilian
       optical-network medium; there is no weaponisation / target-selection mode.
    G4 sensing-not-surveillance / object-not-person — the only entities are cables,
       stations, segments and chokepoints; no person / biometric field is representable.
    G1 cleanroom / open-EDA — `:representative` public seed; sims are arithmetic.

  House style: kebab keyword keys in noroshi-internal maps; the watatsuna seed's Python
  ':…' strings stay LITERAL strings (\":cable/id\" etc., the keys we read the seed on);
  pure fns; file I/O only at the #?(:clj) edge; missing-seed → ex-info. Float arithmetic
  is EXACT: round(x, n) reproduces Python's banker's rounding (HALF_EVEN) on the exact
  double; the report bytes match python3 cable_endpoint.py exactly. Portable .cljc."
  (:require [clojure.string :as str]
            [noroshi.methods.link-budget :as lb]
            [noroshi.methods.edn :as edn]))

;; ── default seed: the watatsuna submarine-cable seed (sibling actor) ─────────
#?(:clj
   (def ^:private watatsuna-seed
     (-> (java.io.File. "20-actors/noroshi/methods")
         ;; parents[2] of methods/cable_endpoint.py == 20-actors; then watatsuna/data/…
         (.getCanonicalFile)
         (.getParentFile)        ; noroshi
         (.getParentFile)        ; 20-actors
         (java.io.File. "watatsuna/data/seed-cable-graph.kotoba.edn")
         (.getPath))))

;; ── float helpers reused from link-budget (Python round / repr, byte-identical) ──
(def py-round lb/py-round)
(def py-float-repr lb/py-float-repr)

(defn- ceil-pos
  "math.ceil for a non-negative double, returning a long (matches Python int)."
  [x]
  (long (Math/ceil (double x))))

(defn lanes-for
  "CPO transceiver lanes needed to carry a cable's design capacity at one landing.
  Mirrors `_lanes_for`: max(1, ceil(capacity_tbps * 1000 / line_rate_gbps))."
  [capacity-tbps line-rate-gbps]
  (max 1 (ceil-pos (/ (* (double capacity-tbps) 1000.0) (double line-rate-gbps)))))

(defn- as-double
  "float(x) with a default — mirrors float(c.get(:cable/design-capacity-tbps, 0.0))."
  [x default]
  (cond
    (nil? x) default
    (number? x) (double x)
    :else (double (Double/parseDouble (str x)))))

(defn load-graph
  "Read the watatsuna seed into {cables stations links segments}.
  `cables` is a VECTOR of [id record] pairs in seed (Python dict insertion) order —
  Clojure array-map degrades to an unordered hash-map past 8 entries, so explicit
  ordering is required to keep per-cable iteration + the chokepoint-rank tie order
  byte-identical to Python (::order-stability note in the task). `stations` is keyed
  by id (lookup-only, order irrelevant)."
  ([] (load-graph nil))
  ([seed]
   #?(:clj
      (let [seed (or seed watatsuna-seed)]
        (when-not (.exists (java.io.File. ^String (str seed)))
          (throw (ex-info
                  (str "watatsuna cable seed not found at " seed
                       "; the noroshi×watatsuna join needs the sibling actor's seed "
                       "(20-actors/watatsuna/data/seed-cable-graph.kotoba.edn)")
                  {:seed (str seed) :kind :file-not-found})))
        (let [rows (edn/load-edn seed)]
          (reduce
           (fn [{:keys [cables stations links segments]} r]
             (cond
               (not (map? r)) {:cables cables :stations stations :links links :segments segments}
               (contains? r ":cable/id")
               {:cables (conj cables [(r ":cable/id") r]) :stations stations :links links :segments segments}
               (contains? r ":station/id")
               {:cables cables :stations (assoc stations (r ":station/id") r) :links links :segments segments}
               (contains? r ":cable.link/id")
               {:cables cables :stations stations :links (conj links r) :segments segments}
               (contains? r ":cable.seg/id")
               {:cables cables :stations stations :links links :segments (conj segments r)}
               :else
               {:cables cables :stations stations :links links :segments segments}))
           {:cables [] :stations {} :links [] :segments []}
           rows)))
      :cljs (throw (ex-info "load-graph requires a #?(:clj) file edge" {})))))

(defn size-fleet
  "Size the CPO transceiver fleet per cable / station / chokepoint from the watatsuna graph.
  1:1 with size_fleet — insertion-ordered accumulation preserves the rank tie order."
  ([] (size-fleet nil))
  ([seed]
   (let [g (load-graph seed)
         budget (lb/compute lb/cpo-reference)
         line-rate (:line-rate-gbps lb/cpo-reference)
         energy-pj (:energy-pj-per-bit budget)
         ;; incidence: cable id -> [station ids] (link insertion order)
         incidence (reduce (fn [acc lk]
                             (update acc (lk ":cable.link/cable")
                                     (fnil conj []) (lk ":cable.link/station")))
                           (array-map) (:links g))
         ;; authoritative per-cable chokepoint crossings from :cable.seg/traverses (a set)
         seg-crossings (reduce (fn [acc sg]
                                 (let [cid (get sg ":cable.seg/cable")]
                                   (reduce (fn [a cp] (update a cid (fnil conj #{}) cp))
                                           acc
                                           (or (get sg ":cable.seg/traverses") []))))
                               (array-map) (:segments g))]
     (loop [cs (seq (:cables g))
            per-cable []
            by-station (array-map)
            by-chokepoint (array-map)
            by-chokepoint-seg (array-map)]
       (if-not cs
         (let [rank (fn [d]
                      (->> d
                           (map (fn [[k v]]
                                  {:chokepoint k
                                   :lanes (:lanes v)
                                   :cables (count (:cables v))
                                   :capacity_tbps (py-round (:capacity-tbps v) 1)}))
                           (sort-by :lanes #(compare %2 %1))   ;; stable desc by lanes
                           vec))]
           {:per-cable per-cable
            :by-station-lanes by-station
            :chokepoints (rank by-chokepoint)
            :chokepoints-via-segments (rank by-chokepoint-seg)
            :lane-rate-gbps line-rate
            :energy-pj-per-bit energy-pj})
         (let [[cid c] (first cs)
               status (get c ":cable/status")]
           (if-not (or (= status ":in-service") (nil? status))
             (recur (next cs) per-cable by-station by-chokepoint by-chokepoint-seg)
             (let [cap (as-double (get c ":cable/design-capacity-tbps") 0.0)
                   stns (get incidence cid [])
                   lanes (lanes-for cap line-rate)
                   energy-kw (/ (* energy-pj cap) 1e3)
                   pc (conj per-cable
                            {:cable-id cid
                             :name (get c ":cable/name" cid)
                             :design-capacity-tbps cap
                             :stations stns
                             :lanes-per-endpoint lanes
                             :energy-kw (py-round energy-kw 2)})
                   ;; per station: lanes + station-tag chokepoint attribution
                   [bs bc]
                   (reduce
                    (fn [[bs bc] s]
                      (let [bs (update bs s (fnil + 0) lanes)
                            cps (or (get-in g [:stations s ":station/chokepoint"]) [])
                            bc (reduce
                                (fn [bc cp]
                                  (let [agg (get bc cp {:lanes 0 :cables #{} :capacity-tbps 0.0})]
                                    (assoc bc cp
                                           {:lanes (+ (:lanes agg) lanes)
                                            :cables (conj (:cables agg) cid)
                                            :capacity-tbps (+ (:capacity-tbps agg) cap)})))
                                bc cps)]
                        [bs bc]))
                    [by-station by-chokepoint] stns)
                   ;; authoritative segment crossing attribution
                   bcs (reduce
                        (fn [bcs cp]
                          (let [agg (get bcs cp {:lanes 0 :cables #{} :capacity-tbps 0.0})]
                            (assoc bcs cp
                                   {:lanes (+ (:lanes agg) lanes)
                                    :cables (conj (:cables agg) cid)
                                    :capacity-tbps (+ (:capacity-tbps agg) cap)})))
                        by-chokepoint-seg (get seg-crossings cid #{}))]
               (recur (next cs) pc bs bc bcs)))))))))

(defn report
  "Render the human-readable resilience report (1:1 with cable_endpoint.report)."
  ([] (report nil))
  ([seed]
   (let [f (size-fleet seed)
         lines (transient
                ["# noroshi 烽 × watatsuna 綿津綱 — optical-network resilience (CPO transceivers at the cable ends)"
                 ""
                 (str "Each in-service cable terminates on noroshi CPO transceivers ("
                      (format "%.2f" (double (:lane-rate-gbps f))) " Gb/s/lane, "
                      (py-float-repr (:energy-pj-per-bit f)) " pJ/bit). Demand sized from the watatsuna seed.")
                 ""
                 "## CPO transceiver demand behind each maritime chokepoint (resilience, NOT a target-list)"
                 "| chokepoint | CPO lanes (per end) | cables | aggregate capacity (Tb/s) |"
                 "|---|---|---|---|"])]
     (doseq [cp (:chokepoints f)]
       (conj! lines (str "| " (:chokepoint cp) " | " (:lanes cp) " | " (:cables cp)
                         " | " (py-float-repr (:capacity_tbps cp)) " |")))
     (conj! lines "")
     (conj! lines "## same demand by AUTHORITATIVE segment crossing (:cable.seg/traverses — physical, not landing-tag)")
     (conj! lines "| chokepoint | CPO lanes (per end) | cables | aggregate capacity (Tb/s) |")
     (conj! lines "|---|---|---|---|")
     (doseq [cp (:chokepoints-via-segments f)]
       (conj! lines (str "| " (:chokepoint cp) " | " (:lanes cp) " | " (:cables cp)
                         " | " (py-float-repr (:capacity_tbps cp)) " |")))
     (conj! lines "")
     (conj! lines "## per-cable endpoint transceiver sizing")
     (conj! lines "| cable | capacity (Tb/s) | landings | CPO lanes/end | endpoint power (kW) |")
     (conj! lines "|---|---|---|---|---|")
     (doseq [c (sort-by :lanes-per-endpoint #(compare %2 %1) (:per-cable f))]
       (conj! lines (str "| " (:name c) " | " (py-float-repr (:design-capacity-tbps c))
                         " | " (count (:stations c)) " | " (:lanes-per-endpoint c)
                         " | " (py-float-repr (:energy-kw c)) " |")))
     (conj! lines "")
     (conj! lines (str "> **Composition**: watatsuna ranks where cable *capacity* concentrates behind a chokepoint; "
                       "noroshi turns that into the *transceiver* fleet that must be built and diversified there. "
                       "The output routes to **redundant endpoints + diverse routes + faster repair**, never "
                       "interdiction (watatsuna G2 / watatsumi N8). R0: sizing arithmetic over the `:representative` "
                       "watatsuna seed + the noroshi CPO reference; no live deployment (G8)."))
     (str/join "\n" (persistent! lines)))))

#?(:clj
   (defn -main
     "CLI entry: print the offline resilience report (1:1 with `python3 cable_endpoint.py`)."
     [& _argv]
     (println (report))
     0))
