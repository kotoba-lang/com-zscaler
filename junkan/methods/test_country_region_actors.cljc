(ns junkan.methods.test-country-region-actors
  (:require [clojure.test :refer [deftest is]]
            [junkan.methods.country-region-actors :as cra]))

(def seed-path "20-actors/junkan/kotoba/seed.country-region-loop-actors.edn")
(defn rows [] (cra/registry seed-path))

(deftest registry-validates
  (let [v (cra/validate (rows))]
    (is (empty? (:errors v)) (pr-str (:errors v)))
    (is (= 2 (get-in v [:stats :domains])))
    (is (>= (get-in v [:stats :actors]) 18))
    (is (pos? (get-in v [:stats :by-scope :country] 0)))
    (is (pos? (get-in v [:stats :by-scope :region] 0)))))

(deftest required-gates-on-every-actor
  (doseq [a (cra/actors (rows))]
    (is (every? (set (:gates a)) cra/required-gates)
        (str (:id a) " carries all required gates"))))

(deftest india-fissions-into-regions
  (let [as (cra/actors-by-id (rows))
        in (get as "junkan.loop.packaged-goods-culture.IN")
        children (->> (vals as) (filter #(= (:id in) (:parent %))) (map :region) set)]
    (is (= :country (:scope in)))
    (is (= #{:north :south :west :east :northeast :central} children))))

(deftest domain-inheritance-works
  (let [domain-map (cra/domains (rows))
        actor (get (cra/actors-by-id (rows)) "junkan.loop.packaged-goods-culture.IN-SOUTH")]
    (is (= :inherit-domain (:stocks actor)))
    (is (seq (cra/effective-stocks domain-map actor)))
    (is (seq (cra/effective-loops domain-map actor)))))

(deftest waste-sanitation-cycle-india-fissions-into-regions
  (let [as (cra/actors-by-id (rows))
        in (get as "junkan.loop.waste-sanitation-cycle.IN")
        children (->> (vals as) (filter #(= (:id in) (:parent %))) (map :region) set)]
    (is (= :country (:scope in)))
    (is (= #{:north :south :west :east :northeast :central} children))))

(deftest waste-sanitation-cycle-domain-inheritance-works
  (let [domain-map (cra/domains (rows))
        actor (get (cra/actors-by-id (rows)) "junkan.loop.waste-sanitation-cycle.IN-EAST")]
    (is (= :inherit-domain (:stocks actor)))
    (is (seq (cra/effective-stocks domain-map actor)))
    (is (seq (cra/effective-loops domain-map actor)))))

(deftest design-render-includes-hierarchy
  (let [r (cra/render-design (rows))]
    (is (re-find #"Hierarchy" r))
    (is (re-find #"junkan.loop.packaged-goods-culture.IN-SOUTH" r))
    (is (re-find #"errors: 0" r))))
