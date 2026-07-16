#!/usr/bin/env bb
;; ugachi 穿ち × busshi 物資 — grounding bridge (clj-native, pure stdlib).
(ns ugachi.methods.bridge
  "ugachi × busshi grounding bridge (ADR-2606161830, Wave 2 of ADR-2606161800).

  The §2(l) gate's monopoly-effect input was a free-standing project field. This
  bridge GROUNDS it in busshi 物資's actual concentration observation
  (ADR-2606161730): map a project's :resource → a busshi commodity, pull its
  observed chokepoint-risk + top-producer-share, and corroborate-or-correct the
  project's declared :monopoly-effect:

    :diversify  + commodity is concentrated (:high/:critical)  → corroborated (keep)
    :diversify  + commodity is NOT concentrated (:low/:mod)    → downgrade :neutral
                                                                  + flag :overclaimed-diversification
    :entrench   + commodity is concentrated                    → corroborated (keep)
    :entrench   + commodity is NOT concentrated                → keep + flag :entrench-on-unconcentrated
    :neutral                                                    → keep (conservative: no auto-upgrade)
    resource not mapped to busshi                              → keep declared, context :unmapped

  Then the grounded projects feed gate/assess unchanged. This composes the
  OBSERVATION layer (busshi) into the EXECUTION layer (ugachi): the gate now
  refuses monopoly entrenchment / corroborates diversification using real observed
  concentration, not a free claim. Conservative by design — grounding never
  fabricates an :entrench (no false refusals); it only corroborates or downgrades."
  (:require [ugachi.methods.gate :as gate]
            [ugachi.methods.ugachi-edn :as ue]
            [busshi.methods.analyze :as ba]))

;; project :resource  →  busshi commodity :id
(def resource->commodity
  {"gold" "au" "silver" "ag" "platinum" "pt" "palladium" "pd"
   "copper" "cu" "aluminium" "al" "zinc" "zn" "nickel" "ni" "lead" "pb" "tin" "sn"
   "lithium" "li" "cobalt" "co" "rare-earth" "ree" "gallium" "ga" "germanium" "ge"
   "tungsten" "w" "antimony" "sb"
   "crude-oil" "crude" "natural-gas" "natgas" "coal" "coal" "uranium" "u3o8"})

(def ^:private concentrated #{:high :critical})

(defn busshi-index
  "Build {commodity-id → busshi analyze row} from busshi's own seed analysis."
  [busshi-commodities]
  (into {} (map (fn [r] [(get r "id") r])
                (get (ba/analyze busshi-commodities) "commodities"))))

(defn ground-monopoly-effect
  "Given a project + the busshi index, return {:effect :context :flags}."
  [project bindex]
  (let [declared (:monopoly-effect project)
        cid (resource->commodity (:resource project))
        row (when cid (get bindex cid))]
    (if (nil? row)
      {:effect declared :context {:busshi :unmapped} :flags []}
      (let [choke (get row "chokepoint_risk")
            conc? (contains? concentrated choke)
            ctx {:busshi cid :chokepoint choke :top-share (get row "top_producer_share")}]
        (cond
          (and (= declared :diversify) (not conc?))
          {:effect :neutral :context ctx :flags [:overclaimed-diversification]}

          (and (= declared :entrench) (not conc?))
          {:effect :entrench :context ctx :flags [:entrench-on-unconcentrated]}

          :else
          {:effect declared :context ctx :flags []})))))

(defn ground-project
  "Return the project with its :monopoly-effect grounded in busshi, plus
  :grounding metadata (context + flags + the original declared effect)."
  [project bindex]
  (let [{:keys [effect context flags]} (ground-monopoly-effect project bindex)]
    (assoc project
           :monopoly-effect effect
           :grounding {:declared (:monopoly-effect project)
                       :grounded effect
                       :context context
                       :flags flags})))

(defn ground-and-assess
  "Ground every project against busshi, then run the §2(l) gate. Returns the
  gate assessment plus a :grounding summary."
  [projects busshi-commodities]
  (let [bindex (busshi-index busshi-commodities)
        grounded (mapv #(ground-project % bindex) projects)
        a (gate/assess grounded)
        adjusted (filter #(seq (get-in % [:grounding :flags])) grounded)]
    (assoc a
           "grounded" true
           "adjustments" (mapv (fn [p] {"id" (:id p)
                                        "resource" (:resource p)
                                        "declared" (name (get-in p [:grounding :declared]))
                                        "grounded" (name (:monopoly-effect p))
                                        "flags" (mapv name (get-in p [:grounding :flags]))
                                        "context" (get-in p [:grounding :context])})
                               adjusted))))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [ug-seed (or (first args) "20-actors/ugachi/kotoba/seed.edn")
           bu-seed (or (second args) "20-actors/busshi/kotoba/seed.edn")
           projects (ue/projects ug-seed)
           commodities (vec (filter #(= (:type %) :commodity)
                                    (ue/normalize-rows (clojure.edn/read-string (slurp bu-seed)))))
           a (ground-and-assess projects commodities)]
       (println "# ugachi × busshi — grounded §2(l) gate\n")
       (println (str "Grounded against busshi concentration. "
                     (count (get a "adjustments")) " monopoly-effect adjustment(s):\n"))
       (doseq [adj (get a "adjustments")]
         (println (str "- " (get adj "id") " (" (get adj "resource") "): declared "
                       (get adj "declared") " → grounded " (get adj "grounded")
                       " " (get adj "flags")
                       " [busshi " (get-in adj ["context" :busshi])
                       " chokepoint=" (name (get-in adj ["context" :chokepoint] :na))
                       " top=" (get-in adj ["context" :top-share]) "%]")))
       (println (str "\nVerdicts: " (get a "permitted_r0") " propose-r0 · "
                     (get a "routed_to_recovery") " route-to-recovery · "
                     (get a "refused") " refused.")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
