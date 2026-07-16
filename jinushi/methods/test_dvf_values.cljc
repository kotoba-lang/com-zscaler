(ns jinushi.methods.test-dvf-values
  "jinushi 地主 — FR DVF property-value source tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [jinushi.methods.dvf-values :as d]
            [jinushi.methods.confidence :as c]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def snap-file (io/file repo-root "80-data" "jinushi-land" "fr-dvf-values.kotoba.edn"))
(defn snap [] (clojure.edn/read-string (slurp snap-file)))

(deftest test-records-have-no-owner-or-address
  (let [recs (:records (snap))]
    (is (pos? (count recs)) "transactions present")
    (is (not-any? #(or (:owner %) (:owner/name %) (:owner/key %) (:address %) (:adresse %)) recs)
        "G1/gate: no owner identity, no street address in DVF records")
    (is (every? #(= :dvf (:source %)) recs) "source :dvf")
    (is (every? :parcel/id recs) "keyed by official id_parcelle")))

(deftest test-value-aggregates
  (let [s (snap)]
    (is (>= (:lines s) 2000) "Paris 5e 2023 sample")
    (is (pos? (:total-value-eur s)) "total transaction value computed")
    (is (contains? (:by-type s) "Appartement") "per-type medians (apartments)")
    (is (pos? (:median-eur-m2 (get (:by-type s) "Appartement"))) "median €/m² for apartments")))

(deftest test-confidence-tier
  (is (= 0.95 (c/trust-score :dvf)) "DVF is authoritative-gov")
  (is (= :authoritative-gov (:tier (c/trust :dvf)))))

(deftest test-analyze-from-raw-multi-commune
  (let [a (d/analyze* (d/load-all (io/file repo-root "80-data" "jinushi-land")))]
    (is (= (:lines a) (:lines (snap))) "re-analysis from all raw CSVs matches committed snapshot")
    (is (< (:mutations a) (:lines a)) "mutations deduped below line count (multi-lot)")
    (is (>= (count (:by-commune a)) 2) "multi-commune (Paris + Saint-Étienne)")
    ;; the value spread is real: Paris 5e ≫ Saint-Étienne
    (is (> (:appt-median-eur-m2 (get (:by-commune a) "75105"))
           (:appt-median-eur-m2 (get (:by-commune a) "42218"))) "Paris €/m² > Saint-Étienne")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-dvf-values)]
    (System/exit (+ (or fail 0) (or error 0)))))
