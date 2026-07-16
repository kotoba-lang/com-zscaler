(ns tatara.methods.analyze
  "tatara 鑪 — global manufacturing-plant + logistics-flow analyzer (ADR-2606171800).

  Reads a kotoba-EDN plant graph (:plant/* facilities, :hub/* logistics nodes, :flow/*
  export edges) and emits an AGGREGATE-FIRST resilience report (concentration-report.md)
  framed toward REDUNDANCY / reshoring options, plus derived :concentration/* datoms
  (concentration-situation.kotoba.edn), flagged :concentration/derived — never re-ingested
  as fact.

  Sibling of the 取-concentration mirror lineage (kabuto / tsumugi / inochi): the OUTPUT is
  a map of where the world's manufacturing SITS and how export-dependent each chokepoint is —
  routed to diversification, NEVER a target-list (Charter Rider §2(a)+§2(d)).

  CONSTITUTIONAL: :plant/headcount-est is the DISCLOSED AGGREGATE facility-employment SIZE.
  There is no :worker/* / :person/* attribute; an individual worker is unrepresentable (G4).

  cljc-native (real keywords; clojure.edn reader). Pure fns; file I/O only behind #?(:clj …)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn])))

;; ── load ──────────────────────────────────────────────────────────────────────
(defn read-edn
  "Parse a top-level EDN vector of records from text."
  [text]
  #?(:clj  (edn/read-string text)
     :cljs (cljs.reader/read-string text)))

#?(:clj
   (defn load-edn [path] (read-edn (slurp (str path)))))

;; ── classify ────────────────────────────────────────────────────────────────────
(defn classify
  "Partition rows into {:plants :hubs :flows :chokepoints}, each a vector in seed order."
  [rows]
  (reduce (fn [acc r]
            (cond
              (not (map? r))           acc
              (contains? r :plant/id)  (update acc :plants conj r)
              (contains? r :hub/id)    (update acc :hubs conj r)
              (contains? r :flow/id)   (update acc :flows conj r)
              (contains? r :chokepoint/id) (update acc :chokepoints conj r)
              :else acc))
          {:plants [] :hubs [] :flows [] :chokepoints []}
          rows))

;; ── helpers ─────────────────────────────────────────────────────────────────────
(defn- round2 [x] (/ (Math/round (* (double x) 100.0)) 100.0))

(defn- hhi
  "Herfindahl-Hirschman index over a count-map's shares (0..1)."
  [count-map]
  (let [total (reduce + 0 (vals count-map))]
    (if (zero? total)
      0.0
      (round2 (reduce + 0.0 (map (fn [c] (let [s (/ (double c) total)] (* s s)))
                                 (vals count-map)))))))

(defn- top-entry
  "[key share] of the max-count entry (ties: lexically-smallest key for determinism)."
  [count-map]
  (let [total (reduce + 0 (vals count-map))]
    (if (zero? total)
      [nil 0.0]
      (let [[k c] (first (sort-by (fn [[k c]] [(- c) k]) count-map))]
        [k (round2 (/ (double c) total))]))))

;; ── analyze ─────────────────────────────────────────────────────────────────────
(def ^:const single-source-share 0.6)
(def ^:const single-source-min-plants 3)

(defn analyze
  "Aggregate roll-ups over the plant graph. Returns a keyword-keyed result map."
  [{:keys [plants hubs flows chokepoints]}]
  (let [plant-by-id (into {} (map (juxt :plant/id identity) plants))
        choke-coords (into {} (map (fn [c] [(:chokepoint/keyword c)
                                            {:lat (:chokepoint/lat c) :lon (:chokepoint/lon c)
                                             :name (:chokepoint/name c) :id (:chokepoint/id c)}])
                                   chokepoints))

        sector-country
        (reduce (fn [m p]
                  (update-in m [(:plant/sector p) (:plant/country p)] (fnil inc 0)))
                {} plants)

        sector-count (into {} (map (fn [[s cc]] [s (reduce + 0 (vals cc))]) sector-country))

        sector-stats
        (into {}
              (map (fn [[sector cc]]
                     (let [[tc ts] (top-entry cc)
                           n (reduce + 0 (vals cc))]
                       [sector {:hhi (hhi cc)
                                :plants n
                                :countries (count cc)
                                :top-country tc
                                :top-share ts
                                :single-source (and (>= n single-source-min-plants)
                                                    (> ts single-source-share))}]))
                   sector-country))

        ;; sector capacity rollup (each sector is single-unit by construction)
        sector-capacity
        (reduce (fn [m p]
                  (let [s (:plant/sector p)]
                    (-> m
                        (update-in [s :value] (fnil + 0.0) (double (:plant/capacity-value p 0)))
                        (assoc-in  [s :unit] (:plant/capacity-unit p)))))
                {} plants)

        ;; country rollup: plants + aggregate headcount + aggregate floor area
        country-roll
        (reduce (fn [m p]
                  (let [c (:plant/country p)]
                    (-> m
                        (update-in [c :plants] (fnil inc 0))
                        (update-in [c :headcount] (fnil + 0) (:plant/headcount-est p 0))
                        (update-in [c :floor-m2] (fnil + 0) (:plant/floor-area-m2 p 0)))))
                {} plants)

        ;; chokepoint export-dependence: distinct plants whose flow traverses each chokepoint
        choke-plants
        (reduce (fn [m f]
                  (let [from (:flow/from f)
                        plant? (contains? plant-by-id from)]
                    (if-not plant?
                      m
                      (reduce (fn [m cp] (update m cp (fnil conj #{}) from))
                              m (:flow/via f)))))
                {} flows)

        global-headcount (reduce + 0 (map #(:plant/headcount-est % 0) plants))

        ;; logistics modal + commodity split over the export flows (seed data analyze previously ignored)
        flow-modes (frequencies (map :flow/mode flows))
        flow-commodities (frequencies (map :flow/commodity flows))]
    {:plant-by-id plant-by-id
     :flow-modes flow-modes
     :flow-commodities flow-commodities
     :sector-country sector-country
     :sector-count sector-count
     :sector-stats sector-stats
     :sector-capacity sector-capacity
     :country-roll country-roll
     :choke-plants choke-plants
     :global-headcount global-headcount
     :choke-coords choke-coords
     :n-plants (count plants)
     :n-hubs (count hubs)
     :n-flows (count flows)
     :n-chokepoints (count chokepoints)}))

;; ── ordering helpers for deterministic rendering ─────────────────────────────────
(defn sectors-by-size [a]
  (sort-by (fn [s] [(- (get-in a [:sector-stats s :plants])) (name s)])
           (keys (:sector-stats a))))

(defn countries-by-plants [a]
  (sort-by (fn [c] [(- (get-in a [:country-roll c :plants])) c])
           (keys (:country-roll a))))

(defn chokes-by-load [a]
  (sort-by (fn [cp] [(- (count (get-in a [:choke-plants cp]))) (name cp)])
           (keys (:choke-plants a))))

(defn cross-sector-chokepoints
  "Per-COUNTRY cross-sector view: which countries are the dominant (top) source for the MOST
  sectors at once, and which of those they are the single source for. The per-sector HHI surfaces
  a fragile sector in isolation; this surfaces the country whose dominance SPANS many sectors —
  the highest systemic REDUNDANCY / reshoring priority, which a per-sector ranking cannot show
  (a country that tops only one sector with a huge share is less systemically concentrating than
  one that tops five). Aggregate-first (country↔sector counts only — no facility/worker detail,
  G4); a resilience/diversification map routed to redundancy, NEVER a target-list (G2). Ranked
  [country dominates-count single-source-count sectors] by (single-source desc, dominates desc,
  country)."
  ([a] (cross-sector-chokepoints a 10))
  ([a limit]
   (->> (:sector-stats a)
        (reduce (fn [m [sector st]]
                  (if-let [c (:top-country st)]
                    (-> m
                        (update-in [c :dominates] (fnil conj []) sector)
                        (cond-> (:single-source st)
                          (update-in [c :single-source] (fnil conj []) sector)))
                    m))
                {})
        (map (fn [[c {:keys [dominates single-source]}]]
               [c (count dominates) (count (or single-source [])) (vec (sort-by name dominates))]))
        (sort-by (fn [[c dom ss _]] [(- ss) (- dom) (str c)]))
        (take limit)
        vec)))

(defn sector-supply-diversity
  "Per-sector supply RESILIENCE: the EFFECTIVE number of independent source countries a sector draws
  on = 1 / its country HHI (the inverse-Simpson / Hill number — 4 equal source countries read as 4.0,
  a single-source sector as 1.0). The risk lenses (the single-source flag, cross-sector-chokepoints)
  surface where supply is FRAGILE; this surfaces where it is ROBUST — the resilience benchmark a
  reshoring plan steers a concentrated sector toward (diversification, never interdiction, G2).
  Aggregate-first (sector↔country-diversity, no facility/worker detail, G4). Ranked
  [sector effective-sources hhi countries] by effective-sources descending (most diversified first)."
  ([a] (sector-supply-diversity a 10))
  ([a limit]
   (->> (:sector-stats a)
        (keep (fn [[sector st]]
                (let [hhi (:hhi st)]
                  (when (and hhi (pos? hhi))
                    [sector (/ 1.0 hhi) hhi (:countries st)]))))
        (sort-by (fn [[sector eff _ _]] [(- eff) (name sector)]))
        (take limit)
        vec)))

(defn- fmt-num [x]
  (let [n (long x)] (str/replace (str n) #"\B(?=(\d{3})+(?!\d))" ",")))

(defn- unit-label [u]
  (get {:wafers-300mm-kpm "k 300mm wafers/mo" :vehicles-yr "vehicles/yr"
        :gwh-yr "GWh/yr" :tonnes-yr "t/yr" :units-yr "units/yr"
        :aircraft-yr "aircraft/yr" :gt-yr "GT/yr"} u (when u (name u))))

;; ── report ───────────────────────────────────────────────────────────────────────
(defn render-report [{:keys [plants] :as _g} a]
  (let [P (fn [L s] (conj L s))
        L (-> []
              (P "# tatara 鑪 — global manufacturing concentration & logistics resilience")
              (P "")
              (P (str "> ADR-2606171800 · **aggregate-first** · a RESILIENCE / reshoring map "
                      "(NOT a target-list; Charter Rider §2(a) force-separation + §2(d)). "
                      "All sourcing `:representative` — a bounded illustrative seed, NOT exhaustive "
                      "coverage. Worker figures are DISCLOSED AGGREGATE facility employment (a SIZE), "
                      "never per-worker data (G4)."))
              (P "")
              (P (str "- plants: **" (:n-plants a) "**  ·  logistics hubs: **" (:n-hubs a)
                      "**  ·  export flows: **" (:n-flows a) "**  ·  sectors: **"
                      (count (:sector-stats a)) "**"))
              (P (str "- aggregate charted facility employment: **" (fmt-num (:global-headcount a))
                      "** people across " (:n-plants a) " plants (disclosed aggregate, never per-worker)"))
              (P "")
              (P "## Sector geographic concentration — single-region fragility")
              (P "")
              (P (str "HHI = Herfindahl index of plant-count share by COUNTRY within the sector "
                      "(0 = dispersed → 1 = one country). High HHI + high top-share = a "
                      "diversification / reshoring candidate. **Routed to redundancy, never to "
                      "interdiction.**"))
              (P "")
              (P "| sector | plants | countries | HHI | top country | top share | single-source? |")
              (P "|---|---:|---:|---:|---|---:|:--:|"))
        L (reduce (fn [L s]
                    (let [st (get-in a [:sector-stats s])]
                      (P L (str "| " (name s) " | " (:plants st) " | " (:countries st)
                                " | " (:hhi st) " | " (:top-country st) " | " (:top-share st)
                                " | " (if (:single-source st) "⚠ yes" "no") " |"))))
                  L (sectors-by-size a))
        L (-> L
              (P "")
              (P "## Geographic distribution — plants & aggregate scale by country")
              (P "")
              (P "| country | plants | aggregate employment | aggregate floor area (m²) |")
              (P "|---|---:|---:|---:|"))
        L (reduce (fn [L c]
                    (let [r (get-in a [:country-roll c])]
                      (P L (str "| " c " | " (:plants r) " | " (fmt-num (:headcount r))
                                " | " (fmt-num (:floor-m2 r)) " |"))))
                  L (countries-by-plants a))
        L (-> L
              (P "")
              (P "## Chokepoint export-dependence — composes with watari (live craft) + watatsuna (cable)")
              (P "")
              (P (str "Distinct charted plants whose export flow traverses each maritime chokepoint. "
                      "Shares the SAME keywords as watari `:lane/chokepoint` (live vessel transit) and "
                      "watatsuna `:station/chokepoint` (static cable load): a chokepoint's manufacturing "
                      "export-dependence, its live ship transit, and its cable dependence compose into "
                      "ONE resilience picture — all routed to redundancy + safer routing."))
              (P "")
              (P "| chokepoint | plants export-dependent |")
              (P "|---|---:|"))
        L (reduce (fn [L cp]
                    (P L (str "| `" (name cp) "` | " (count (get-in a [:choke-plants cp])) " |")))
                  L (chokes-by-load a))
        L (-> L
              (P "")
              (P "## Production capacity rollup by sector (disclosed/estimated)")
              (P "")
              (P "| sector | total capacity | unit |")
              (P "|---|---:|---|"))
        L (reduce (fn [L s]
                    (let [cap (get-in a [:sector-capacity s])]
                      (P L (str "| " (name s) " | " (fmt-num (:value cap)) " | "
                                (unit-label (:unit cap)) " |"))))
                  L (sectors-by-size a))
        sort-cm (fn [cm] (sort-by (fn [k] [(- (get cm k)) (name k)]) (keys cm)))
        L (-> L
              (P "")
              (P "## Logistics modal & commodity split (export flows)")
              (P "")
              (P "| transport mode | flows |   | commodity | flows |")
              (P "|---|---:|---|---|---:|"))
        L (let [modes (sort-cm (:flow-modes a))
                comms (sort-cm (:flow-commodities a))
                rows (max (count modes) (count comms))]
            (reduce (fn [L i]
                      (let [m (nth modes i nil) c (nth comms i nil)]
                        (P L (str "| " (if m (str "`" (name m) "` ") "") " | " (if m (get (:flow-modes a) m) "") " |  | "
                                  (if c (str "`" (name c) "` ") "") " | " (if c (get (:flow-commodities a) c) "") " |"))))
                    L (range rows)))
        L (-> L
              (P "")
              (P "## Plant inventory snapshot")
              (P "")
              (P "| plant | operator | country | sector | lat | lon | employment | capacity |")
              (P "|---|---|---|---|---:|---:|---:|---|"))
        L (reduce (fn [L p]
                    (P L (str "| " (:plant/name p) " | `" (:plant/operator p) "` | "
                              (:plant/country p) " | " (name (:plant/sector p)) " | "
                              (:plant/lat p) " | " (:plant/lon p) " | "
                              (fmt-num (:plant/headcount-est p)) " | "
                              (fmt-num (:plant/capacity-value p)) " "
                              (unit-label (:plant/capacity-unit p)) " |")))
                  L (sort-by (fn [p] [(name (:plant/sector p)) (:plant/id p)]) plants))
        L (-> L
              (P "")
              (P "---")
              (P (str "*Generated by `tatara/methods/analyze.cljc`. HONEST: R0 bounded "
                      "`:representative` seed; coordinates rounded to ~0.01° (city/campus, G1); "
                      "headcount/capacity are disclosed-or-estimated round figures, NOT live "
                      "telemetry. Live ingest (company disclosures, GLEIF, OSM facility geometry) "
                      "is G7 Council+operator gated. No per-worker data ever (G4).*")))]
    (str (str/join "\n" L) "\n")))

;; ── derived datoms ────────────────────────────────────────────────────────────────
(defn render-datoms [a]
  (let [P (fn [L s] (conj L s))
        L (-> []
              (P ";; tatara — DERIVED concentration datoms (ADR-2606171800). :concentration/derived — NOT fact.")
              (P ";; Recomputed from the seed graph; never re-ingested as :authoritative.")
              (P "["))
        L (reduce (fn [L s]
                    (let [st (get-in a [:sector-stats s])]
                      (P L (str " {:concentration/sector " s
                                " :concentration/hhi " (:hhi st)
                                " :concentration/top-country " (pr-str (:top-country st))
                                " :concentration/top-share " (:top-share st)
                                " :concentration/single-source " (:single-source st)
                                " :concentration/derived true}"))))
                  L (sectors-by-size a))
        L (reduce (fn [L cp]
                    (P L (str " {:concentration/chokepoint " cp
                              " :concentration/chokepoint-plants " (count (get-in a [:choke-plants cp]))
                              " :concentration/derived true}")))
                  L (chokes-by-load a))
        L (P L "]")]
    (str (str/join "\n" L) "\n")))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (let [f (when (and *file* (not (str/blank? *file*))) (clojure.java.io/file *file*))
                      pp (some-> f .getAbsoluteFile .getParentFile .getParentFile)]
                  (if (and pp (.isDirectory (clojure.java.io/file pp "data")))
                    pp
                    (clojure.java.io/file "20-actors" "tatara")))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-plant-graph.kotoba.edn"))
           outdir (clojure.java.io/file here "out")
           rows (load-edn seed)
           g (classify rows)
           a (analyze g)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "concentration-report.md") (render-report g a))
       (spit (clojure.java.io/file outdir "concentration-situation.kotoba.edn") (render-datoms a))
       (println (str "tatara: " (:n-plants a) " plants, " (:n-hubs a) " hubs, "
                     (:n-flows a) " flows; aggregate employment " (fmt-num (:global-headcount a))))
       (let [top (take 3 (chokes-by-load a))]
         (when (seq top)
           (println (str "top chokepoint export-dependence: "
                         (str/join ", " (map #(str (name %) " "
                                                   (count (get-in a [:choke-plants %])) " plants")
                                             top))))))
       (println (str "wrote " (clojure.java.io/file outdir "concentration-report.md")))
       0)))
