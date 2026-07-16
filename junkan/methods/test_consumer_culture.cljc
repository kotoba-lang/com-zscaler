(ns junkan.methods.test-consumer-culture
  (:require [clojure.test :refer [deftest is]]
            [junkan.methods.consumer-culture :as cc]))

(def seed-path "20-actors/junkan/kotoba/seed.india-packaged-goods.edn")
(defn sigs [] (cc/signals seed-path))
(defn analysis [] (cc/analyze (sigs)))

(deftest contribution-sign
  (is (pos? (cc/contribution {:polarity :toward-loose :magnitude 0.5 :confidence 1.0})))
  (is (neg? (cc/contribution {:polarity :toward-packaged :magnitude 0.5 :confidence 1.0})))
  (is (zero? (cc/contribution {:polarity :ambiguous :magnitude 0.5 :confidence 1.0}))))

(deftest analysis-shape-and-invariants
  (let [a (analysis)]
    (is (= true (get a "hypothesis_only")))
    (is (= true (get a "aggregate_only")))
    (is (= false (get a "actuation_taken")))
    (is (map? (get a "stocks")))
    (is (seq (get a "loops")))
    (is (map? (get a "region")))
    (is (map? (get a "language")))
    (is (re-find #"not a claim about all Indians|Not an ethnic claim" (str (get a "caveat"))))))

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
    (is (pos? (get pol :toward-loose 0)))
    (is (pos? (get pol :toward-packaged 0)))
    (is (seq (get a "strongest_loose_forces")))
    (is (seq (get a "strongest_packaged_forces")))))

(deftest loops-grounded-in-member-stocks
  (let [a (analysis)
        lp (first (filter #(= "R-language-local-trust" (:id %)) (get a "loops")))]
    (is (= [:language-label-fit :trust-proximity] (:member-stocks lp)))
    (is (number? (:drive lp)))
    (is (not= 0.0 (:drive lp)) "drive reads member-stock pressure, not an empty lookup")
    (is (= true (:hypothesis? lp)))))

(deftest report-renders-region-language
  (let [r (cc/render-report (analysis))]
    (is (re-find #"## Region" r))
    (is (re-find #"## Language" r))
    (is (re-find #"Sign convention" r))))
