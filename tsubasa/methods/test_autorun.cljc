#!/usr/bin/env bb
;; tsubasa 翼 — heartbeat (idempotent-by-content) tests.
;; Run:  bb --classpath 20-actors 20-actors/tsubasa/methods/test_autorun.cljc
(ns tsubasa.methods.test-autorun
  (:require [tsubasa.methods.autorun :as ar]
            [tsubasa.methods.kotoba :as k]
            [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]))

(defn- tmp [] (str (System/getProperty "java.io.tmpdir") "/tsubasa-autorun-test-" (gensym) ".edn"))

(def ^:private rows
  [{:type :airport :airport/iata "JFK" :airport/region :north-america}
   {:type :airport :airport/iata "NRT" :airport/region :east-asia}
   {:type :carrier :carrier/iata "AA"}
   {:type :carrier :carrier/iata "JL"}
   {:type :fare :fare/id "f1" :fare/origin "JFK" :fare/destination "NRT" :fare/carrier "AA"
    :fare/duration-min 825 :fare/fare-minor 68000 :fare/baggage-minor 3500 :fare/co2-kg 1080.0 :fare/sourcing :representative}
   {:type :fare :fare/id "f2" :fare/origin "JFK" :fare/destination "NRT" :fare/carrier "JL"
    :fare/duration-min 815 :fare/fare-minor 72000 :fare/baggage-minor 0 :fare/co2-kg 980.0 :fare/sourcing :representative}])

(deftest first-beat-appends
  (let [p (tmp)]
    (try
      (let [r (ar/beat {:rows rows :tx-id "b1" :as-of "a1" :log-path p})]
        (is (:appended r))
        (is (nil? (:reason r)))
        (is (pos? (:count r)))
        (is (= 1 (:routes r)))
        (is (= 2 (:carriers r)))
        (is (= 1 (count (k/read-log p)))))
      (finally (io/delete-file p true)))))

(deftest second-identical-beat-is-noop   ; idempotent-by-content
  (let [p (tmp)]
    (try
      (ar/beat {:rows rows :tx-id "b1" :as-of "a1" :log-path p})
      (let [r2 (ar/beat {:rows rows :tx-id "b2" :as-of "a2" :log-path p})]
        (is (not (:appended r2)))
        (is (= :no-change (:reason r2)))
        (is (= 1 (count (k/read-log p)))))   ; chain did NOT grow
      (finally (io/delete-file p true)))))

(deftest changed-content-appends-again
  (let [p (tmp)
        rows2 (conj rows
                    {:type :fare :fare/id "f3" :fare/origin "JFK" :fare/destination "NRT" :fare/carrier "NH"
                     :fare/duration-min 820 :fare/fare-minor 70500 :fare/baggage-minor 0 :fare/co2-kg 1010.0 :fare/sourcing :representative})]
    (try
      (ar/beat {:rows rows :tx-id "b1" :as-of "a1" :log-path p})
      (let [r2 (ar/beat {:rows rows2 :tx-id "b2" :as-of "a2" :log-path p})]
        (is (:appended r2))
        (is (= 2 (count (k/read-log p))))
        (is (:ok (k/verify-chain p))))
      (finally (io/delete-file p true)))))

(deftest deterministic-head-given-same-inputs
  (let [p1 (tmp) p2 (tmp)]
    (try
      (let [r1 (ar/beat {:rows rows :tx-id "b1" :as-of "a1" :log-path p1})
            r2 (ar/beat {:rows rows :tx-id "b1" :as-of "a1" :log-path p2})]
        (is (= (:head r1) (:head r2))))   ; no wall-clock, no Math/random
      (finally (io/delete-file p1 true) (io/delete-file p2 true)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsubasa.methods.test-autorun)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
