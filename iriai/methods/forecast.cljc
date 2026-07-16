#!/usr/bin/env bb
;; iriai 入会 — predictive-maintenance FORECAST (twin run-ahead), clj-native.
(ns iriai.methods.forecast
  "iriai 入会 — predictive maintenance (ADR-2606280900, the mitooshi 見通し discipline).

  The twin's `project` runs an asset's degradation AHEAD of reality; this layer turns
  that into a LEAD-TIME forecast: for each deployed asset, how many years until it first
  needs a CONDITION/SAFETY-driven intervention (not a calendar inspect) — i.e. when does
  it cross into :corrective-repair / :renew / :decommission. The result is a prioritized
  predictive-maintenance worklist (soonest-first) so the commons funds + crews upkeep
  BEFORE failure, not after.

  Honesty (mitooshi 見通し): the lead-time is a MODEL projection — deterministic given the
  disclosed degradation model, but the model is a simplification; real condition telemetry
  (kizashi sensing, R1) refines it. Forecasts are HORIZON-BOUNDED (no false precision past
  the horizon → :beyond-horizon), and `:model-based true`. SIMULATION ONLY (G5) — a forecast
  never actuates; it informs the maintenance plan + the funding cadence."
  (:require [clojure.string :as str]
            [iriai.methods.twin :as twin]
            [iriai.methods.maintain :as maintain]))

(def default-horizon-yr 40)
(def ^:private renew-condition 0.25)
(def ^:private renew-horizon-yr 3.0)
(def ^:private repair-condition 0.50)

(defn action-due?
  "A projected twin state needs a CONDITION/SAFETY intervention (beyond calendar inspect):
  unsafe, OR condition below the repair threshold, OR remaining-life within the renew horizon."
  [t]
  (or (= :unsafe (:safety t))
      (< (:condition t) repair-condition)
      (<= (:rul t) renew-horizon-yr)))

(defn lead-time
  "Years until the asset first needs a condition/safety action, scanning 0..horizon.
  0 = needs action now; nil = beyond the horizon (no action projected within it)."
  [asset horizon]
  (loop [y 0]
    (cond
      (> y horizon) nil
      (action-due? (twin/project asset y)) y
      :else (recur (inc y)))))

(defn forecast
  "Predictive-maintenance forecast for one asset."
  ([asset] (forecast asset default-horizon-yr))
  ([asset horizon]
   (let [lt (lead-time asset horizon)
         at-year (if (nil? lt) horizon lt)
         proj (twin/project asset at-year)
         vd (:verdict (maintain/verdict (update asset :age-years #(+ (double (or % 0)) at-year)) proj))]
     {:asset (:id asset) :name (:name asset) :lifeline (:lifeline asset) :region (:region asset)
      :current-condition (:condition (twin/assess-asset asset))
      :lead-time-years lt
      :beyond-horizon? (nil? lt)
      :predicted-verdict vd
      :horizon horizon
      :model-based true})))

(defn schedule
  "Predictive-maintenance worklist over all assets, soonest-first (beyond-horizon last)."
  ([assets] (schedule assets default-horizon-yr))
  ([assets horizon]
   (let [rows (mapv #(forecast % horizon) assets)
         sorted (sort-by (fn [r] [(if (:beyond-horizon? r) 1 0)
                                  (or (:lead-time-years r) (inc horizon))])
                         rows)]
     {"forecasts" (vec sorted)
      "count" (count rows)
      "due-now" (count (filter #(= 0 (:lead-time-years %)) rows))
      "within-5yr" (count (filter #(and (:lead-time-years %) (<= (:lead-time-years %) 5)) rows))
      "beyond-horizon" (count (filter :beyond-horizon? rows))
      "horizon" horizon})))

;; ── datom emission (append-only EAVT; flagged; SIMULATION ONLY) ────────────────
(defn- add [e a v] [":db/add" e a v])

(defn datoms
  "Append-only EAVT datoms for the predictive forecast. SIMULATION ONLY — no actuation
  attribute (G5). lead-time is a MODEL projection (:iriai.forecast/model-based true)."
  [{:strs [forecasts]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "iriai-forecast:" (:asset r))]
        [(add e ":iriai.forecast/lifeline" (str (:lifeline r)))
         (add e ":iriai.forecast/lead-time-years"
              (if (:beyond-horizon? r) -1 (long (:lead-time-years r))))
         (add e ":iriai.forecast/predicted-verdict" (str (:predicted-verdict r)))
         (add e ":iriai.forecast/model-based" true)
         (add e ":iriai/sourcing" ":synthetic")
         (add e ":iriai/derived" true)]))
    forecasts)))

(defn render-report [sch]
  (let [rows (get sch "forecasts")]
    (str
     "# iriai 入会 — predictive-maintenance FORECAST (見通し)\n\n"
     "The twin's `project` run-ahead → per-asset LEAD-TIME until the next condition/safety "
     "intervention, soonest-first. The commons funds + crews upkeep BEFORE failure. Lead-time "
     "is a MODEL projection (deterministic given the degradation model; real telemetry refines "
     "it, R1) and HORIZON-bounded (" (get sch "horizon") " yr). SIMULATION ONLY (G5).\n\n"
     "**" (get sch "due-now") "** due now · **" (get sch "within-5yr") "** within 5 yr · **"
     (get sch "beyond-horizon") "** beyond horizon.\n\n"
     "| asset | lifeline | condition | lead-time (yr) | predicted action |\n"
     "|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (:name r) " | " (name (:lifeline r))
                      " | " (:current-condition r)
                      " | " (if (:beyond-horizon? r) (str "> " (get sch "horizon")) (:lead-time-years r))
                      " | " (name (:predicted-verdict r)) " |")))
     "\n\n_Feeds iriai.maintain (the action gate) + iriai.fund (the §1.16 upkeep cadence). "
     "A schedule of FUTURE care, never an actuation._\n")))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           assets (vec (filter #(= (:type %) :asset) (clojure.edn/read-string (slurp seed))))]
       (println (render-report (schedule assets)))
       (println (str "-- " (count assets) " assets forecast --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
