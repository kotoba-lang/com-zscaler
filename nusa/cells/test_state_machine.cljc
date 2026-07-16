(ns nusa.cells.test-state-machine
  "State-machine tests for nusa 幣 cells (R0). 1:1 port of cells/test_state_machines.py
  (ADR-2606039800). fiber_provenance (G1/G2) + cultivation_license_plan (G1/G4/G5/G8) +
  observation_bridge (G3 routing); .solve() raises at R0."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [nusa.cells.fiber-provenance.state-machine :as fp]
            [nusa.cells.cultivation-license-plan.state-machine :as lic]
            [nusa.cells.observation-bridge.cell :as ob]))

;; ── fiber_provenance (G1/G2) ──
(defn- provenance
  [& {:keys [thc-class fiber-use cultivar]
      :or {thc-class "low-thc" fiber-use ["textile" "shimenawa"] cultivar "hemp.tochigishiro"}}]
  (-> (fp/transition-to-screened {"cell_state" {} "cultivar" cultivar "thc_class" thc-class "fiber_use" fiber-use})
      fp/transition-to-recorded))

(deftest test-fiber-provenance-low-thc-records
  (let [cs (get (provenance) "cell_state")]
    (is (= fp/phase-recorded (get cs "phase")))
    (is (= "low-thc" (get-in cs ["payload" "thcClass"])))
    (is (= true (get-in cs ["payload" "screened"])))))

(deftest test-fiber-provenance-fiber-class-records
  (is (= "fiber" (get-in (provenance :thc-class ":fiber" :fiber-use ["aratae"]) ["cell_state" "payload" "thcClass"]))))

(deftest test-fiber-provenance-rejects-psychoactive
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation" (provenance :thc-class "psychoactive"))))

(deftest test-fiber-provenance-rejects-high-thc-keyword
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation"
                        (fp/transition-to-screened {"cell_state" {} "cultivar" "x" "thc_class" ":high-thc"}))))

(deftest test-fiber-provenance-rejects-consumption-use
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G2 violation" (provenance :fiber-use ["smoking"]))))

(deftest test-fiber-provenance-record-requires-screen
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"THC-class screen"
                        (fp/transition-to-recorded {"cell_state" {"screened" false}}))))

;; ── cultivation_license_plan (G1/G4/G5/G8) ──
(defn- license
  [& {:keys [thc-class purpose funding-source member-sig server-sig cultivar]
      :or {thc-class "low-thc" purpose "industrial-fiber" funding-source "member-okaimono"
           member-sig "member-ed25519-sig" server-sig "" cultivar "hemp.tochigishiro"}}]
  (-> (lic/transition-to-screened {"cell_state" {} "cultivar" cultivar "thc_class" thc-class "purpose" purpose})
      (merge {"funding_source" funding-source}) lic/transition-to-plan-built
      (merge {"member_sig" member-sig "server_sig" server-sig}) lic/transition-to-authorized))

(deftest test-license-member-principal-serverless-outward-gated
  (let [p (get-in (license) ["cell_state" "payload"])]
    (is (= "member" (get p "licenseePrincipal")))      ; G4
    (is (= "member-okaimono" (get p "fundingSource")))  ; G4
    (is (= false (get p "serverHeldKey")))              ; G5
    (is (= true (get p "outwardGated")))                ; G8
    (is (= "member" (get p "signedBy")))))              ; G5

(deftest test-license-rejects-org-funding
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4 violation" (license :funding-source "org-treasury"))))

(deftest test-license-refuses-server-signature
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G5 violation" (license :server-sig "server-sig"))))

(deftest test-license-requires-member-signature
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"member signature" (license :member-sig ""))))

(deftest test-license-rejects-psychoactive-cultivar
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation" (license :thc-class "psychoactive"))))

(deftest test-license-rejects-unknown-purpose
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"purpose" (license :purpose "recreational"))))

;; ── observation_bridge (G3) ──
(deftest test-observation-bridge-routes-off-actor
  (is (str/includes? (ob/route "legislative-trace") "danjo"))
  (is (str/includes? (ob/route "public-comment") "moushibumi"))
  (is (str/includes? (ob/route "cannabis-derived-medicine") "yakushi")))

(deftest test-observation-bridge-rejects-unknown-concern
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown concern" (ob/route "advocate-legalization"))))

;; ── R0: .solve() raises ──
(deftest test-solve-raises-at-r0
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (fp/solve {})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (lic/solve {})))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"R0 scaffold" (ob/solve {}))))
