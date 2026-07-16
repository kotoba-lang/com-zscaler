#!/usr/bin/env bb
;; 樋 toi — analyze → datoms → coverage (clj-native, pure stdlib).
(ns toi.methods.analyze
  "樋 toi — the Compute-as-Thermal-Routing core (Energy Order Protocol).

  OBSERVATION ONLY. Deferrable compute is a movable load — an energy-flow valve.
  toi scores each compute SITE and routes each movable JOB to the best one:
    site-score = 0.30·carbon-factor + 0.25·surplus-renewable + 0.20·cooling-factor
               + 0.15·heat-sink-bonus + 0.10·transparency
  carbon-factor rewards a clean grid, cooling-factor a low PUE, heat-sink-bonus a site
  whose waste heat feeds 燠 okibi, and transparency scores Murakumo / donated-mesh above
  commercial GPU (G2, Rider §2(i) / ADR-2606172359 — a net score, not a vendor ban).
  Routing avoids carbon vs a warm grid-average baseline; the placement's waste heat is
  offered to okibi. A routed job's saving becomes a 澪 mio flow-improvement claim (R1).

  Hard invariants (proven by tests):
    G1  a routing MAP, never a forced job-kill / load-shedding weapon — no
        :toi/dispatch or :toi.job/kill-order attribute exists (Murakumo fleet + operator
        actuate). A non-movable job is never coerced; it stays in-place.
    G2  Murakumo is default-preferred — a clean Murakumo site outscores a commercial-GPU
        site, which is only ever a fallback (and unused while clean capacity exists)."
  (:require [clojure.string :as str]
            #?(:clj [toi.methods.toi-edn :as te])))

;; ── route params ─────────────────────────────────────────────────────────────

(def baseline-carbon-intensity 450.0) ;; gCO2/kWh — the warm grid-average counterfactual
(def baseline-cooling 1.5)
(def transparency {:murakumo 1.0 :donated-mesh 0.9 :commercial-gpu 0.3})

;; ── pure scoring ─────────────────────────────────────────────────────────────

(defn carbon-factor [site]
  (max 0.0 (/ (- baseline-carbon-intensity (double (:carbon-intensity site)))
              baseline-carbon-intensity)))

(defn cooling-factor [site]
  ;; reward low PUE; baseline 1.5 → 0, ideal ~1.0 → 1.0
  (max 0.0 (min 1.0 (/ (- baseline-cooling (double (:cooling-efficiency site)))
                       (- baseline-cooling 1.0)))))

(defn site-score [site]
  (+ (* 0.30 (carbon-factor site))
     (* 0.25 (double (or (:surplus-renewable site) 0)))
     (* 0.20 (cooling-factor site))
     (* 0.15 (if (:heat-demand-sink site) 1.0 0.0))
     (* 0.10 (get transparency (:site-class site) 0.3))))

(defn avoided-carbon-kg
  "kgCO2 avoided by running job at site vs the warm-grid baseline."
  [job site]
  (* (double (:kwh job))
     (/ (max 0.0 (- baseline-carbon-intensity (double (:carbon-intensity site)))) 1000.0)))

(defn heat-reuse-kwh
  "Compute energy whose waste heat is reusable (site feeds an okibi heat sink)."
  [job site]
  (if (:heat-demand-sink site) (double (:kwh job)) 0.0))

;; ── routing (greedy whole-job by size, to the best site with capacity) ───────

(defn route-jobs
  "Returns {:routes [{:job :site :avoided-kg :heat-reuse-kwh :score} ...]
            :site-cap {id remaining-kwh} :in-place [job-id ...]}.
  Non-movable jobs stay in-place (never coerced). A movable job is assigned to the
  highest-scoring site with capacity ≥ its kWh and score > 0 (an improvement)."
  [jobs sites]
  (let [ranked-sites (sort-by (juxt #(- (site-score %)) :id) sites)
        movable (->> jobs (filter :movable) (sort-by (juxt #(- (double (:kwh %))) :id)))
        init {:site-cap (into {} (map (fn [s] [(:id s) (double (:capacity-kwh s))]) sites))
              :routes [] :in-place (mapv :id (remove :movable jobs))}]
    (reduce
     (fn [st job]
       (let [pick (some (fn [s]
                          (let [rem (get-in st [:site-cap (:id s)])]
                            (when (and (> (site-score s) 0.0) (>= rem (double (:kwh job)))) s)))
                        ranked-sites)]
         (if pick
           (-> st
               (update-in [:site-cap (:id pick)] - (double (:kwh job)))
               (update :routes conj {:job (:id job) :site (:id pick)
                                     :kwh (double (:kwh job))
                                     :avoided-kg (avoided-carbon-kg job pick)
                                     :heat-reuse-kwh (heat-reuse-kwh job pick)
                                     :score (site-score pick)}))
           (update st :in-place conj (:id job)))))
     init movable)))

;; ── analysis ─────────────────────────────────────────────────────────────────

(defn analyze
  [jobs sites]
  (let [{:keys [routes site-cap in-place]} (route-jobs jobs sites)
        route-by-job (into {} (map (juxt :job identity) routes))
        job-rows (mapv (fn [j]
                         (let [r (route-by-job (:id j))]
                           {"id" (:id j) "name" (:name j) "job_class" (:job-class j)
                            "kwh" (double (:kwh j)) "movable" (boolean (:movable j))
                            "route" (if r :routed :in-place)
                            "site" (when r (:site r))
                            "avoided_carbon_kg" (if r (:avoided-kg r) 0.0)
                            "heat_reuse_kwh" (if r (:heat-reuse-kwh r) 0.0)
                            "sourcing" (or (:sourcing j) :representative) "source" (:source j)}))
                       jobs)
        site-rows (mapv (fn [s]
                          (let [cap (double (:capacity-kwh s))
                                rem (get site-cap (:id s) cap)]
                            {"id" (:id s) "name" (:name s) "site_class" (:site-class s)
                             "score" (site-score s) "carbon_intensity" (:carbon-intensity s)
                             "utilization_kwh" (- cap rem) "remaining_kwh" rem
                             "heat_demand_sink" (boolean (:heat-demand-sink s))
                             "sourcing" (or (:sourcing s) :representative) "source" (:source s)}))
                        sites)]
    {"jobs" job-rows
     "sites" site-rows
     "routes" (mapv (fn [r] {"job" (:job r) "site" (:site r) "kwh" (:kwh r)
                             "avoided_carbon_kg" (:avoided-kg r)
                             "heat_reuse_kwh" (:heat-reuse-kwh r) "score" (:score r)}) routes)
     "totals" {"job_count" (count jobs)
               "routed_count" (count routes)
               "in_place_count" (count in-place)
               "routed_kwh" (reduce + 0.0 (map (fn [r] (->> jobs (filter #(= (:id %) (:job r))) first :kwh double)) routes))
               ;; the org's avoided carbon (kgCO2) — the ORDERED compute flow's benefit
               "avoided_carbon_kg" (reduce + 0.0 (map :avoided-kg routes))
               "heat_reuse_kwh" (reduce + 0.0 (map :heat-reuse-kwh routes))}}))

;; ── datom emission (append-only EAVT; every derived datom flagged) ───────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS. Every datom carries :toi/derived + :toi/sourcing;
  :authoritative rows add the cited :toi/source. No :toi/dispatch / :toi.job/kill-order
  / :trade / :signal attribute is ever emitted (G1)."
  [{:strs [jobs sites routes totals]}]
  (let [jdatoms (mapcat
                 (fn [r]
                   (let [e (str "toi-job:" (get r "id")) src (get r "source")]
                     (cond-> [(add e ":toi.job/name" (get r "name"))
                              (add e ":toi.job/class" (str (get r "job_class")))
                              (add e ":toi.job/kwh" (round3 (get r "kwh")))
                              (add e ":toi.obs/route" (str (get r "route")))
                              (add e ":toi.obs/avoided-carbon-kg" (round3 (get r "avoided_carbon_kg")))
                              (add e ":toi.obs/heat-reuse-kwh" (round3 (get r "heat_reuse_kwh")))
                              (add e ":toi/sourcing" (str (get r "sourcing")))
                              (add e ":toi/derived" true)]
                       (get r "site") (conj (add e ":toi.obs/site" (get r "site")))
                       src (conj (add e ":toi/source" src)))))
                 jobs)
        sdatoms (mapcat
                 (fn [r]
                   (let [e (str "toi-site:" (get r "id")) src (get r "source")]
                     (cond-> [(add e ":toi.site/name" (get r "name"))
                              (add e ":toi.site/class" (str (get r "site_class")))
                              (add e ":toi.site/carbon-intensity" (get r "carbon_intensity"))
                              (add e ":toi.obs/site-score" (round3 (get r "score")))
                              (add e ":toi.obs/utilization-kwh" (round3 (get r "utilization_kwh")))
                              (add e ":toi.obs/remaining-kwh" (round3 (get r "remaining_kwh")))
                              (add e ":toi/sourcing" (str (get r "sourcing")))
                              (add e ":toi/derived" true)]
                       src (conj (add e ":toi/source" src)))))
                 sites)
        rdatoms (mapcat
                 (fn [r]
                   (let [e (str "toi-route:" (get r "job") "@" (get r "site"))]
                     [(add e ":toi.route/job" (get r "job"))
                      (add e ":toi.route/site" (get r "site"))
                      (add e ":toi.route/avoided-carbon-kg" (round3 (get r "avoided_carbon_kg")))
                      (add e ":toi.route/heat-reuse-kwh" (round3 (get r "heat_reuse_kwh")))
                      (add e ":toi/derived" true)]))
                 routes)
        e "toi-ledger:routing"
        ldatoms [(add e ":toi.ledger/routed-count" (get totals "routed_count"))
                 (add e ":toi.ledger/in-place-count" (get totals "in_place_count"))
                 (add e ":toi.ledger/routed-kwh" (round3 (get totals "routed_kwh")))
                 (add e ":toi.ledger/avoided-carbon-kg" (round3 (get totals "avoided_carbon_kg")))
                 (add e ":toi.ledger/heat-reuse-kwh" (round3 (get totals "heat_reuse_kwh")))
                 (add e ":toi/derived" true)]]
    (vec (concat jdatoms sdatoms rdatoms ldatoms))))

(defn render-datoms
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── coverage (site classes mapped) ───────────────────────────────────────────

(def ^:private universe
  {:murakumo 3 :donated-mesh 2 :commercial-gpu 1})

(defn coverage
  [sites]
  (let [by-class (group-by :site-class sites)
        rows (for [[cls target] (sort-by (comp name key) universe)
                   :let [have (count (get by-class cls []))]]
               {"site_class" cls "have" have "target" target
                "gap" (max 0 (- target have))})]
    {"by_class" (vec rows)
     "total_have" (count sites)
     "total_target" (reduce + (vals universe))
     "total_gap" (reduce + (map #(get % "gap") rows))}))

;; ── markdown routing map (NOT a forced dispatch) ─────────────────────────────

(defn render-report
  [analysis coverage-map]
  (let [routes (->> (get analysis "routes") (sort-by #(- (get % "avoided_carbon_kg"))))
        totals (get analysis "totals")
        sites (->> (get analysis "sites") (sort-by #(- (get % "score"))))
        cov (get coverage-map "by_class")]
    (str
     "# 樋 toi — COMPUTE ROUTING MAP (Compute as Thermal Routing)\n\n"
     "OBSERVATION ONLY. This is a **routing map, NEVER a forced job-kill / load-shedding "
     "weapon** (G1) — the Murakumo fleet + operator actuate; a non-movable job is never "
     "coerced. Murakumo is default-preferred (G2): transparency scores opaque commercial "
     "GPU low. Each routed job avoids carbon vs a " (int baseline-carbon-intensity)
     " gCO2/kWh baseline; its waste heat feeds 燠 okibi. A routed saving becomes a 澪 mio "
     "flow-improvement claim (R1).\n\n"
     "## Org routed compute\n\n"
     "- **avoided carbon = " (round3 (get totals "avoided_carbon_kg")) " kgCO2** · routed "
     (round3 (get totals "routed_kwh")) " kWh across " (get totals "routed_count") " jobs\n"
     "- waste heat reusable (→ okibi): " (round3 (get totals "heat_reuse_kwh")) " kWh · "
     "in-place (not routed): " (get totals "in_place_count") "\n\n"
     "## Routings (avoided carbon, highest first)\n\n"
     "| job | → site | avoided kgCO2 | heat-reuse kWh | site-score |\n|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r routes]
                 (str "| " (get r "job") " | " (get r "site")
                      " | " (round3 (get r "avoided_carbon_kg"))
                      " | " (round3 (get r "heat_reuse_kwh"))
                      " | " (round3 (get r "score")) " |")))
     "\n\n## Site ranking (score, highest first)\n\n"
     "| site | class | gCO2/kWh | score | utilization kWh |\n|---|---|---|---|---|\n"
     (str/join "\n"
               (for [s sites]
                 (str "| " (get s "name") " | " (name (get s "site_class"))
                      " | " (get s "carbon_intensity")
                      " | " (round3 (get s "score"))
                      " | " (round3 (get s "utilization_kwh")) " |")))
     "\n\n## Coverage (site classes mapped)\n\n"
     "| site class | have | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "site_class")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_Murakumo default-preferred; commercial GPU is a scored fallback, not chosen "
     "while clean capacity exists. toi maps routing, never forces a job-kill._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/toi/kotoba/seed.edn")
           ;; te/jobs+te/sites tolerate both the legacy bare-map seed.edn
           ;; shape and the datomized tx-data shape (single reconstitution
           ;; point — see toi.methods.toi-edn/classify).
           jobs (te/jobs seed)
           sites (te/sites seed)
           a (analyze jobs sites)
           cov (coverage sites)]
       (println (render-report a cov))
       (println (str "-- " (count jobs) " jobs, " (count sites) " sites, "
                     (get-in a ["totals" "routed_count"]) " routed, avoided "
                     (round3 (get-in a ["totals" "avoided_carbon_kg"])) " kgCO2, "
                     (get cov "total_gap") " site-gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
