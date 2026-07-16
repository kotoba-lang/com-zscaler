#!/usr/bin/env bb
(ns junkan.methods.waste-sanitation
  "System-dynamics read-off for India's municipal solid-waste collection,
  source-segregation, processing, and recycling-market-linkage cycle.

  This namespace is deliberately separate from governance-asymmetry and
  consumer-culture. It models aggregate region/language/channel/settlement
  signals only. Positive net pressure means the cycle is moving toward reliable
  collection, source segregation, processing capacity, and recycler-market
  linkage (circularity); negative net pressure means it is moving toward
  uncollected, unsegregated, landfill/open-dumping accumulation. Every
  read-off is hypothesis-only.

  ANALYSIS-ONLY (G4 by absence): this namespace has no dispatch/route/payment
  function. On-the-ground collection-vehicle routing, recycler-payout
  execution, or worker-dispatch decisions are out of junkan's scope — see the
  junkan CLAUDE.md 'Waste & sanitation-cycle substrate' section for the
  analysis/intervention boundary."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def stock-order
  [:collection-reliability
   :source-segregation
   :informal-worker-integration
   :municipal-processing-capacity
   :landfill-dependency
   :recycler-market-linkage
   :public-compliance-norm
   :regulatory-enforcement])

(def stock-label
  {:collection-reliability "collection reliability / route coverage"
   :source-segregation "household source segregation (wet/dry/hazardous)"
   :informal-worker-integration "waste-picker / kabadiwala formal integration"
   :municipal-processing-capacity "MRF / composting / WtE processing capacity"
   :landfill-dependency "open-dumping / unscientific landfill dependency"
   :recycler-market-linkage "linkage from collected material to recycler demand"
   :public-compliance-norm "civic bin-use / no-litter / no-burn norm"
   :regulatory-enforcement "SWM Rules / EPR enforcement strength"})

(def region-order [:north :south :west :east :northeast :central :pan-india])
(def settlement-order [:rural :urban :mixed])

(def loops
  [{:id "R-segregation-recycler-linkage"
    :type :reinforcing
    :stocks [:source-segregation :recycler-market-linkage]
    :doc "Clean segregated streams raise recycler demand/price, which raises the payoff to segregating further."}
   {:id "R-collection-compliance-trust"
    :type :reinforcing
    :stocks [:collection-reliability :public-compliance-norm]
    :doc "Reliable collection builds bin-use norms; less street dumping in turn makes collection routes more effective."}
   {:id "R-informal-integration-recycler-linkage"
    :type :reinforcing
    :stocks [:informal-worker-integration :recycler-market-linkage]
    :doc "Integrating waste-pickers into formal aggregation strengthens material recovery/linkage, which raises formal demand for integrated waste-pickers."}
   {:id "B-processing-capacity-landfill"
    :type :balancing
    :stocks [:municipal-processing-capacity :landfill-dependency]
    :doc "MRF/composting/WtE capacity investment is a counterforce to landfill/open-dumping dependency."}
   {:id "B-enforcement-informal-displacement"
    :type :balancing
    :stocks [:regulatory-enforcement :informal-worker-integration]
    :doc "Enforcement-heavy formalization (contractor crackdowns) can displace informal waste-pickers rather than integrate them, a counterforce to informal-worker-integration."}])

(defn signals [path]
  (edn/read-string (slurp path)))

