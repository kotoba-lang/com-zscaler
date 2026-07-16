#!/usr/bin/env bb
;; junkan 循環 — seed loader + substrate-integrity tests.
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_junkan_edn.cljc
(ns junkan.methods.test-junkan-edn
  (:require [junkan.methods.junkan-edn :as je]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
(def onto-path "20-actors/junkan/kotoba/ontology.junkan-gov.edn")
(defn- is* [] (je/instruments seed-path))
(defn- onto [] (edn/read-string (slurp onto-path)))

(deftest loads-instruments
  (let [insts (is*)]
    (is (>= (count insts) 30) "global seed has a substantial instrument set")
    (is (every? :id insts))
    (is (every? :name insts))
    (is (every? :jurisdiction insts))))

(deftest ids-unique
  (let [ids (map :id (is*))]
    (is (= (count ids) (count (distinct ids))) "instrument ids are unique")))

(deftest every-instrument-has-who-why-who
  ;; 誰が定めたか (:enactor) / 経緯 (:origin) / 関係者 (:stakeholders) on every row
  (doseq [i (is*)]
    (is (and (:enactor i) (not (str/blank? (:enactor i)))) (str (:id i) " has :enactor (誰が)"))
    (is (and (:origin i) (not (str/blank? (:origin i)))) (str (:id i) " has :origin (経緯)"))
    (is (seq (:stakeholders i)) (str (:id i) " has :stakeholders (関係者)"))))

(deftest enums-valid
  (let [{:keys [enums]} (onto)
        insts (is*)]
    (doseq [i insts]
      (is (contains? (:stock enums) (:stock i)) (str (:id i) " stock in ontology enum"))
      (is (contains? (:polarity enums) (:polarity i)) (str (:id i) " polarity in enum"))
      (is (contains? (:kind enums) (:kind i)) (str (:id i) " kind in enum"))
      (is (contains? (:reversibility enums) (:reversibility i)) (str (:id i) " reversibility in enum"))
      (is (contains? (:sourcing enums) (:sourcing i)) (str (:id i) " sourcing in enum")))))

(deftest numeric-ranges
  (doseq [i (is*)]
    (is (<= 0.0 (double (:magnitude i)) 1.0) (str (:id i) " magnitude in 0..1"))
    (is (<= 0.0 (double (:confidence i)) 1.0) (str (:id i) " confidence in 0..1"))
    (is (<= 1 (:meadows i) 12) (str (:id i) " meadows in 1..12"))))

(deftest covers-all-five-stocks
  (let [stocks (set (map :stock (is*)))]
    (is (= 5 (count stocks)) "all five asymmetry stocks have ≥1 instrument")))

(deftest has-both-polarities-per-coverage
  ;; maturity: at least some narrowing (balancing) instruments exist, not only widening
  (let [pol (frequencies (map :polarity (is*)))]
    (is (>= (get pol :widen 0) 1) "has widening instruments")
    (is (>= (get pol :narrow 0) 1) "has narrowing instruments (balancers)")))

(deftest global-jurisdiction-spread
  (let [js (set (map :jurisdiction (is*)))]
    (is (>= (count js) 8) "global: covers ≥8 jurisdictions")))

(deftest g6-no-person-attribute
  ;; G6 aggregate-only: no per-person modeling attribute on any row
  (doseq [i (is*)]
    (is (not (contains? i :person)) (str (:id i) " has no :person attr (G6)"))
    (is (not (contains? i :pii)) (str (:id i) " has no :pii attr (G6)"))))

(deftest g4-g5-g11-unrepresentable-declared
  (let [u (set (:unrepresentable (onto)))]
    (is (contains? u ":junkan/actuate") "G4 :junkan/actuate declared unrepresentable")
    (is (contains? u ":junkan/dispatch") "G4 :junkan/dispatch declared unrepresentable")
    (is (contains? u ":junkan.gov.loop/proven-cause") "G5 proven-cause unrepresentable")
    (is (contains? u ":junkan.gov/prescription") "G11 prescription unrepresentable")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-junkan-edn)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
