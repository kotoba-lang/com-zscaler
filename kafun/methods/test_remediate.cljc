#!/usr/bin/env bb
;; kafun 花粉 — remediation gate tests (incl. the constitutional refusal invariants).
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_remediate.cljc
(ns kafun.methods.test-remediate
  (:require [kafun.methods.kafun-edn :as ke]
            [kafun.methods.remediate :as r]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/kafun/kotoba/seed.edn")
(defn- ss [] (ke/stands seed-path))
(defn- by-id [id] (first (filter #(= id (:id %)) (ss))))
(defn- v [id] (:verdict (r/verdict (by-id id))))
(defn- reason [id] (:reason (r/verdict (by-id id))))

;; ── hard refusals (撲滅 is RESTORATION — the gate REFUSES non-restorative cuts) ─

(deftest refuse-clearcut-without-reforest
  (is (= :refuse (v "sugi-clearcut-f")))
  (is (= :clearcut-without-reforest (reason "sugi-clearcut-f"))
      "主伐 without 再造林 is refused (撲滅 ≠ deforestation), even with consent"))

(deftest refuse-carbon-positive
  (is (= :refuse (v "hinoki-carbon-g")))
  (is (= :carbon-positive (reason "hinoki-carbon-g"))
      "net-positive carbon refused even when replant is included"))

;; ── route / await / monitor ──────────────────────────────────────────────────

(deftest reforest-priority-when-passes
  (is (= :reforest-priority (v "sugi-tama-a")))
  (is (= :reforest-priority (v "hinoki-kii-b")))
  (is (= :reforest-priority (v "hinoki-gifu-k")) "partial 苗木 supply still proceeds")
  (is (= :shubatsu-saizourin (:route (r/verdict (by-id "sugi-tama-a"))))))

(deftest await-sapling-supply-l1
  (is (= :await-sapling-supply (v "sugi-tochigi-c")) "ready but no 無花粉苗木 → routes to L1-1 production")
  (is (= :mubunka-nae (:route (r/verdict (by-id "sugi-tochigi-c"))))))

(deftest await-consent-g3
  (is (= :await-consent (v "sugi-akita-d")) "no landowner/community consent → await (land sovereignty)"))

(deftest protected-selective-never-clearcut
  (is (= :protected-selective (v "sugi-watershed-e")) "watershed → selective/gradual only")
  (is (= :protected-selective (v "sugi-steep-j")) "steep → selective/gradual only"))

(deftest monitor-low-or-nonviable
  (is (= :monitor (v "broadleaf-h")) "broadleaf = negligible pollen")
  (is (= :monitor (v "mixed-low-i")) "sparse population = low burden")
  (is (= :monitor (v "sugi-remote-l")) "high burden but reforest-viability < 0.5 → observe until viable"))

;; ── ordering invariant: hard refusal beats every other route ─────────────────

(deftest refusal-precedes-routing
  ;; sugi-clearcut-f is consented + high-burden + supply-sufficient BUT replant=false →
  ;; must REFUSE, never reforest-priority (a non-restorative cut is not "fixed" by burden).
  (is (= :refuse (v "sugi-clearcut-f"))))

;; ── structural invariants (G5 no actuation / G2 no person health) ────────────

(deftest g5-g2-no-actuation-no-person-attribute
  (let [edn (r/render-datoms (r/assess (ss)))]
    (is (not (str/includes? edn ":kafun/actuate")))
    (is (not (str/includes? edn ":kafun/clearcut")))
    (is (not (str/includes? edn ":kafun.person/health")))
    (is (str/includes? edn ":kafun.rem/verdict"))
    (is (str/includes? edn ":kafun/derived"))))

(deftest g1-report-is-restoration-not-cut-list
  (let [md (r/render-report (r/assess (ss)))]
    (is (str/includes? md "cut-list") "must declare it is NOT a cut-list")
    (is (str/includes? md "DESIGN-ONLY"))
    (is (str/includes? md "never cuts"))))

(deftest no-permit-for-any-non-restorative-stand
  ;; META-invariant: NO clearcut-without-reforest / net-carbon-positive stand
  ;; anywhere in the seed returns a remediation permit (:reforest-priority).
  (doseq [s (ss)]
    (let [vd (:verdict (r/verdict s))]
      (when (or (not (:replant s))
                (= (:carbon s) :net-positive))
        (is (= :refuse vd) (str (:id s) " must be refused"))))))

;; ── edge-primary burden scoring is on-read, bounded, monotone ────────────────

(deftest pollen-burden-bounded-and-ordered
  (is (<= 0.0 (r/pollen-burden (by-id "sugi-tama-a")) 1.0))
  (is (> (r/pollen-burden (by-id "sugi-tama-a"))
         (r/pollen-burden (by-id "broadleaf-h")))
      "old-growth sugi by people > broadleaf in the wild"))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-remediate)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