(defn- round3 [x]
  (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn polarity-sign [p]
  (case p
    :toward-circular 1.0
    :toward-accumulation -1.0
    :ambiguous 0.0
    0.0))

(defn contribution [signal]
  (* (polarity-sign (:polarity signal))
     (double (or (:magnitude signal) 0.0))
     (double (or (:confidence signal) 1.0))))

(defn regime-of [net circular accumulation]
  (cond
    (and (>= circular 0.35) (>= accumulation 0.35) (< (Math/abs (double net)) 0.18)) :contested
    (> net 0.12) :circularity-strengthening
    (< net -0.12) :accumulation-worsening
    :else :mixed))

(defn pressure [signals]
  (let [cs (map contribution signals)
        n (count cs)
        net (if (zero? n) 0.0 (/ (reduce + cs) n))
        circular (reduce + (filter pos? cs))
        accumulation (- (reduce + (filter neg? cs)))]
    {:count n
     :net (round3 net)
     :circular-force (round3 circular)
     :accumulation-force (round3 accumulation)
     :regime (regime-of net circular accumulation)
     :hypothesis? true}))

(defn by-stock [signals]
  (into {}
        (for [s stock-order]
          [s (pressure (filter #(= s (:stock %)) signals))])))

(defn- index-pressure [signals ks f]
  (into {}
        (for [k ks
              :let [xs (filter #(= k (f %)) signals)]
              :when (seq xs)]
          [k (pressure xs)])))

(defn by-region [signals]
  (index-pressure signals region-order :region))

(defn by-settlement [signals]
  (index-pressure signals settlement-order :settlement))

(defn by-language [signals]
  (let [expanded (mapcat (fn [s] (map #(assoc s :language %) (:languages s))) signals)
        langs (sort (distinct (map :language expanded)))]
    (into {}
          (for [lang langs]
            [lang (pressure (filter #(= lang (:language %)) expanded))]))))

(defn loop-drive [stocks member-stocks]
  (let [ps (keep #(get stocks %) member-stocks)
        n (count ps)
        net (if (zero? n) 0.0 (/ (reduce + (map :net ps)) n))
        circular (reduce + (map :circular-force ps))
        accumulation (reduce + (map :accumulation-force ps))]
    {:drive (round3 net)
     :circular-force (round3 circular)
     :accumulation-force (round3 accumulation)
     :regime (regime-of net circular accumulation)}))

(defn loop-regimes [stock-pressures]
  (mapv (fn [{member-stocks :stocks :as lp}]
          (merge (dissoc lp :stocks)
                 {:member-stocks member-stocks
                  :hypothesis? true}
                 (loop-drive stock-pressures member-stocks)))
        loops))

(defn strongest [signals n pred sort-fn]
  (->> signals
       (filter pred)
       (map #(assoc % :contribution (round3 (contribution %))))
       (sort-by sort-fn)
       (take n)
       (mapv #(select-keys % [:id :name :region :settlement :languages :stock :polarity :contribution]))))

(defn coverage [signals]
  (let [regions (set (map :region signals))
        langs (set (mapcat :languages signals))
        stocks (set (map :stock signals))
        missing-regions (remove regions region-order)
        missing-stocks (remove stocks stock-order)]
    {:signals (count signals)
     :regions (frequencies (map :region signals))
     :settlements (frequencies (map :settlement signals))
     :languages (count langs)
     :language-list (vec (sort langs))
     :stocks (frequencies (map :stock signals))
     :polarity (frequencies (map :polarity signals))
     :missing-regions (vec missing-regions)
     :missing-stocks (vec missing-stocks)
     :worklist (vec (concat
                     (map #(str "add region signal: " (name %)) missing-regions)
                     (map #(str "add stock signal: " (name %)) missing-stocks)
                     ["replace representative hypotheses with ULB/state-level SWM Annual Report panel data"
                      "split by waste-stream category: wet/dry, e-waste, hazardous, construction-and-demolition"]))}))

(defn analyze [signals]
  (let [stocks (by-stock signals)
        regions (by-region signals)
        langs (by-language signals)
        settlements (by-settlement signals)]
    {"question" "India municipal solid-waste collection/segregation/processing/recycling-linkage cycle"
     "sign_convention" "positive=toward collection/segregation/processing/recycling circularity; negative=toward uncollected/unsegregated/landfill/open-dumping accumulation"
     "stocks" (into {} (map (fn [[k v]] [(name k) v]) stocks))
     "loops" (loop-regimes stocks)
     "region" (into {} (map (fn [[k v]] [(name k) v]) regions))
     "language" (into {} (map (fn [[k v]] [(name k) v]) langs))
     "settlement" (into {} (map (fn [[k v]] [(name k) v]) settlements))
     "strongest_circular_forces" (strongest signals 5 #(pos? (contribution %)) #(- (:contribution %)))
     "strongest_accumulation_forces" (strongest signals 5 #(neg? (contribution %)) :contribution)
     "coverage" (coverage signals)
     "hypothesis_only" true
     "aggregate_only" true
     "actuation_taken" false
     "caveat" "Not a municipality scorecard or ranking. The model reads region/language/channel pressures and includes explicit circularity counter-forces; it is not a claim that any state or city is uniformly worse or better."}))

(defn render-report [analysis]
  (let [cov (get analysis "coverage")]
    (str
     "# India waste & sanitation cycle — system-dynamics read-off\n\n"
     "Sign convention: **positive = collection/segregation/processing/recycling circularity**, "
     "**negative = uncollected/unsegregated/landfill/open-dumping accumulation**. This is aggregate "
     "and hypothesis-only; it is not a municipality ranking.\n\n"
     "_coverage_: " (:signals cov) " signals · " (:languages cov) " languages · polarity "
     (pr-str (:polarity cov)) "\n\n"
     "## Stocks\n\n"
     "| stock | n | net | circular | accumulation | regime |\n"
     "|---|---|---:|---:|---:|---|\n"
     (str/join "\n"
               (for [s stock-order
                     :let [sp (get (get analysis "stocks") (name s))]
                     :when sp]
                 (str "| " (stock-label s) " | " (:count sp) " | " (:net sp)
                      " | " (:circular-force sp) " | " (:accumulation-force sp)
                      " | " (name (:regime sp)) " |")))
     "\n\n## Region\n\n"
     "| region | n | net | regime |\n|---|---:|---:|---|\n"
     (str/join "\n"
               (for [r region-order
                     :let [rp (get (get analysis "region") (name r))]
                     :when rp]
                 (str "| " (name r) " | " (:count rp) " | " (:net rp)
                      " | " (name (:regime rp)) " |")))
     "\n\n## Language\n\n"
     "| language | n | net | regime |\n|---|---:|---:|---|\n"
     (str/join "\n"
               (for [[lang lp] (sort (get analysis "language"))]
                 (str "| " lang " | " (:count lp) " | " (:net lp)
                      " | " (name (:regime lp)) " |")))
     "\n\n## Loops\n\n"
     "| loop | type | member stocks | drive | regime |\n|---|---|---|---:|---|\n"
     (str/join "\n"
               (for [lp (get analysis "loops")]
                 (str "| " (:id lp) " | " (name (:type lp)) " | "
                      (str/join ", " (map name (:member-stocks lp)))
                      " | " (:drive lp) " | " (name (:regime lp)) " |")))
     "\n\n## Caveat\n\n"
     (get analysis "caveat") "\n")))

(defn -main [& [seed]]
  (let [path (or seed "20-actors/junkan/kotoba/seed.india-waste-sanitation.edn")
        analysis (analyze (signals path))]
    (println (render-report analysis))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
