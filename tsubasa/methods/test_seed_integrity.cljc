#!/usr/bin/env bb
;; tsubasa 翼 — seed ↔ ontology integrity + data-layer gate invariants.
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_seed_integrity.cljc
(ns tsubasa.methods.test-seed-integrity
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [clojure.test :refer [deftest is run-tests]]))

;; Paths resolved relative to THIS file (cwd-independent) — methods/ → tsubasa/ → 20-actors/ → root.
(def ^:private here (fs/parent (fs/absolutize *file*)))
(def ^:private seed-path (str (fs/file here ".." "data" "seed-fares.kotoba.edn")))
(def ^:private onto-path (str (fs/file here ".." ".." ".." "00-contracts" "schemas" "flight-fare-ontology.kotoba.edn")))

(def ^:private rows (edn/read-string (slurp seed-path)))
(def ^:private onto (edn/read-string (slurp onto-path)))

(def ^:private fares    (filter #(= (:type %) :fare) rows))
(def ^:private airports (filter #(= (:type %) :airport) rows))
(def ^:private carriers (filter #(= (:type %) :carrier) rows))

(def ^:private declared-idents (set (map :db/ident onto)))
(def ^:private allowed-regions
  #{:north-america :south-america :europe :middle-east :east-asia
    :south-asia :southeast-asia :oceania :africa})

(deftest seed-has-substance
  (is (>= (count fares) 20) "a meaningful representative fare set")
  (is (>= (count airports) 10))
  (is (>= (count carriers) 10)))

(deftest fare-ids-unique
  (let [ids (map :fare/id fares)]
    (is (= (count ids) (count (distinct ids))))
    (is (every? some? ids))))

(deftest every-fare-has-required-attrs
  (doseq [f fares]
    (is (:fare/id f))
    (is (:fare/origin f))
    (is (:fare/destination f))
    (is (:fare/carrier f))
    (is (:fare/sourcing f))))

(deftest co2-required-and-positive-on-every-fare   ; G4 — emissions surfaced, never hidden
  (doseq [f fares]
    (is (number? (:fare/co2-kg f)) (str (:fare/id f) " missing :fare/co2-kg (G4)"))
    (is (pos? (:fare/co2-kg f)) (str (:fare/id f) " has non-positive co2"))))

(deftest fares-reference-declared-airports-and-carriers
  (let [iatas (set (map :airport/iata airports))
        codes (set (map :carrier/iata carriers))]
    (doseq [f fares]
      (is (contains? iatas (:fare/origin f)) (str (:fare/id f) " origin not a declared airport"))
      (is (contains? iatas (:fare/destination f)) (str (:fare/id f) " destination not a declared airport"))
      (is (contains? codes (:fare/carrier f)) (str (:fare/id f) " carrier not a declared carrier")))))

(deftest airport-regions-valid
  (doseq [ap airports]
    (is (contains? allowed-regions (:airport/region ap))
        (str (:airport/iata ap) " has unknown region " (:airport/region ap)))))

(deftest sourcing-honest
  (doseq [f fares]
    (is (contains? #{:authoritative :representative :synthesized} (:fare/sourcing f)))))

(deftest every-seed-attribute-is-declared-in-ontology   ; seed ↔ ontology parity
  (let [seed-attrs (->> rows
                        (mapcat keys)
                        (remove #{:type})
                        (filter #(namespace %))
                        set)]
    (doseq [a seed-attrs]
      (is (contains? declared-idents a)
          (str "seed uses :" a " which is NOT declared in the ontology")))))

(deftest g1-g3-g5-no-forbidden-key-in-the-data   ; structural at the data layer
  (let [all-keys (->> rows (mapcat keys) (map (comp str/lower-case name)) set)
        forbidden ["commission" "affiliate" "merchant" "sponsored"
                   "urgency" "scarcity" "seatsleft" "searcher" "person" "profile"]]
    (doseq [bad forbidden]
      (is (not-any? #(str/includes? % bad) all-keys)
          (str "G1/G3/G5 — a seed key contains '" bad "'")))))

(deftest at-least-one-monopoly-and-one-competitive-route   ; the seed exercises both readings
  (let [by-route (group-by (juxt :fare/origin :fare/destination) fares)
        carrier-counts (map (fn [[_ fs]] (count (distinct (map :fare/carrier fs)))) by-route)]
    (is (some #(= 1 %) carrier-counts) "seed has a single-carrier (monopoly) route")
    (is (some #(>= % 3) carrier-counts) "seed has a 3+-carrier (competitive) route")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-seed-integrity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
