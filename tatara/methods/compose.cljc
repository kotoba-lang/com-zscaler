(ns tatara.methods.compose
  "tatara 鑪 — cross-actor maritime-resilience composition (ADR-2606171800).

  Makes the 'ONE maritime resilience picture' claim a first-class DATA artifact (not just a viz):
  over the SAME chokepoint keyword, fuse
    静  tatara plant export-dependence  (distinct plants whose export flow transits the chokepoint)
    動  watari live craft transit       (distinct craft whose LATEST fix transits the chokepoint's lane)
    静  watatsuna cable-station load     (distinct cable landing stations carrying the chokepoint)
  into a per-chokepoint composition → resilience-composition.md + derived :composition/* datoms.

  This is the canonical SSoT for the composition; build_viz consumes the same fns to render the
  world-supply globe. Aggregate-first, framed toward REDUNDANCY / safer routing / faster repair —
  NEVER interdiction (G2, mirrors watari/watatsuna): only counts-per-chokepoint are representable,
  there is no 'where to cut' attribute. cljc-native; file I/O only behind #?(:clj …)."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            #?(:clj [clojure.edn :as edn])
            [tatara.methods.analyze :as az]))

;; ── readers: the 動 (watari) + 静-infra (watatsuna) legs ──────────────────────────
#?(:clj
   (defn watari-choke-transit
     "watari craft graph → {chokepoint-keyword-name → set of distinct live craft transiting it}."
     [watari-seed]
     (if-not (.exists (io/file watari-seed))
       {}
       (let [rows (edn/read-string (slurp watari-seed))
             lane->cp (into {} (keep (fn [r] (when (and (:lane/id r) (:lane/chokepoint r))
                                               [(:lane/id r) (name (:lane/chokepoint r))]))
                                     rows))
             fixes (filter :craft.fix/id rows)
             latest (reduce (fn [m fx]
                              (let [c (:craft.fix/craft fx) cur (get m c)]
                                (if (or (nil? cur)
                                        (pos? (compare (:craft.fix/observed-at fx "")
                                                       (:craft.fix/observed-at cur ""))))
                                  (assoc m c fx) m)))
                            {} fixes)]
         (reduce (fn [acc [c fx]]
                   (if-let [cp (lane->cp (:craft.fix/lane fx))]
                     (update acc cp (fnil conj #{}) c) acc))
                 {} latest)))))

#?(:clj
   (defn watatsuna-choke-load
     "watatsuna cable graph → {chokepoint-keyword-name → set of distinct cable landing stations}."
     [watatsuna-seed]
     (if-not (.exists (io/file watatsuna-seed))
       {}
       (reduce (fn [acc s]
                 (reduce (fn [acc cp] (update acc (name cp) (fnil conj #{}) (:station/id s)))
                         acc (:station/chokepoint s)))
               {} (filter :station/id (edn/read-string (slurp watatsuna-seed)))))))

;; ── the composition (pure) ───────────────────────────────────────────────────────
(defn choke-composition
  "Fuse the three legs over each chokepoint. Returns a vector (in tatara export-dependence order)
  of {:chokepoint kw :plants n :craft n :cable n :exposure n}. exposure = a coarse sum of the
  three counts (a relative redundancy-priority cue, NEVER a target score)."
  [a craft-transit cable-load]
  (mapv (fn [cp]
          (let [k (name cp)
                plants (count (get-in a [:choke-plants cp]))
                craft (count (get craft-transit k #{}))
                cable (count (get cable-load k #{}))]
            {:chokepoint cp :plants plants :craft craft :cable cable
             :exposure (+ plants craft cable)}))
        (az/chokes-by-load a)))

;; ── EAVT emission (for the autorun commit-DAG; mirrors kotoba's [:db/add e a v]) ──────
(defn composition-eavt
  "Flatten the composition into append-only EAVT assertions, each flagged :composition/derived
  (aggregate resilience cue recomputed on read, never re-ingested as fact, never a target — G2)."
  [comp]
  (vec (mapcat (fn [{:keys [chokepoint plants craft cable exposure]}]
                 (let [e (str "composition-" (name chokepoint))]
                   [[:db/add e :composition/chokepoint chokepoint]
                    [:db/add e :composition/plants plants]
                    [:db/add e :composition/craft craft]
                    [:db/add e :composition/cable cable]
                    [:db/add e :composition/exposure exposure]
                    [:db/add e :composition/derived true]]))
               comp)))

;; ── report ───────────────────────────────────────────────────────────────────────
(defn render-report [comp]
  (let [P (fn [L s] (conj L s))
        L (-> []
              (P "# tatara 鑪 — cross-actor maritime-resilience composition")
              (P "")
              (P (str "> ADR-2606171800 · **aggregate-first** · the ONE maritime resilience picture: "
                      "static manufacturing export-dependence (tatara) + live craft transit (watari) "
                      "+ static cable load (watatsuna), fused over the SAME chokepoint keyword. "
                      "Routed to REDUNDANCY / safer routing / faster repair — NEVER interdiction (G2)."))
              (P "")
              (P "| chokepoint | 静 plants | 動 live craft | 静-infra cable | exposure |")
              (P "|---|---:|---:|---:|---:|"))
        L (reduce (fn [L {:keys [chokepoint plants craft cable exposure]}]
                    (P L (str "| `" (name chokepoint) "` | " plants " | " craft " | " cable
                              " | " exposure " |")))
                  L comp)
        L (-> L
              (P "")
              (P "---")
              (P (str "*Generated by `tatara/methods/compose.cljc` from the tatara + watari + watatsuna "
                      "seeds. `exposure` is a coarse redundancy-PRIORITY cue (where to ADD redundancy), "
                      "never a target score — only per-chokepoint counts are representable (G2).*")))]
    (str (str/join "\n" L) "\n")))

(defn render-datoms [comp]
  (let [P (fn [L s] (conj L s))
        L (-> []
              (P ";; tatara — DERIVED cross-actor composition datoms (ADR-2606171800). :composition/derived — NOT fact.")
              (P "["))
        L (reduce (fn [L {:keys [chokepoint plants craft cable]}]
                    (P L (str " {:composition/chokepoint " chokepoint
                              " :composition/plants " plants
                              " :composition/craft " craft
                              " :composition/cable " cable
                              " :composition/derived true}")))
                  L comp)
        L (P L "]")]
    (str (str/join "\n" L) "\n")))

#?(:clj
   (defn -main [& _]
     (let [cand (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile)   ; tatara/
           here (if (.exists (io/file cand "data" "seed-plant-graph.kotoba.edn"))
                  cand (io/file "20-actors" "tatara"))
           root (.getParentFile (.getAbsoluteFile here))                             ; 20-actors/
           a (az/analyze (az/classify (az/load-edn (io/file here "data" "seed-plant-graph.kotoba.edn"))))
           ct (watari-choke-transit (io/file root "watari" "data" "seed-craft-graph.kotoba.edn"))
           cl (watatsuna-choke-load (io/file root "watatsuna" "data" "cable-graph.merged.kotoba.edn"))
           comp (choke-composition a ct cl)
           outdir (io/file here "out")]
       (.mkdirs outdir)
       (spit (io/file outdir "resilience-composition.md") (render-report comp))
       (spit (io/file outdir "composition-situation.kotoba.edn") (render-datoms comp))
       (println (str "tatara compose: " (count comp) " chokepoints fused (静 plants · 動 craft · 静 cable); "
                     "top " (-> comp first :chokepoint name) " "
                     (->> comp first ((juxt :plants :craft :cable)) (str/join "·"))))
       0)))
