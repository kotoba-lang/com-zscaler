#!/usr/bin/env bb
;; busshi 物資 — analyze → datoms → coverage (clj-native, pure stdlib).
(ns busshi.methods.analyze
  "busshi 物資 — the observatory's analytical core (ADR-2606161730).

  OBSERVATION ONLY. Given the commodity/materials seed, compute the §2(l)
  multi-generational (子・孫) × wellbecoming RISK axis (ADR-2606161700):
    * producer concentration (top-producer-share + named-HHI lower bound),
    * chokepoint risk level,
    * multi-gen risk (monopoly + carbon-intensity + irreversibility), and
    * the resilience ROUTE (:resilience / :de-monopolization / :restoration).
  Emits append-only EAVT datoms (every derived datom :busshi/derived true +
  :busshi/sourcing) and a markdown RESILIENCE MAP.

  Hard invariants (proven by tests):
    G1  never a trade — no buy/sell/position/signal is computed or emitted.
    G3  a producer SHARE and a price LEVEL are FACTS, never a verdict and never a
        forecast point (no point-forecast attribute exists; mitooshi does dists).
    G5  aggregate-first — no precise mine coordinates; a RESILIENCE map, NEVER a
        target-list (the report says so, in those words)."
  (:require [clojure.string :as str]
            [busshi.methods.busshi-edn :as be]))

;; ── pure analytics ───────────────────────────────────────────────────────────

(defn- named-producers
  "Producer pairs excluding the fragmented :other residual bucket."
  [c]
  (remove (fn [[country _]] (= country :other)) (:producers c)))

(defn top-producer-share
  "Largest single named-producer share (percent). 0 if none named."
  [c]
  (let [ps (named-producers c)]
    (if (seq ps) (apply max (map second ps)) 0)))

