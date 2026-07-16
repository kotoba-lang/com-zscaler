(ns kuni-umi.robotics.test-robotics
  "kuni-umi 国産み robotics physics layer — cljc conformance.
  The oracle values below are committed fixtures preserved from the port wave."
  (:require [clojure.test :refer [deftest is testing]]
            [kuni-umi.robotics.plant :as plant]
            [kuni-umi.robotics.control :as control]
            [kuni-umi.robotics.safety :as safety]
            [kuni-umi.robotics.commissioning :as comm]))

;; ── plant.cljc ────────────────────────────────────────────────────

(deftest plant-first-order-step
  (let [p (plant/first-order-plant :gain 2.0 :tau 1.0 :disturbance 0.0 :x 0.0)]
    (is (= 0.0 (plant/measure p)))
    ;; dxdt = (-0 + 2*1 + 0)/1 = 2 ; x' = 0 + 2*0.1 = 0.2
    (is (= 0.2 (plant/measure (plant/step p 1.0 0.1))))))

(deftest plant-microgrid-set-load-and-soc
  (let [p (-> (plant/microgrid-plant) (plant/set-load 140.0))]
    (is (= 140.0 (:p-load p)))
    (is (= 50.0 (plant/measure p)))
    ;; soc clamps to [0,1]
    (let [p2 (plant/step (assoc p :soc 0.999) 200.0 3600.0)]
      (is (<= (:soc p2) 1.0)))))

;; ── control.cljc ──────────────────────────────────────────────────

(deftest pid-anti-windup-holds-integral-when-saturated
  ;; kp=1, ki=1, clamp [0,1]; a large positive error saturates → integral held
  (let [p (control/pid :kp 1.0 :ki 1.0 :out-min 0.0 :out-max 1.0)
        {p1 :ctrl o1 :out} (control/controller-step p 100.0 0.1)]
    (is (= 1.0 o1))                       ;; clamped
    (is (true? (:saturated p1)))
    (is (= 0.0 (:integral p1)))))         ;; anti-windup: integral NOT committed

(deftest droop-command-clamps-to-band
  (let [d (control/droop :nominal 50.0 :droop-r 0.04 :p-base 100.0 :p-min 0.0 :p-max 200.0)]
    ;; measured = nominal → p = p_base
    (is (= 100.0 (control/droop-command d 50.0)))
    ;; big droop → clamp to p_max
    (is (= 200.0 (control/droop-command d 40.0)))))

(deftest simulate-first-order-matches-python-oracle
  (let [p (plant/first-order-plant :gain 2.0 :tau 1.5 :disturbance 0.3)
        pid (control/pid :kp 2.0 :ki 1.0 :out-min -10.0 :out-max 10.0)
        r (control/simulate p pid 5.0 500 0.02 :tol 1e-3)]
    ;; oracle captured from robotics/control.py
    (is (= 4.996086 (:final-value r)))
    (is (= 0.003914 (:steady-error r)))
    (is (false? (:converged r)))
    (is (= -1 (:settling-step r)))
    (is (= 5.0 (:max-abs-error r)))
    (is (= 500 (count (:trajectory r))))))

;; ── safety.cljc ───────────────────────────────────────────────────

(deftest safety-civilian-gate
  (is (nil? (safety/assert-civilian "install" ["install" "service"])))
  (is (thrown? clojure.lang.ExceptionInfo (safety/assert-civilian "weapon" ["weapon"])))   ;; forbidden anchor wins
  (is (thrown? clojure.lang.ExceptionInfo (safety/assert-civilian "mine" ["install"]))))   ;; not allowlisted

(deftest safety-no-server-key
  (is (nil? (safety/require-member-signature "member-sig")))
  (is (thrown? clojure.lang.ExceptionInfo (safety/require-member-signature "m" "server-sig")))  ;; G15: platform never signs
  (is (thrown? clojure.lang.ExceptionInfo (safety/require-member-signature ""))))               ;; nobody authorised

