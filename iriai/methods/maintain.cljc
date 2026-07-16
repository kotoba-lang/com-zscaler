#!/usr/bin/env bb
;; iriai 入会 — operations/maintenance LIFECYCLE gate (clj-native, pure stdlib).
(ns iriai.methods.maintain
  "iriai 入会 — the operations / maintenance (運用メンテナンス) LIFECYCLE (ADR-2606280900).

  The upkeep layer the steady-state :maintain verdict was a placeholder for. Reads the
  physical-simulation twin (condition / RUL / safety) for each DEPLOYED asset, folds in
  its maintenance schedule (inspect + service intervals, mid-life refurbish, last-done),
  and routes the asset through the maintenance lifecycle:

    verdict ∈ {:decommission :renew :corrective-repair :refurbish
               :preventive-service :inspect :ok}
  Order — SAFETY FLOOR FIRST (an unsafe asset is never deferred for cost):
    1. safety :unsafe AND RUL ≤ 0        → :decommission      (end-of-life + unsafe)
    2. safety :unsafe                     → :corrective-repair (immediate, cost-independent)
    3. RUL ≤ 0                            → :decommission
    4. condition < 0.25 OR RUL < 3 yr     → :renew             (replace; old → recycle)
    5. condition < 0.50                   → :corrective-repair
    6. mid-life reached, not refurbished  → :refurbish
    7. service interval due               → :preventive-service
    8. inspect interval due               → :inspect
    9. else                               → :ok

  The verdict routes to an EXECUTOR (kuni-umi build/commission fleet · tazuna teleop
  inspection · giemon repair arm · noroshi fibre splice · hodoki disassembly + kanayama
  material recovery) and imputes OpEx onto the §1.16 non-profit rails (cash≡0 to the
  consumer — upkeep is never billed, G2). DESIGN ONLY — iriai plans the maintenance, it
  never dispatches a crew or actuates a tool (actuation-class :intent; live work is the
  executor actor's cell under Council Lv7+ + operator-DID + member-sig, G5).

  The SAFETY-FLOOR is structural + test-enforced: NO :unsafe asset can return :ok /
  :inspect / :preventive-service / :refurbish (mirrors kafun's refuse-precedes-routing,
  mizuho's chlorination clamp, kamado's purge-to-entry gate)."
  (:require [clojure.string :as str]
            [iriai.methods.twin :as twin]))

(def ^:private renew-condition 0.25)
(def ^:private renew-horizon-yr 3.0)
(def ^:private repair-condition 0.50)

(def opex-cost-multiplier
  "Disclosed OpEx multiplier per maintenance action (× the asset's annual baseline)."
  {:inspect 0.05 :preventive-service 0.2 :corrective-repair 0.6
   :refurbish 1.0 :renew 2.5 :decommission 0.8 :ok 0.0})

(def executor
  "Which actor carries each action (iriai plans; the executor acts under Council)."
  {:inspect "tazuna(teleop) + kizashi(sensing)"
   :preventive-service "kuni-umi(fleet)"
   :corrective-repair "kuni-umi + giemon(repair-arm)"
   :refurbish "kuni-umi(fleet)"
   :renew "kuni-umi(build) + hodoki/kanayama(recycle-old)"
   :decommission "hodoki(disassembly) + kanayama(material-recovery)"
   :ok "—"})

(defn- due? [age last-done interval]
  (>= (- (double age) (double (or last-done 0))) (double (or interval 1e9))))

(defn verdict
  "The maintenance verdict for one asset, given its twin state. SAFETY FLOOR FIRST."
  [asset t]
  (let [{:keys [condition rul safety]} t
        age (double (or (:age-years asset) 0))
        dl (double (or (:design-life asset) 30))
        refurbished? (boolean (:refurbished? asset))
        mid-life? (>= age (* dl 0.5))]
    (cond
      (and (= safety :unsafe) (<= rul 0)) {:verdict :decommission :reason :unsafe-eol}
      (= safety :unsafe)                  {:verdict :corrective-repair :reason :safety-floor}
      (<= rul 0)                          {:verdict :decommission :reason :end-of-life}
      (or (< condition renew-condition)
          (< rul renew-horizon-yr))       {:verdict :renew}
      (< condition repair-condition)      {:verdict :corrective-repair}
      (and mid-life? (not refurbished?))  {:verdict :refurbish}
      (due? age (:last-service-age asset) (:service-interval asset)) {:verdict :preventive-service}
      (due? age (:last-inspect-age asset) (:inspect-interval asset)) {:verdict :inspect}
      :else                               {:verdict :ok})))

(defn- round2 [x] (/ (Math/round (* (double x) 100.0)) 100.0))

(defn plan-asset [asset]
  (let [t (twin/assess-asset asset)
        vd (verdict asset t)
        v (:verdict vd)
        opex (* (double (or (:opex-base asset) 0)) (double (get opex-cost-multiplier v 0)))]
    (merge {"id" (:id asset) "name" (:name asset) "lifeline" (:lifeline asset)
            "region" (:region asset)
            "condition" (:condition t) "rul" (:rul t) "safety" (:safety t)
            "verdict" v "executor" (get executor v "—")
            "opex_usd" (round2 opex)
            "actuation_class" :intent}
           (when (:reason vd) {"reason" (:reason vd)}))))

(defn plan
  "Maintenance plan over all deployed assets: per-asset verdict + executor + OpEx +
  aggregate annual OpEx (the §1.16 in-kind upkeep cost, cash≡0 to the consumer)."
  [assets]
  (let [rows (mapv plan-asset assets)]
    {"actions" rows
     "count" (count rows)
     "tally" (frequencies (map #(get % "verdict") rows))
     "safety_floor_actions" (count (filter #(= :safety-floor (get % "reason")) rows))
     "opex_annual_usd" (round2 (reduce + 0.0 (map #(get % "opex_usd") rows)))
     "all_intent" (every? #(= :intent (get % "actuation_class")) rows)}))

;; ── datom emission (append-only EAVT; flagged; DESIGN ONLY) ────────────────────
(defn- add [e a v] [":db/add" e a v])

(defn datoms
  "Append-only EAVT datoms for the maintenance plan. actuation-class is the const
  :intent (G5). NO :iriai.maintain/dispatch-crew / :iriai/actuate / :iriai.person/*
  attribute is ever emitted (G3/G5/G1): iriai plans, the executor acts under Council."
  [{:strs [actions]}]
  (vec
   (mapcat
    (fn [r]
      (let [e (str "iriai-maint:" (get r "id"))]
        (concat
         [(add e ":iriai.maint/lifeline" (str (get r "lifeline")))
          (add e ":iriai.maint/condition" (double (get r "condition")))
          (add e ":iriai.maint/rul-years" (double (get r "rul")))
          (add e ":iriai.maint/safety" (str (get r "safety")))
          (add e ":iriai.maint/verdict" (str (get r "verdict")))
          (add e ":iriai.maint/executor" (str (get r "executor")))
          (add e ":iriai.maint/opex-usd" (double (get r "opex_usd")))
          (add e ":iriai.maint/actuation-class" ":intent")]
         (when (get r "reason") [(add e ":iriai.maint/reason" (str (get r "reason")))])
         [(add e ":iriai/sourcing" ":synthetic")
          (add e ":iriai/derived" true)])))
    actions)))

(defn render-datoms [pl]
  (str "[\n " (str/join "\n " (map pr-str (datoms pl))) "\n]\n"))

(defn render-report [pl]
  (let [rows (->> (get pl "actions")
                  (sort-by #(get % "condition")))]
    (str
     "# iriai 入会 — operations/maintenance (運用メンテナンス) plan\n\n"
     "Each DEPLOYED asset routed through the maintenance lifecycle from its physical-simulation "
     "twin (condition/RUL/safety): **decommission → renew → corrective-repair → refurbish → "
     "preventive-service → inspect → ok**, **SAFETY FLOOR FIRST** (an unsafe asset is never "
     "deferred for cost — structural, test-enforced). Each routes to an EXECUTOR (kuni-umi / "
     "tazuna / giemon / noroshi / hodoki+kanayama) and imputes OpEx onto the §1.16 rails "
     "(**cash≡0 to the consumer** — upkeep is never billed, G2). **DESIGN ONLY** — iriai plans, "
     "the executor acts under Council Lv7+ (actuation-class :intent, G5).\n\n"
     "**" (get pl "count") "** assets · " (pr-str (get pl "tally")) " · "
     (get pl "safety_floor_actions") " safety-floor · annual upkeep OpEx **$"
     (get pl "opex_annual_usd") "** (§1.16 in-kind) · all :intent **" (get pl "all_intent") "**.\n\n"
     "| asset | lifeline | condition | RUL(yr) | safety | verdict | executor | OpEx/yr |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name") " | " (name (get r "lifeline"))
                      " | " (get r "condition") " | " (get r "rul")
                      " | " (name (get r "safety")) " | " (name (get r "verdict"))
                      " | " (get r "executor") " | $" (get r "opex_usd") " |")))
     "\n\n_Upkeep is continuous OpEx on the same §1.16 in-kind rails as the build CapEx; "
     "labour freed by OSS robotics funds the Displacement-Dividend cohort that crews it._\n")))

;; ── CLI (bb) ───────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/iriai/kotoba/seed.edn")
           assets (vec (filter #(= (:type %) :asset) (clojure.edn/read-string (slurp seed))))]
       (println (render-report (plan assets)))
       (println (str "-- " (count assets) " assets planned --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
