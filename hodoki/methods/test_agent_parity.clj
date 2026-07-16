#!/usr/bin/env bb
;; LIVE cross-language py↔clj parity for the hodoki ELV-disassembly agent.
(ns hodoki.methods.test-agent-parity
  "test_agent_parity.clj — hodoki agent py↔clj LIVE parity (ADR-2606261215).

  Runs the ACTUAL agent.py via a python3 subprocess and the clj impl over the SAME inputs, then
  DEEP-COMPARES FULL outputs across the deterministic gates: G5 charter-scan (military/weapon
  REFUSED), G6 F-gas ≥95% capture, G8 ECU-wipe (wiped + witnessed), PGM-yield audit, and the
  G3/G4 10%-tithe settlement. The charter-scan reason embeds the matched-pattern list — the exact
  message-format class that this parity sweep has repeatedly caught (py repr vs clj pr-str).

  Gracefully SKIPS if python3 is unavailable (red only on a genuine py↔clj divergence).

  Run:  bb --classpath 20-actors 20-actors/hodoki/py/test_agent_parity.clj"
  (:require [hodoki.methods.agent :as a]
            [clojure.java.shell :refer [sh]]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private py-dir "20-actors/hodoki/py")

(def ^:private py-src
  (str "import json, agent as a\n"
       "out={'scan':[a.scan_charter_compliance('V1','military armored personnel carrier with weapon mount'),"
       "a.scan_charter_compliance('V2','sedan passenger car')],\n"
       " 'fgas':[a.check_fgas_compliance(96.0),a.check_fgas_compliance(50.0,90.0),a.check_fgas_compliance(-1.0)],\n"
       " 'ecu':[a.ecu_data_wipe_mandatory(True,2),a.ecu_data_wipe_mandatory(False,2),a.ecu_data_wipe_mandatory(True,0)],\n"
       " 'pgm':[a.audit_pgm_yield(85.0),a.audit_pgm_yield(50.0)],\n"
       " 'settle':[a.build_settlement_intent(100000,None),a.build_settlement_intent(100000,'sig:x')]}\n"
       "print(json.dumps(out))\n"))

(defn- py-results []
  (try
    (let [r (sh "python3" "-c" py-src :dir py-dir)]
      (when (and (= 0 (:exit r)) (seq (:out r)))
        (json/parse-string (:out r) false)))
    (catch Exception _ nil)))

(defn- stringify [x]
  (cond
    (map? x) (into {} (map (fn [[k v]] [(if (keyword? k) (name k) k) (stringify v)]) x))
    (sequential? x) (mapv stringify x)
    :else x))

(defn- clj-results []
  {"scan"   [(stringify (a/scan-charter-compliance "V1" "military armored personnel carrier with weapon mount"))
             (stringify (a/scan-charter-compliance "V2" "sedan passenger car"))]
   "fgas"   [(stringify (a/check-fgas-compliance 96.0)) (stringify (a/check-fgas-compliance 50.0 90.0))
             (stringify (a/check-fgas-compliance -1.0))]
   "ecu"    [(stringify (a/ecu-data-wipe-mandatory true 2)) (stringify (a/ecu-data-wipe-mandatory false 2))
             (stringify (a/ecu-data-wipe-mandatory true 0))]
   "pgm"    [(stringify (a/audit-pgm-yield 85.0)) (stringify (a/audit-pgm-yield 50.0))]
   "settle" [(stringify (a/build-settlement-intent 100000 nil))
             (stringify (a/build-settlement-intent 100000 "sig:x"))]})

(deftest clj-gates-fire
  ;; runs regardless of python: G5 charter REFUSE (with py-style list repr), G6/G8 gates, tithe.
  (let [scan (a/scan-charter-compliance "V1" "military weapon armored")]
    (is (false? (get scan "ok")) "military/weapon vehicle refused (G5)")
    (is (re-find #"\['military', 'weapon', 'armored'\]" (get scan "reason"))
        "reason uses py-style single-quote, comma-space list repr"))
  (is (false? (get (a/check-fgas-compliance 50.0 90.0) "ok")) "F-gas target <95% refused (G6)")
  (is (false? (get (a/ecu-data-wipe-mandatory false 2) "ok")) "un-wiped ECU blocked (G8)")
  (is (= 10000 (get (a/build-settlement-intent 100000 nil) "titheMinor")) "10% tithe"))

(deftest agent-full-output-matches-python
  (let [py (py-results)]
    (if-not py
      (is true "python3 unavailable — hodoki agent cross-language parity skipped")
      (let [clj (clj-results)]
        (is (= (get py "scan") (get clj "scan")) "charter-scan full outputs (list-repr reason)")
        (is (= (get py "fgas") (get clj "fgas")) "F-gas compliance full outputs")
        (is (= (get py "ecu") (get clj "ecu")) "ECU-wipe full outputs")
        (is (= (get py "pgm") (get clj "pgm")) "PGM-yield audit full outputs")
        (is (= (get py "settle") (get clj "settle")) "settlement full outputs")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'hodoki.methods.test-agent-parity)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
