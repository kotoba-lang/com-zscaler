#!/usr/bin/env bb
;; iriai 入会 — infra (SoS coverage/resilience) gate tests.
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_infra.cljc
(ns iriai.methods.test-infra
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.infra :as infra]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- cells [] (ie/cells seed-path))
(defn- cell [region lifeline]
  (first (filter #(and (= region (:region %)) (= lifeline (:lifeline %))) (cells))))
(defn- v [region lifeline] (:verdict (infra/verdict (cell region lifeline))))

;; ── every verdict reached by the seed ──────────────────────────────────────────
(deftest provision-closes-reach-gap
  (is (= :provision (v "kibou" :electric)) "off-grid rural → close the §1.16 reach gap")
  (is (= :provision (v "kibou" :water)))
  (is (= :provision (v "kibou" :gas)))
  (is (= :provision (v "kibou" :telecom)))
  (is (= :close-reach-gap (:route (infra/verdict (cell "kibou" :electric))))))

(deftest redundancy-removes-spof
  (is (= :redundancy (v "shima" :electric)) "single-source island power = SPOF → redundancy")
  (is (= :redundancy (v "shima" :telecom)) "single-source telecom = SPOF → redundancy")
  (is (= :remove-spof (:route (infra/verdict (cell "shima" :electric))))))

(deftest reinforce-after-disaster
  (is (= :reinforce (v "saigai" :water)) "disaster-degraded → restore to baseline")
  (is (= :reinforce (v "saigai" :electric)))
  (is (= :restore-to-baseline (:route (infra/verdict (cell "saigai" :water))))))

(deftest maintain-when-served-and-resilient
  (is (= :maintain (v "midori" :electric)))
  (is (= :maintain (v "midori" :water)))
  (is (= :maintain (v "shima" :water)) "high coverage + resilient even on a single-source island")
  (is (= :maintain (v "machi" :electric))))

(deftest await-consent-is-land-sovereignty
  (is (= :await-consent (v "yama" :electric)) "high need but no consent → await (land sovereignty, G3)")
  (is (= :await-consent (v "yama" :water)))
  (is (= :await-consent (v "yama" :gas))))

(deftest monitor-when-below-adequate-but-low-burden
  (is (= :monitor (v "machi" :gas)) "coverage below adequate but low commons-gap → observe"))

;; ── consent gates the BUILD, never withholds a lifeline ────────────────────────
(deftest consent-precedes-provision
  ;; yama has the same high gap as kibou; the ONLY difference is consent=false →
  ;; it must await-consent, never silently provision on someone's land.
  (is (= :await-consent (v "yama" :water)))
  (is (= :provision (v "kibou" :water))))

;; ── edge-primary commons-gap is on-read, bounded, essentiality-ordered ─────────
(deftest commons-gap-bounded-and-life-first
  (is (<= 0.0 (infra/commons-gap (cell "kibou" :water)) 1.0))
  ;; same coverage gap, water (ess 1.0) outranks gas (ess 0.6)
  (let [w (assoc (cell "kibou" :water) :lifeline :water :served-pop 500 :total-pop 1000)
        g (assoc (cell "kibou" :gas)   :lifeline :gas   :served-pop 500 :total-pop 1000)]
    (is (> (infra/commons-gap w) (infra/commons-gap g))
        "water is more essential than gas at equal coverage gap")))

;; ── G1 structural: a coverage MAP, never a shut-off list / no person attrs ─────
(deftest g1-no-shutoff-no-person-attribute
  (let [edn (infra/render-datoms (infra/assess (cells)))]
    (is (not (str/includes? edn ":iriai/shutoff")))
    (is (not (str/includes? edn ":iriai/disconnect")))
    (is (not (str/includes? edn ":iriai/actuate")))
    (is (not (str/includes? edn ":iriai.person/")))
    (is (str/includes? edn ":iriai.infra/verdict"))
    (is (str/includes? edn ":iriai/derived"))))

(deftest g1-g5-report-declares-commons-not-shutoff
  (let [md (infra/render-report (infra/assess (cells)))]
    (is (str/includes? md "never a shut-off list"))
    (is (str/includes? md "ASSESSMENT + R0 DESIGN ONLY"))
    (is (str/includes? md "never energizes"))))

;; ── coverage is the served fraction; unserved tallied ──────────────────────────
(deftest coverage-and-unserved
  (is (= 0.2 (infra/coverage (cell "kibou" :electric))))
  (let [a (infra/assess (cells))]
    (is (pos? (get a "unserved_pop")))
    (is (= 27 (apply + (vals (get a "tally")))) "every cell (incl. 3 road) has exactly one verdict")
    (is (= 6 (count (keys (get a "tally")))) "all six verdicts are reached by the seed")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-infra)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
