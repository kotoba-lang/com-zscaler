(ns tsutae.cells.tsutae-packaging.test-state-machine
  "Tests for the tsutae packaging state machine (ADR-2605261300 port). Drives materials_verified →
  manual_included → packed → record_emitted: phase/pct progression, plastic-free recyclable
  materials, the G5 manual guard (bilingual JA+EN + BoM + Charter Rider mandatory), and the
  packagedRecord accept (manualGuard). Top-level manual inputs re-supplied across the threading."
  (:require [clojure.test :refer [deftest is]]
            [tsutae.cells.tsutae-packaging.state-machine :as sm]))

(defn- run-all [over]
  (-> {"packaging_state" {"phase" "init" "deviceId" "dev-1" "completionPct" 0}}
      sm/transition-to-materials-verified
      (merge over)
      sm/transition-to-manual-included
      sm/transition-to-packed
      sm/transition-to-record-emitted))

(deftest test-full-progression
  (let [s0 {"packaging_state" {"phase" "init" "deviceId" "dev-1" "completionPct" 0}}
        s1 (sm/transition-to-materials-verified s0)
        s2 (sm/transition-to-manual-included s1)
        s3 (sm/transition-to-packed s2)
        s4 (sm/transition-to-record-emitted s3)]
    (is (= "materials_verified" (get-in s1 ["packaging_state" "phase"])))
    (is (= [25 55 80 100] (mapv #(get-in % ["packaging_state" "completionPct"]) [s1 s2 s3 s4])))
    (is (= true (get-in s1 ["packaging_state" "materials" "plasticFree"])))
    (is (= 100 (get-in s1 ["packaging_state" "materials" "recyclablePct"])))
    (is (= true (get-in s2 ["packaging_state" "manualGuard" "accept"])))   ; defaults ja+en+bom+rider
    (is (= 9 (get-in s2 ["packaging_state" "manualGuard" "ifixitScore"])))
    (is (= true (get-in s3 ["packaging_state" "pack" "tamperEvident"])))
    (is (= "end" (get s4 "next_node")))))

(deftest test-g5-manual-guard
  ;; missing English → reject
  (is (= false (get-in (run-all {"manualLangs" ["ja"]}) ["packaged_record" "manualGuard" "accept"])))
  ;; missing Japanese → reject
  (is (= false (get-in (run-all {"manualLangs" ["en"]}) ["packaged_record" "manualGuard" "accept"])))
  ;; BoM not disclosed → reject
  (is (= false (get-in (run-all {"bomDisclosed" false}) ["packaged_record" "manualGuard" "accept"])))
  ;; Charter Rider missing → reject
  (is (= false (get-in (run-all {"charterRiderIncluded" false}) ["packaged_record" "manualGuard" "accept"])))
  ;; ja+en+bom+rider → accept
  (let [mg (get-in (run-all {"manualLangs" ["ja" "en" "fr"]}) ["packaged_record" "manualGuard"])]
    (is (= true (get mg "accept")))
    (is (= "bilingual manual + BoM + Rider present" (get mg "reason")))))

(deftest test-record-accept-and-fields
  (let [rec (get (run-all {}) "packaged_record")]
    (is (= "dev-1" (get rec "deviceId")))
    (is (= true (get rec "accept"))))
  (is (= false (get-in (run-all {"manualLangs" []}) ["packaged_record" "accept"]))))
