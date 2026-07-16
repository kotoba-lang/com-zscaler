#!/usr/bin/env bb
;; junkan 循環 — substrate integrity-checker tests.
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_validate.cljc
(ns junkan.methods.test-validate
  (:require [junkan.methods.junkan-edn :as je]
            [junkan.methods.validate :as v]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
(def onto-path "20-actors/junkan/kotoba/ontology.junkan-gov.edn")
(defn- enums [] (:enums (edn/read-string (slurp onto-path))))
(defn- is* [] (je/instruments seed-path))

(deftest live-substrate-clean
  (let [r (v/check (is*) (enums))]
    (is (empty? (:errors r)) (str "live substrate has zero integrity errors; got " (:errors r)))
    ;; no region-mapping warnings (every jurisdiction resolves to a continent);
    ;; a stock-imbalance warning may legitimately appear (it steers seeding).
    (is (not-any? #(re-find #"unmapped" %) (:warnings r))
        (str "live substrate fully region-mapped; warnings=" (:warnings r)))
    (is (= (get-in r [:stats :instruments]) (get-in r [:stats :unique-ids])) "all ids unique")))

(deftest flags-stock-imbalance
  ;; a wildly imbalanced set (5 widen participation, 1 narrow info) → imbalance warning
  (let [bad (concat (repeat 5 {:type :instrument :id "p" :name "x" :jurisdiction "US"
                               :kind :institution :year 2000 :enactor "a" :origin "b" :stakeholders ["c"]
                               :stock :participation-barrier :polarity :widen :magnitude 0.5
                               :reversibility :statutory :meadows 5 :sourcing :synthetic :confidence 0.5})
                    [{:type :instrument :id "i" :name "y" :jurisdiction "US"
                      :kind :law :year 2000 :enactor "a" :origin "b" :stakeholders ["c"]
                      :stock :information-asymmetry :polarity :narrow :magnitude 0.5
                      :reversibility :statutory :meadows 5 :sourcing :synthetic :confidence 0.5}])
        ;; give each a unique id to avoid the dup-id error
        bad (map-indexed (fn [n m] (assoc m :id (str (:id m) n))) bad)
        r (v/check bad (enums))]
    (is (some #(re-find #"stock imbalance" %) (:warnings r)) "flags an imbalanced stock distribution")))

(deftest catches-missing-who-why
  (let [bad (conj (is*) {:type :instrument :id "broken-x" :name "Broken"
                         :jurisdiction "US" :kind :law :year 2000
                         :stock :information-asymmetry :polarity :widen
                         :magnitude 0.5 :reversibility :statutory :meadows 5
                         :sourcing :synthetic :confidence 0.5})
        r (v/check bad (enums))]
    ;; the broken row has no enactor/origin/stakeholders → 3 errors at least
    (is (some #(re-find #"broken-x.*enactor" %) (:errors r)) "flags missing 誰が")
    (is (some #(re-find #"broken-x.*origin" %) (:errors r)) "flags missing 経緯")
    (is (some #(re-find #"broken-x.*stakeholders" %) (:errors r)) "flags missing 関係者")))

(deftest catches-bad-enum-and-range
  (let [bad [{:type :instrument :id "e1" :name "x" :jurisdiction "US"
              :kind :law :year 2000 :enactor "a" :origin "b" :stakeholders ["c"]
              :stock :not-a-stock :polarity :widen :magnitude 5.0
              :reversibility :statutory :meadows 99 :sourcing :synthetic :confidence 0.5}]
        r (v/check bad (enums))]
    (is (some #(re-find #"invalid :stock" %) (:errors r)))
    (is (some #(re-find #":magnitude out of" %) (:errors r)))
    (is (some #(re-find #":meadows out of" %) (:errors r)))))

(deftest catches-duplicate-id
  (let [dup (concat (is*) [(first (is*))])
        r (v/check dup (enums))]
    (is (some #(re-find #"duplicate :id" %) (:errors r)))))

(deftest catches-unmapped-region
  (let [bad [{:type :instrument :id "z1" :name "x" :jurisdiction "ZZ"
              :kind :law :year 2000 :enactor "a" :origin "b" :stakeholders ["c"]
              :stock :information-asymmetry :polarity :narrow :magnitude 0.5
              :reversibility :statutory :meadows 5 :sourcing :synthetic :confidence 0.5}
             {:type :instrument :id "z2" :name "y" :jurisdiction "US"
              :kind :law :year 2000 :enactor "a" :origin "b" :stakeholders ["c"]
              :stock :information-asymmetry :polarity :widen :magnitude 0.5
              :reversibility :statutory :meadows 5 :sourcing :synthetic :confidence 0.5}]
        r (v/check bad (enums))]
    (is (some #(re-find #"ZZ unmapped" %) (:warnings r)) "unmapped jurisdiction → warning")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-validate)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
