(ns yamabiko.methods.test-agent
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [yamabiko.methods.agent :as agent]))

(deftest test-carbody-fsw-count-ok
  (is (get (agent/validate-carbody-fabrication 120 true) "ok")))

(deftest test-carbody-fsw-count-low
  (is (not (get (agent/validate-carbody-fabrication 50 true) "ok"))))

(deftest test-carbody-structural-fail
  (is (not (get (agent/validate-carbody-fabrication 120 false) "ok"))))

(deftest test-bogie-traction-type-valid
  (is (get (agent/validate-bogie-assembly "bg1" "electric" true) "ok")))

(deftest test-bogie-traction-type-invalid
  (is (not (get (agent/validate-bogie-assembly "bg1" "diesel" true) "ok"))))

(deftest test-bogie-witness-quorum-fail
  (is (not (get (agent/validate-bogie-assembly "bg1" "electric" false) "ok"))))

(deftest test-interior-car-number-valid
  (is (get (agent/validate-interior-hvac 8 80 true) "ok")))

(deftest test-interior-car-number-invalid
  (is (not (get (agent/validate-interior-hvac 17 80 true) "ok"))))

(deftest test-interior-seating-valid
  (is (get (agent/validate-interior-hvac 1 75 true) "ok")))

(deftest test-interior-seating-low
  (is (not (get (agent/validate-interior-hvac 1 50 true) "ok"))))

(deftest test-interior-hvac-fail
  (is (not (get (agent/validate-interior-hvac 1 80 false) "ok"))))

(deftest test-traction-hv-type-valid
  (is (get (agent/validate-traction-electrical
             "25kV AC / 1500V DC hybrid" "abc1234567def89fghijk" true)
           "ok")))

(deftest test-traction-firmware-sha-invalid
  (is (not (get (agent/validate-traction-electrical
                  "25kV AC" "short" true)
                "ok"))))

(deftest test-traction-firmware-license-unverified
  (is (not (get (agent/validate-traction-electrical
                  "25kV AC" "abc1234567def89fghijk" false)
                "ok"))))

(deftest test-dynamic-test-speed-320-ok
  (is (get (agent/validate-dynamic-test 320 850 8500) "ok")))

(deftest test-dynamic-test-speed-over-320
  (is (not (get (agent/validate-dynamic-test 350 850 8500) "ok"))))

(deftest test-dynamic-test-speed-too-low
  (is (not (get (agent/validate-dynamic-test 150 850 8500) "ok"))))

(deftest test-dynamic-test-braking-excessive
  (is (not (get (agent/validate-dynamic-test 320 1200 8500) "ok"))))

(deftest test-dynamic-test-power-draw-low
  (is (not (get (agent/validate-dynamic-test 320 850 4000) "ok"))))

(deftest test-dynamic-test-power-draw-high
  (is (not (get (agent/validate-dynamic-test 320 850 20000) "ok"))))

(deftest test-emissions-iso3095-over-85dba
  (is (not (get (agent/validate-emissions-acoustic 90 true true) "ok"))))

(deftest test-emissions-iec62236-fail
  (is (not (get (agent/validate-emissions-acoustic 75 false true) "ok"))))

(deftest test-emissions-kyoukusei-fail
  (is (not (get (agent/validate-emissions-acoustic 75 true false) "ok"))))

(deftest test-emissions-all-pass
  (is (get (agent/validate-emissions-acoustic 75 true true) "ok")))

(deftest test-homologation-eol-recyclability-low
  (is (not (get (agent/validate-homologation true 85) "ok"))))

(deftest test-homologation-eol-recyclability-ok
  (is (get (agent/validate-homologation true 92) "ok")))

(deftest test-homologation-gates-not-pass
  (is (not (get (agent/validate-homologation false 92) "ok"))))

(deftest test-council-review-sigs-insufficient
  (is (not (get (agent/validate-silen-review 3 true true) "ok"))))

(deftest test-council-review-all-passed
  (is (get (agent/validate-silen-review 5 true true) "ok")))

(deftest test-settlement-tithe-split
  (let [s (agent/build-settlement-intent 5000000000)]
    (is (= 500000000 (get s "titheMinor")))
    (is (= "intent" (get s "state")))
    (is (= "usdc-base-l2" (get s "rail")))))

(deftest test-settlement-executed-with-sig
  (let [s (agent/build-settlement-intent 5000000000 "0xsig")]
    (is (= "executed" (get s "state")))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'yamabiko.methods.test-agent)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
