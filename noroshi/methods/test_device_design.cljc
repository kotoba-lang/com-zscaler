(ns noroshi.methods.test-device-design
  "Tests for noroshi (烽) generative device-design core
  (`methods/device_design.cljc`). Pins: the civilian gate refuses an unknown
  kind or a non-civilian force-class (G1/G3/N1) before any plan is built;
  assembly kinds delegate to pic-layout's transmitter-plan; a single discrete
  component gets a minimal one-op plan; the emitted device-record is
  :representative and civilian-comms; the report carries honest framing."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [noroshi.methods.device-design :as dd]))

;; ── civilian gate (G1/G3/N1) ──────────────────────────────────────────────────
(deftest test-known-kind-and-civilian-force-class-passes
  (is (= {:kind "modulator" :force-class "civilian-comms"}
         (dd/civilian-gate {:kind "modulator" :force-class "civilian-comms"}))))

(deftest test-unknown-kind-refused
  (is (thrown? clojure.lang.ExceptionInfo
               (dd/civilian-gate {:kind "quantum-dazzler" :force-class "civilian-comms"}))))

(deftest test-non-civilian-force-class-refused
  (doseq [fc ["weaponizable" "fire-control" "military-comms"]]
    (testing fc
      (is (thrown? clojure.lang.ExceptionInfo
                   (dd/civilian-gate {:kind "modulator" :force-class fc}))))))

(deftest test-design-plan-refuses-before-building-anything
  (is (thrown? clojure.lang.ExceptionInfo
               (dd/design-plan {:kind "modulator" :force-class "weaponizable"}))))

;; ── EDA plan generation ───────────────────────────────────────────────────────
(deftest test-assembly-kind-delegates-to-transmitter-plan
  (let [plan (dd/design-plan {:kind "cpo-module" :force-class "civilian-comms"
                              :name "test-cpo" :route-um 1200.0})]
    (is (= "test-cpo" (:name plan)))
    (is (> (:total-waveguide-um plan) 0.0))
    (is (>= (count (:components plan)) 3))))                ; laser + mzm + grating coupler

(deftest test-pic-link-kind-also-delegates-to-transmitter-plan
  (let [plan (dd/design-plan {:kind "pic-link" :force-class "civilian-comms"})]
    (is (> (:total-waveguide-um plan) 0.0))))

(deftest test-single-discrete-component-gets-minimal-plan
  (let [plan (dd/design-plan {:kind "modulator" :force-class "civilian-comms" :name "mzm-only"})]
    (is (= ["mzm-only"] (:components plan)))
    (is (= 1 (count (:ops plan))))
    (is (= "place" (:op (first (:ops plan)))))
    (is (zero? (:total-waveguide-um plan)))))                ; a single part has no on-chip route

(deftest test-design-plan-defaults-a-name-when-omitted
  (let [plan (dd/design-plan {:kind "photodetector" :force-class "civilian-comms"})]
    (is (.contains (:name plan) "photodetector"))))

;; ── photonicDevice record shape ──────────────────────────────────────────────
(deftest test-device-record-is-civilian-and-representative
  (let [intent {:kind "grating-coupler" :force-class "civilian-comms"}
        plan (dd/design-plan intent)
        dev (dd/device-record intent plan)]
    (is (= "civilian-comms" (:force-class dev)))
    (is (= "open-pdk" (:process dev)))
    (is (true? (:representative dev)))
    (is (= "silicon-photonics" (:platform dev)))))

(deftest test-device-record-defaults-line-rate-and-eda
  (let [intent {:kind "waveguide" :force-class "civilian-comms"}
        plan (dd/design-plan intent)
        dev (dd/device-record intent plan)]
    (is (= 106.25 (:line-rate-gbps dev)))
    (is (= "gdsfactory" (:eda dev)))))

(deftest test-device-record-honors-overrides
  (let [intent {:kind "laser" :force-class "civilian-comms" :line-rate-gbps 50.0 :eda "klayout"}
        plan (dd/design-plan intent)
        dev (dd/device-record intent plan)]
    (is (= 50.0 (:line-rate-gbps dev)))
    (is (= "klayout" (:eda dev)))))

;; ── honest framing ────────────────────────────────────────────────────────────
(deftest test-report-renders-and-carries-honest-framing
  (let [txt (dd/report)]
    (is (.contains txt "civilian"))
    (is (.contains txt "representative"))
    (is (.contains txt "G8"))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-device-design)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