(deftest safety-witness-quorum
  (is (false? (:ok (safety/witness-quorum-ok ["a"]))))
  (is (true? (:escalate-council-lv6 (safety/witness-quorum-ok ["a"]))))
  (is (false? (:ok (safety/witness-quorum-ok ["a" "a"]))))   ;; duplicates
  (is (true? (:ok (safety/witness-quorum-ok ["a" "b"])))))

(deftest safety-envelope-trajectory
  (let [env (safety/safety-envelope :max-joint-speed 1.0 :human-proximity-speed 0.25)]
    ;; |Δq|/dt = 0.5/1.0 = 0.5 < 1.0 far from humans → ok
    (is (:ok (safety/check-trajectory env [[0.0] [0.5] [1.0]] 1.0)))
    ;; same trajectory with a human present → 0.5 > 0.25 ceiling → violation
    (is (not (:ok (safety/check-trajectory env [[0.0] [0.5]] 1.0 :human-present true))))))

;; ── commissioning.cljc ────────────────────────────────────────────

(deftest microgrid-acceptance-matches-python-oracle
  ;; oracle captured from robotics/commissioning.py (final=50.0, rocof linear in load step)
  (let [cases {120.0 [true  0.5945 false]
               140.0 [true  1.1890 false]
               160.0 [true  1.7835 false]
               180.0 [false 2.3780 true]}]   ;; 180 trips the ROCOF guard
    (doseq [[ls [passed rocof tripped]] cases]
      (testing (str "load-step " ls)
        (let [r (comm/run-microgrid-acceptance ls)]
          (is (= passed (:passed r)))
          (is (= 50.0 (:final-freq-hz r)))
          (is (= rocof (:rocof r)))
          (is (= tripped (:rocof-tripped r))))))))

(deftest commission-record-gates
  ;; member-sig required first (G15)
  (is (thrown? clojure.lang.ExceptionInfo
               (comm/commission-microgrid-site {:site-did "s" :loop-dids [] :member-sig ""
                                                :witness-sigs ["a" "b"]})))
  ;; passing acceptance + quorum → operational, python-twin tier, server-held-key false
  (let [rec (comm/commission-microgrid-site {:site-did "did:site:1" :loop-dids ["l1" "l2"]
                                             :member-sig "m" :witness-sigs ["a" "b"]})]
    (is (= "operational" (:site-state rec)))
    (is (= "python-twin" (:acceptance-tier rec)))
    (is (true? (:acceptance-passed rec)))
    (is (false? (:server-held-key rec)))
    (is (= 50.0 (:final-freq-hz rec))))
  ;; quorum fails → punch-list + council escalation
  (let [rec (comm/commission-microgrid-site {:site-did "s" :loop-dids [] :member-sig "m"
                                             :witness-sigs ["a"]})]
    (is (= "punch-list" (:site-state rec)))
    (is (true? (:escalate-council-lv6 rec))))
  ;; device-evidence can only TIGHTEN: consistent evidence → device-wasm tier
  (let [rec (comm/commission-microgrid-site
             {:site-did "s" :loop-dids [] :member-sig "m" :witness-sigs ["a" "b"]
              :device-evidence {:freq-restored true :rocof-trip false
                                :twin-verdict-match true :dry-run true :server-held-key false}})]
    (is (= "device-wasm" (:acceptance-tier rec))))
  ;; inconsistent evidence → demote to punch-list (tier stays python-twin)
  (let [rec (comm/commission-microgrid-site
             {:site-did "s" :loop-dids [] :member-sig "m" :witness-sigs ["a" "b"]
              :device-evidence {:freq-restored false :rocof-trip true
                                :twin-verdict-match false :dry-run true :server-held-key false}})]
    (is (= "punch-list" (:site-state rec)))
    (is (= "python-twin" (:acceptance-tier rec)))))

(deftest to-datoms-server-held-key-always-false
  (let [rec (comm/commission-microgrid-site {:site-did "did:site:1" :loop-dids ["l1"]
                                             :member-sig "m" :witness-sigs ["a" "b"]})
        d (comm/to-datoms rec)]
    (is (= false (:commission/server-held-key d)))       ;; G15 structural invariant
    (is (= "did:site:1" (:commission/site d)))
    (is (= ["l1"] (:commission/open-ot-loops d)))))
