(ns junkan.methods.test-waste-sanitation
  (:require [clojure.test :refer [deftest is]]
            [junkan.methods.waste-sanitation :as ws]))

(def seed-path "20-actors/junkan/kotoba/seed.india-waste-sanitation.edn")
(defn sigs [] (ws/signals seed-path))
(defn analysis [] (ws/analyze (sigs)))

(deftest contribution-sign
  (is (pos? (ws/contribution {:polarity :toward-circular :magnitude 0.5 :confidence 1.0})))
  (is (neg? (ws/contribution {:polarity :toward-accumulation :magnitude 0.5 :confidence 1.0})))
  (is (zero? (ws/contribution {:polarity :ambiguous :magnitude 0.5 :confidence 1.0}))))

(deftest analysis-shape-and-invariants
  (let [a (analysis)]
    (is (= true (get a "hypothesis_only")))
    (is (= true (get a "aggregate_only")))
    (is (= false (get a "actuation_taken")))
    (is (map? (get a "stocks")))
    (is (seq (get a "loops")))
    (is (map? (get a "region")))
    (is (map? (get a "language")))
    (is (re-find #"(?i)not a municipality" (str (get a "caveat"))))))

(deftest region-and-language-coverage
  (let [cov (get (analysis) "coverage")]
    (is (empty? (:missing-regions cov)) "north/south/west/east/northeast/central/pan-india covered")
    (is (empty? (:missing-stocks cov)) "all modeled stocks covered")
    (is (>= (:languages cov) 12) "language cross-section is present")
    (doseq [l [:hi :ta :bn :te :mr :gu :kn :ml :as]]
      (is (some #{l} (:language-list cov)) (str "language present: " l)))))

(deftest counterforces-are-explicit
  (let [a (analysis)
        pol (:polarity (get a "coverage"))]
    (is (pos? (get pol :toward-circular 0)))
    (is (pos? (get pol :toward-accumulation 0)))
    (is (seq (get a "strongest_circular_forces")))
    (is (seq (get a "strongest_accumulation_forces")))))

(deftest loops-grounded-in-member-stocks
  (let [a (analysis)
        lp (first (filter #(= "R-segregation-recycler-linkage" (:id %)) (get a "loops")))]
    (is (= [:source-segregation :recycler-market-linkage] (:member-stocks lp)))
    (is (number? (:drive lp)))
    (is (not= 0.0 (:drive lp)) "drive reads member-stock pressure, not an empty lookup")
    (is (= true (:hypothesis? lp)))))

(deftest report-renders-region-language
  (let [r (ws/render-report (analysis))]
    (is (re-find #"## Region" r))
    (is (re-find #"## Language" r))
    (is (re-find #"Sign convention" r))))
