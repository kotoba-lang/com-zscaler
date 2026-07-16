(ns tatara.methods.test-robustness
  "tatara 鑪 — robustness / graceful-degradation regression suite (ADR-2606171800).

  Pins that the pipeline degrades gracefully on empty / partial / malformed input — important
  BEFORE the G7 live-ingest leg, where missing fields and junk rows are the norm. Every assertion
  here is a 'must not crash, must return a sensible zero/default' contract."
  (:require [clojure.test :refer [deftest is run-tests]]
            [tatara.methods.analyze :as az]
            [tatara.methods.compose :as compose]
            [tatara.methods.kotoba :as kt]))

(deftest test-empty-seed-yields-zeros-not-a-crash
  (let [g (az/classify [])
        a (az/analyze g)]
    (is (= 0 (:n-plants a)))
    (is (= 0 (:n-hubs a)))
    (is (= 0 (:n-flows a)))
    (is (= 0 (:n-chokepoints a)))
    (is (= 0 (:global-headcount a)))
    (is (empty? (:sector-stats a)))
    (is (empty? (:choke-plants a)))
    (is (string? (az/render-report g a)))            ;; report still renders
    (is (string? (az/render-datoms a)))))

(deftest test-classify-ignores-non-map-and-unknown-rows
  (let [g (az/classify [nil "junk" 42 [] {:unknown/k 1}
                        {:plant/id "p" :plant/sector :steel :plant/country "JP" :plant/operator "o"}])]
    (is (= 1 (count (:plants g))))                   ;; only the real plant survives
    (is (= 0 (count (:hubs g))))
    (is (= 0 (count (:flows g))))))

(deftest test-plant-missing-optional-fields-uses-defaults
  (let [g (az/classify [{:plant/id "x" :plant/sector :steel :plant/country "JP" :plant/operator "o"
                         :plant/capacity-unit :tonnes-yr}])
        a (az/analyze g)]
    ;; no headcount-est / floor / capacity-value → 0 defaults, never a NullPointer
    (is (= 0 (:global-headcount a)))
    (is (= 0.0 (get-in a [:sector-capacity :steel :value])))
    (is (= 1 (get-in a [:sector-stats :steel :plants])))))

(deftest test-compose-handles-missing-legs-and-unknown-chokepoint
  ;; a flow traversing a chokepoint that has NO :chokepoint node + no craft/cable data
  (let [g (az/classify [{:plant/id "p" :plant/sector :steel :plant/country "JP" :plant/operator "o"
                         :plant/capacity-unit :tonnes-yr}
                        {:flow/id "f" :flow/from "p" :flow/to "h" :flow/mode :sea :flow/via [:mystery-strait]}])
        a (az/analyze g)
        comp (compose/choke-composition a {} {})]    ;; empty craft + cable legs
    (is (= 1 (count comp)))
    (is (= {:plants 1 :craft 0 :cable 0} (-> comp first (select-keys [:plants :craft :cable]))))
    (is (= 1 (:exposure (first comp))))
    (is (string? (compose/render-report comp)))
    (is (vector? (compose/composition-eavt comp)))))

(deftest test-kotoba-roundtrips-empty-and-handles-missing-log
  ;; head-cid / verify-chain on a non-existent log must not throw
  (let [missing (clojure.java.io/file "/tmp/tatara-nonexistent-log-xyz.edn")]
    (.delete missing)
    (is (= "" (kt/head-cid missing)))
    (is (:ok (kt/verify-chain missing)))             ;; empty chain is trivially ok
    (is (= 0 (:length (kt/verify-chain missing)))))
  ;; graph-datoms on empty rows → empty datom vec, no crash
  (is (= [] (kt/graph-datoms [])))
  (is (= [] (kt/graph-datoms [nil "junk" 42]))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-robustness)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