(defn named-hhi
  "Herfindahl–Hirschman index over NAMED producer shares, normalized to 0..1.
  A lower bound on concentration (the :other residual is fragmented, excluded)."
  [c]
  (let [shares (map second (named-producers c))]
    (/ (reduce + 0 (map #(* % %) shares)) 10000.0)))

(defn chokepoint-risk
  "Chokepoint level from the top producer share (robust to the :other residual)."
  [top-share]
  (cond
    (>= top-share 60) :critical
    (>= top-share 40) :high
    (>= top-share 25) :moderate
    :else :low))

(defn multigen-risk
  "Multi-gen (子・孫) × wellbecoming risk score ∈ 0..1 — weighted blend of the
  monopoly factor (top-share), carbon-intensity, and irreversibility. NOT a price
  view; a multi-generational footprint score."
  [c]
  (let [monopoly (/ (top-producer-share c) 100.0)
        carbon (double (or (:carbon-intensity c) 0))
        irrev (double (or (:irreversibility c) 0))]
    (+ (* 0.40 monopoly) (* 0.30 carbon) (* 0.30 irrev))))

(defn route
  "Resilience route: the dominant risk driver decides where the observation is
  routed. Never punishment — route-around + restoration."
  [c]
  (let [monopoly (/ (top-producer-share c) 100.0)
        carbon (double (or (:carbon-intensity c) 0))
        irrev (double (or (:irreversibility c) 0))
        env (max carbon irrev)]
    (cond
      (and (>= monopoly 0.40) (>= monopoly env)) :de-monopolization
      (>= env 0.60) :restoration
      :else :resilience)))

(defn analyze-commodity
  "Per-commodity derived observation map (string-keyed, agent-facing)."
  [c]
  (let [ts (top-producer-share c)]
    {"id" (:id c)
     "name" (:name c)
     "class" (:class c)
     "top_producer" (let [ps (named-producers c)]
                      (when (seq ps) (first (apply max-key second ps))))
     "top_producer_share" ts
     "named_hhi" (named-hhi c)
     "chokepoint_risk" (chokepoint-risk ts)
     "multigen_risk" (multigen-risk c)
     "recyclability" (double (or (:recyclability c) 0))
     "route" (route c)
     "sourcing" (or (:sourcing c) :representative)
     "source" (:source c)}))

(defn analyze
  "Full analysis: per-commodity rows + per-class aggregates."
  [commodities]
  (let [rows (mapv analyze-commodity commodities)
        by-class (group-by #(get % "class") rows)
        classes (vec (for [[cls crows] (sort-by (comp name key) by-class)]
                       {"class" cls
                        "count" (count crows)
                        "mean_multigen_risk" (/ (reduce + 0.0 (map #(get % "multigen_risk") crows))
                                                (count crows))
                        "critical_chokepoints" (count (filter #(= (get % "chokepoint_risk") :critical) crows))}))]
    {"commodities" rows "classes" classes}))

(defn cross-commodity-chokepoints
  "Per-PRODUCER cross-commodity view: which producers are the TOP source for the MOST commodities at
  once, weighted by each commodity's multi-gen risk. The per-commodity HHI surfaces one fragile
  commodity; this surfaces the producer whose dominance SPANS many commodities — the systemic §2(l)
  de-monopolization / resilience priority a per-commodity ranking cannot show (a producer topping
  one commodity is less systemically concentrating than one topping five high-multigen-risk ones).
  Aggregate-first (producer↔commodity counts + a summed multigen-risk weight; no coordinates, no
  person data); a resilience / de-monopolization MAP routed to diversification + recovery, NEVER a
  target-list (G2/G5) and never a trade/forecast (G1/G3 — it folds DISCLOSED top-producer facts).
  Takes an `analyze` result; returns [{:producer :commodities-count :risk-weight :commodities} …]
  sorted by (risk-weight desc, count desc, producer)."
  ([analysis] (cross-commodity-chokepoints analysis 10))
  ([analysis limit]
   (let [by-prod (reduce (fn [m row]
                           (if-let [p (get row "top_producer")]
                             (-> m
                                 (update-in [p :commodities] (fnil conj []) (get row "name"))
                                 (update-in [p :risk] (fnil + 0.0) (get row "multigen_risk")))
                             m))
                         {} (get analysis "commodities"))]
     (->> by-prod
          (map (fn [[p {:keys [commodities risk]}]]
                 {:producer p :commodities-count (count commodities)
                  :risk-weight (/ (Math/round (* (double risk) 1000.0)) 1000.0)
                  :commodities (vec (sort-by str commodities))}))
          (sort-by (fn [{:keys [risk-weight commodities-count producer]}]
                     [(- risk-weight) (- commodities-count) (str producer)]))
          (take limit)
          vec))))

;; ── datom emission (append-only EAVT; every derived datom flagged) ───────────

(defn- add [e a v] [":db/add" e a v])

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the derived observations (the persistable
  form; render-datoms stringifies these; autorun/kotoba append these to the ledger).
  Every datom carries :busshi/derived true + the row's :busshi/sourcing (defaulting
  to :representative; :authoritative rows additionally carry :busshi/source, the
  cited primary source folded via an operator-triggered G7 ingest). The DERIVED
  observation is always :derived — provenance describes the INPUT producer shares,
  not the computed score. No :trade / :signal / forecast-point attribute is ever
  emitted (G1/G3)."
  [{:strs [commodities classes]}]
  (let [cdatoms (mapcat
                 (fn [r]
                   (let [e (str "busshi-commodity:" (get r "id"))
                         src (get r "source")]
                     (cond-> [(add e ":busshi.commodity/name" (get r "name"))
                              (add e ":busshi.commodity/class" (str (get r "class")))
                              (add e ":busshi.obs/top-producer" (str (get r "top_producer")))
                              (add e ":busshi.obs/top-producer-share" (get r "top_producer_share"))
                              (add e ":busshi.obs/named-hhi" (round3 (get r "named_hhi")))
                              (add e ":busshi.obs/chokepoint-risk" (str (get r "chokepoint_risk")))
                              (add e ":busshi.obs/multigen-risk" (round3 (get r "multigen_risk")))
                              (add e ":busshi.obs/route" (str (get r "route")))
                              (add e ":busshi/sourcing" (str (get r "sourcing")))
                              (add e ":busshi/derived" true)]
                       src (conj (add e ":busshi/source" src)))))
                 commodities)
        kdatoms (mapcat
                 (fn [k]
                   (let [e (str "busshi-class:" (name (get k "class")))]
                     [(add e ":busshi.class/commodity-count" (get k "count"))
                      (add e ":busshi.class/mean-multigen-risk" (round3 (get k "mean_multigen_risk")))
                      (add e ":busshi.class/critical-chokepoints" (get k "critical_chokepoints"))
                      (add e ":busshi/derived" true)]))
                 classes)
        all (concat cdatoms kdatoms)]
    (vec all)))

(defn render-datoms
  "EDN string of the derived-observation datoms (see `datoms`)."
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── coverage (gap worklist) ──────────────────────────────────────────────────

(def ^:private universe
  "Representative count of the well-known commodities per class (a coverage
  yardstick, not exhaustive). Drives the gap worklist each run."
  {:precious-metal 6 :base-metal 8 :rare-metal 12 :energy 6 :ag-soft 10})

(defn coverage
  "Coverage of the commodity universe by class + a prioritized gap count."
  [commodities]
  (let [by-class (group-by :class commodities)
        rows (for [[cls target] (sort-by (comp name key) universe)
                   :let [have (count (get by-class cls []))]]
               {"class" cls "have" have "target" target
                "gap" (max 0 (- target have))})]
    {"by_class" (vec rows)
     "total_have" (count commodities)
     "total_target" (reduce + (vals universe))
     "total_gap" (reduce + (map #(get % "gap") rows))}))

;; ── markdown resilience map (NOT a target-list) ──────────────────────────────

(defn render-report
  [analysis coverage-map]
  (let [rows (->> (get analysis "commodities")
                  (sort-by #(- (get % "multigen_risk"))))
        cov (get coverage-map "by_class")
        auth (count (filter #(= (get % "sourcing") :authoritative) rows))
        total (count rows)]
    (str
     "# busshi 物資 — commodity & raw-materials RESILIENCE MAP\n\n"
     "OBSERVATION ONLY. This is a **resilience map, NEVER a target-list** and "
     "**NEVER a trade/price signal** (§2(l) multi-gen risk axis, ADR-2606161700). "
     "Producer shares + price levels are DISCLOSED facts, not verdicts or forecasts. "
     "Provenance: **" auth "/" total " rows :authoritative** (producer shares from a "
     "cited primary source via operator-triggered G7 ingest); the remainder are "
     ":representative approximations.\n\n"
     "## Multi-generational risk ranking (highest first)\n\n"
     "| commodity | class | top producer | top share% | chokepoint | multigen-risk | route | sourcing |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (name (get r "class"))
                      " | " (str (get r "top_producer"))
                      " | " (get r "top_producer_share")
                      " | " (name (get r "chokepoint_risk"))
                      " | " (round3 (get r "multigen_risk"))
                      " | " (name (get r "route"))
                      " | " (name (get r "sourcing")) " |")))
     "\n\n## Coverage (Wave 1, all-domains-thin)\n\n"
     "| class | have | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "class")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_route → resilience (diversify+buffer) · de-monopolization (route-around via abaki/kabuto/tsumugi) · restoration (circular via kanayama/kamado/inochi)._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/busshi/kotoba/seed.edn")
           cs (be/commodities seed)
           a (analyze cs)
           cov (coverage cs)]
       (println (render-report a cov))
       (println (str "-- " (count cs) " commodities, "
                     (count (get a "classes")) " classes, "
                     (get cov "total_gap") " gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
