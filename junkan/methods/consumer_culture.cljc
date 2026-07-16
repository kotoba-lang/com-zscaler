#!/usr/bin/env bb
(ns junkan.methods.consumer-culture
  "System-dynamics read-off for Indian packaged-goods vs loose/refill retail culture.

  This namespace is deliberately separate from governance-asymmetry. It models
  aggregate market/culture signals only: region, language, channel, and settlement
  cohorts. Positive net pressure means loose/refill/kirana purchase persists;
  negative net pressure means standardized packaged/modern-retail purchase is
  gaining force. Every read-off is hypothesis-only."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def stock-order
  [:cashflow-unit-size
   :trust-proximity
   :freshness-local-provenance
   :price-transparency-by-weight
   :language-label-fit
   :distribution-friction
   :waste-refill-norm
   :modern-retail-standardization])

(def stock-label
  {:cashflow-unit-size "cash-flow / unit-size constraint"
   :trust-proximity "kirana trust / proximity / credit"
   :freshness-local-provenance "freshness and local provenance"
   :price-transparency-by-weight "price-per-weight transparency"
   :language-label-fit "language and label/media fit"
   :distribution-friction "distribution and shelf-logistics friction"
   :waste-refill-norm "container reuse and refill norm"
   :modern-retail-standardization "modern retail / ecommerce standardization"})

(def region-order [:north :south :west :east :northeast :central :pan-india])
(def settlement-order [:rural :urban :mixed])

(def loops
  [{:id "R-kirana-cashflow-trust"
    :type :reinforcing
    :stocks [:cashflow-unit-size :trust-proximity :price-transparency-by-weight]
    :doc "Small cash basket -> kirana/refill/credit -> more local trust and price-by-weight comparison."}
   {:id "R-language-local-trust"
    :type :reinforcing
    :stocks [:language-label-fit :trust-proximity]
    :doc "Language mismatch on national packages raises value of retailer explanation; language-local packaging can reverse the sign."}
   {:id "R-freshness-refill"
    :type :reinforcing
    :stocks [:freshness-local-provenance :waste-refill-norm]
    :doc "Visible freshness and household container reuse make package material less valuable."}
   {:id "B-sachet-conversion"
    :type :balancing
    :stocks [:cashflow-unit-size :modern-retail-standardization]
    :doc "Small packs/sachets convert low cash-flow from loose buying into packaged adoption."}
   {:id "B-modern-retail-standardization"
    :type :balancing
    :stocks [:modern-retail-standardization :distribution-friction]
    :doc "Modern trade, ecommerce, delivery, and better logistics standardize SKU comparison and storage."}])

(defn signals [path]
  (edn/read-string (slurp path)))

(defn- round3 [x]
  (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn polarity-sign [p]
  (case p
    :toward-loose 1.0
    :toward-packaged -1.0
    :ambiguous 0.0
    0.0))

(defn contribution [signal]
  (* (polarity-sign (:polarity signal))
     (double (or (:magnitude signal) 0.0))
     (double (or (:confidence signal) 1.0))))

(defn regime-of [net loose packaged]
  (cond
    (and (>= loose 0.35) (>= packaged 0.35) (< (Math/abs (double net)) 0.18)) :contested
    (> net 0.12) :loose-refill-persistent
    (< net -0.12) :packaged-gaining
    :else :mixed))

(defn pressure [signals]
  (let [cs (map contribution signals)
        n (count cs)
        net (if (zero? n) 0.0 (/ (reduce + cs) n))
        loose (reduce + (filter pos? cs))
        packaged (- (reduce + (filter neg? cs)))]
    {:count n
     :net (round3 net)
     :loose-force (round3 loose)
     :packaged-force (round3 packaged)
     :regime (regime-of net loose packaged)
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
        loose (reduce + (map :loose-force ps))
        packaged (reduce + (map :packaged-force ps))]
    {:drive (round3 net)
     :loose-force (round3 loose)
     :packaged-force (round3 packaged)
     :regime (regime-of net loose packaged)}))

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
                     ["replace representative hypotheses with state/category panel data"
                      "split by product category: staples, personal care, beverages, fresh food"]))}))

(defn analyze [signals]
  (let [stocks (by-stock signals)
        regions (by-region signals)
        langs (by-language signals)
        settlements (by-settlement signals)]
    {"question" "India packaged goods vs loose/refill/kirana purchase culture"
     "sign_convention" "positive=toward loose/refill/local-small-quantity; negative=toward packaged/modern-retail"
     "stocks" (into {} (map (fn [[k v]] [(name k) v]) stocks))
     "loops" (loop-regimes stocks)
     "region" (into {} (map (fn [[k v]] [(name k) v]) regions))
     "language" (into {} (map (fn [[k v]] [(name k) v]) langs))
     "settlement" (into {} (map (fn [[k v]] [(name k) v]) settlements))
     "strongest_loose_forces" (strongest signals 5 #(pos? (contribution %)) #(- (:contribution %)))
     "strongest_packaged_forces" (strongest signals 5 #(neg? (contribution %)) :contribution)
     "coverage" (coverage signals)
     "hypothesis_only" true
     "aggregate_only" true
     "actuation_taken" false
     "caveat" "Not an ethnic claim. The model reads region/language/channel pressures and includes packaged-goods counter-forces."}))

(defn render-report [analysis]
  (let [cov (get analysis "coverage")]
    (str
     "# India packaged-goods culture — system-dynamics read-off\n\n"
     "Sign convention: **positive = loose/refill/kirana persistence**, "
     "**negative = packaged/modern-retail pull**. This is aggregate and hypothesis-only; "
     "it is not a claim about all Indians.\n\n"
     "_coverage_: " (:signals cov) " signals · " (:languages cov) " languages · polarity "
     (pr-str (:polarity cov)) "\n\n"
     "## Stocks\n\n"
     "| stock | n | net | loose | packaged | regime |\n"
     "|---|---|---:|---:|---:|---|\n"
     (str/join "\n"
               (for [s stock-order
                     :let [sp (get (get analysis "stocks") (name s))]
                     :when sp]
                 (str "| " (stock-label s) " | " (:count sp) " | " (:net sp)
                      " | " (:loose-force sp) " | " (:packaged-force sp)
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
  (let [path (or seed "20-actors/junkan/kotoba/seed.india-packaged-goods.edn")
        analysis (analyze (signals path))]
    (println (render-report analysis))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
