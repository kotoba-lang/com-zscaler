#!/usr/bin/env bb
;; busshi 物資 — loader/classifier tests.
;; Run:  bb --classpath 20-actors 20-actors/busshi/methods/test_busshi_edn.cljc
(ns busshi.methods.test-busshi-edn
  (:require [busshi.methods.busshi-edn :as be]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/busshi/kotoba/seed.edn")

(deftest parse-edn-roundtrips
  (let [rows (be/parse-edn "[{:type :commodity :id \"x\" :class :energy}]")]
    (is (vector? rows))
    (is (= "x" (:id (first rows))))
    (is (= :commodity (:type (first rows))))))

(deftest load-and-classify
  (let [rows (be/load-edn seed-path)
        {:keys [commodities]} (be/classify rows)]
    (is (>= (count commodities) 24) "Wave 1 = all-domains-thin (≥24 commodities)")
    (is (every? #(= :commodity (:type %)) commodities))
    (is (every? :class commodities))
    (is (= (set (map :class commodities))
           #{:precious-metal :base-metal :rare-metal :energy :ag-soft})
        "all five domains present (thin one-pass)")))

(deftest commodities-convenience
  (let [cs (be/commodities seed-path)]
    (is (some #(= "au" (:id %)) cs) "gold present")
    (is (some #(= "crude" (:id %)) cs) "crude oil present")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'busshi.methods.test-busshi-edn)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
