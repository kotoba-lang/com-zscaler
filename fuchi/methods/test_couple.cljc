(ns fuchi.methods.test-couple
  "Tests for 扶持 (fuchi) couple.cljc — 1:1 port of methods/test_couple.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [fuchi.methods.couple :as c]))

(defn- event [& {:as over}]
  (c/make-displacement-event
   (merge {:displacing-actor "sanae" :cohort-id "cohort-sanae"
           :displaced-count 12 :surplus-usd-micros-yr 60000000000 :funded true} over)))

;; ── TitheRouter split ───────────────────────────────────────────────────────
(deftest test-tithe-split-is-ten-percent-exact
  (let [e (c/earmark-from-surplus (event :surplus-usd-micros-yr 60000000000))]
    (is (= (:tithe-usd-micros e) 6000000000))
    (is (= (:earmark-usd-micros-yr e) 54000000000))))

(deftest test-gross-equals-tithe-plus-earmark-always
  (doseq [s [1 7 999 10001 60000000000 123456789]]
    (let [e (c/earmark-from-surplus (event :surplus-usd-micros-yr s))]
      (is (= (+ (:tithe-usd-micros e) (:earmark-usd-micros-yr e)) s)))))

(deftest test-tithe-bps-is-1000
  (is (= c/TITHE-BPS 1000)))

(deftest test-earmark-construction-rejects-inexact-split
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"(?i)split"
                        (c/make-cohort-earmark {:cohort-id "c" :displacing-actor "sanae"
                                                :gross-usd-micros-yr 100 :tithe-usd-micros 5
                                                :earmark-usd-micros-yr 90 :funded true}))))

;; ── G2 coupling gate ────────────────────────────────────────────────────────
(deftest test-funded-cohort-within-earmark-is-admissible
  (let [e (event :funded true) em (c/earmark-from-surplus e)
        g (c/coupling-gate e em 20000000000)]
    (is (and (true? (get g "admissible")) (> (get g "headroom") 0)))))

(deftest test-unfunded-cohort-is-refused
  (let [e (event :funded false) em (c/earmark-from-surplus e)
        g (c/coupling-gate e em 1)]
    (is (and (false? (get g "admissible"))
             (clojure.string/includes? (get g "reason") "G2")
             (clojure.string/includes? (get g "reason") "no funded")))))

(deftest test-over-earmark-commitment-is-refused
  (let [e (event :surplus-usd-micros-yr 10000000000 :funded true) em (c/earmark-from-surplus e)
        g (c/coupling-gate e em 20000000000)]
    (is (and (false? (get g "admissible"))
             (clojure.string/includes? (get g "reason") "exceeds funded earmark")))))

(deftest test-committed-exactly-at-earmark-is-admissible
  (let [e (event :surplus-usd-micros-yr 10000000000 :funded true) em (c/earmark-from-surplus e)
        g (c/coupling-gate e em 9000000000)]
    (is (and (true? (get g "admissible")) (= (get g "headroom") 0)))))

(deftest test-negative-surplus-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (c/make-displacement-event {:displacing-actor "sanae" :cohort-id "c"
                                           :displaced-count 1 :surplus-usd-micros-yr -5}))))

;; ── seed parsing ────────────────────────────────────────────────────────────
(deftest test-events-from-seed-reads-funded-flag
  (let [recs [{":event/displacing-actor" "sanae" ":event/cohort-id" "c1"
               ":event/displaced-count" 12 ":event/surplus-usd-micros-yr" 60000000000
               ":event/funded" true}
              {":event/displacing-actor" "hataori" ":event/cohort-id" "c2"
               ":event/displaced-count" 30 ":event/surplus-usd-micros-yr" 0
               ":event/funded" false}]
        evs (c/events-from-seed recs)]
    (is (and (true? (:funded (first evs))) (false? (:funded (second evs)))))))
