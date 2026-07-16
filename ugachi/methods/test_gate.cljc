#!/usr/bin/env bb
;; ugachi 穿ち — §2(l) gate tests (incl. the constitutional refusal invariants).
;; Run:  bb --classpath 20-actors 20-actors/ugachi/methods/test_gate.cljc
(ns ugachi.methods.test-gate
  (:require [ugachi.methods.ugachi-edn :as ue]
            [ugachi.methods.gate :as g]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/ugachi/kotoba/seed.edn")
(defn- ps [] (ue/projects seed-path))
(defn- by-id [id] (first (filter #(= id (:id %)) (ps))))
(defn- v [id] (:verdict (g/verdict (by-id id))))
(defn- reason [id] (:reason (g/verdict (by-id id))))

;; ── hard refusals (the gate REFUSES — proven, not just documented) ───────────

(deftest refuse-no-consent
  (is (= :refuse (v "agg-noconsent-g")))
  (is (= :no-consent (reason "agg-noconsent-g"))))

(deftest refuse-carbon-positive
  (is (= :refuse (v "coal-new-e")))
  (is (= :carbon-positive (reason "coal-new-e"))))

(deftest refuse-monopoly-entrenchment
  (is (= :refuse (v "ree-entrench-f")))
  (is (= :monopoly-entrenchment (reason "ree-entrench-f"))))

(deftest refuse-irreversible-multigen-harm
  (is (= :refuse (v "deepsea-nodule-d")))
  (is (= :irreversible-multigen-harm (reason "deepsea-nodule-d")))
  (is (= :refuse (v "au-cyanide-h")) "high net-irreversibility refused before recovery routing"))

;; ── permit / route / insufficient ────────────────────────────────────────────

(deftest propose-r0-when-passes
  (is (= :propose-r0 (v "agg-river-a")))
  (is (= :propose-r0 (v "cu-diversify-b")))
  (is (= :propose-r0 (v "w-diversify-c")) "diversifying a chokepoint is favorable, not refused"))

(deftest route-to-recovery-preferred
  (is (= :route-to-recovery (v "au-urban-i")) "viable recovery alt → kanayama, not a new dig")
  (is (= :kanayama (:route (g/verdict (by-id "au-urban-i"))))))

(deftest insufficient-evidence
  (is (= :insufficient-evidence (v "oil-ccs-j")) "borderline irreversibility + weak benefit")
  (is (= :insufficient-evidence (v "u-mine-k")) "passes hard gates but no Transparent-Force commitment"))

;; ── ordering invariant: hard refusal beats recovery routing ──────────────────

(deftest refusal-precedes-recovery
  ;; au-cyanide-h has recovery-alternative :viable BUT high net-irreversibility →
  ;; must REFUSE, never route-to-recovery (a refused project is not "fixed" by routing).
  (is (= :refuse (v "au-cyanide-h"))))

;; ── structural invariants (G1 no actuation / G2 not by-name) ─────────────────

(deftest g1-no-actuation-attribute
  (let [edn (g/render-datoms (g/assess (ps)))]
    (is (not (str/includes? edn ":ugachi/actuate")))
    (is (not (str/includes? edn ":ugachi/extract")))
    (is (str/includes? edn ":ugachi.gate/verdict"))
    (is (str/includes? edn ":ugachi/derived"))))

(deftest g2-report-is-stewardship-not-target-list
  (let [md (g/render-report (g/assess (ps)))]
    (is (str/includes? md "target-list") "must declare it is NOT a target-list")
    (is (str/includes? md "DESIGN-ONLY"))
    (is (str/includes? md "never digs"))))

(deftest no-permit-for-any-failing-project
  ;; META-invariant: NO no-consent / carbon-positive / entrenching / irreversible
  ;; project anywhere in the seed returns a permit (:propose-r0).
  (doseq [p (ps)]
    (let [vd (:verdict (g/verdict p))]
      (when (or (not (:consent p))
                (= (:carbon p) :net-positive)
                (= (:monopoly-effect p) :entrench)
                (>= (g/net-irreversibility p) 0.5))
        (is (= :refuse vd) (str (:id p) " must be refused"))))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-gate)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
