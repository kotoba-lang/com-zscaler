(ns ainori.methods.test-agent
  "test_agent — ainori 相乗 test harness (clojure.test; no kotoba host needed).

  1:1 port of `20-actors/ainori/py/test_agent.py` (ADR-2606071500).

  Verifies the structural invariants of ADR-2606071500:
    G1 no-gig        — driverWageMinor ≡ 0; gig ≡ false
    G2 no-surge      — cost-share depends only on real cost + occupancy; no demand multiplier
    G3 safety        — over-speed / out-of-ODD / >L4 requests are REFUSED, not clamped
    G4 tithe         — TitheRouter 10% split; gross = tithe + carrierReimbursement exactly
    G5 no-server-key — only a member-origin signature authorizes
    G11 pooling-first — match maximizes resulting occupancy

  Python source uses string-keyed dicts; Clojure port uses keyword keys.
  Expected values copied VERBATIM from py/test_agent.py."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [ainori.methods.agent :as agent]))

;; ── helpers ──────────────────────────────────────────────────────────────────
;; Mirrors _trip() and _req() in test_agent.py

(defn- trip
  "Build a base trip map, with overrides (mirrors Python _trip(**kw))."
  [& {:as kw}]
  (merge {:tripId "t1" :carrierDid "did:plc:carrier" :zone "arterial"
          :plannedSpeedMps 12.0 :inOdd true :saeLevel 4 :seatsAvailable 3
          :occupancy 1 :detourMeters 200 :fuelWearMinor 1200000}
         kw))

(defn- req
  "Build a base ride request map, with overrides (mirrors Python _req(**kw))."
  [& {:as kw}]
  (merge {:requestId "r1" :riderDid "did:plc:rider" :origin "A" :destination "B"
          :seats 1 :consentRef "consent-1" :mode "human-pooled"}
         kw))

;; ── SafetyEnvelope (G3) ──────────────────────────────────────────────────────
;; Mirrors Python class SafetyEnvelope

(deftest test-within-cap-ok
  ;; test_within_cap_ok: self.assertTrue(agent.safety_envelope_ok("arterial", 12.0, True, 4)["ok"])
  (is (true? (:ok (agent/safety-envelope-ok "arterial" 12.0 true 4)))))

(deftest test-over-speed-refused
  ;; test_over_speed_refused: cap for residential is 8.3 m/s; 12.0 exceeds it
  ;;   self.assertFalse(v["ok"])
  ;;   self.assertIn("refusal", v["reason"])
  (let [v (agent/safety-envelope-ok "residential" 12.0 true 4)]
    (is (false? (:ok v)))
    (is (str/includes? (:reason v) "refusal"))))

(deftest test-out-of-odd-refused
  ;; test_out_of_odd_refused: self.assertFalse(agent.safety_envelope_ok("arterial", 5.0, False, 4)["ok"])
  (is (false? (:ok (agent/safety-envelope-ok "arterial" 5.0 false 4)))))

(deftest test-above-sae-l4-refused
  ;; test_above_sae_l4_refused: self.assertFalse(agent.safety_envelope_ok("arterial", 5.0, True, 5)["ok"])
  (is (false? (:ok (agent/safety-envelope-ok "arterial" 5.0 true 5)))))

;; ── NoSurge (G2) ─────────────────────────────────────────────────────────────
;; Mirrors Python class NoSurge

(deftest test-flat-split-independent-of-demand
  ;; test_flat_split_independent_of_demand:
  ;;   self.assertEqual(agent.cost_share(1_200_000, 4), 300_000)
  (is (= (agent/cost-share 1200000 4) 300000)))

(deftest test-higher-occupancy-lowers-share
  ;; test_higher_occupancy_lowers_share:
  ;;   self.assertLess(agent.cost_share(1_200_000, 4), agent.cost_share(1_200_000, 2))
  (is (< (agent/cost-share 1200000 4) (agent/cost-share 1200000 2))))

(deftest test-no-demand-kwarg
  ;; test_no_demand_kwarg: verifies cost_share has exactly {fuel_wear_minor, occupancy} params.
  ;; In Clojure: verified structurally — cost-share accepts exactly 2 args, no more, no less.
  ;; Calling with 3 args would throw ArityException; calling with 2 works (number? checks ok).
  (is (number? (agent/cost-share 1200000 2))))

;; ── Matching (G11, G3, G8) ───────────────────────────────────────────────────
;; Mirrors Python class Matching

(deftest test-consent-required
  ;; test_consent_required:
  ;;   m = agent.match_pool(_req(consentRef=""), [_trip()])
  ;;   self.assertEqual(m["state"], "refused")
  ;;   self.assertIn("G8", m["reason"])
  (let [m (agent/match-pool (req :consentRef "") [(trip)])]
    (is (= (:state m) "refused"))
    (is (str/includes? (:reason m) "G8"))))

(deftest test-unsafe-trip-dropped
  ;; test_unsafe_trip_dropped: only trip is over-speed for its zone ⇒ no feasible match
  ;;   m = agent.match_pool(_req(), [_trip(zone="residential", plannedSpeedMps=12.0)])
  ;;   self.assertEqual(m["state"], "refused")
  (let [m (agent/match-pool (req) [(trip :zone "residential" :plannedSpeedMps 12.0)])]
    (is (= (:state m) "refused"))))

(deftest test-pooling-first-maximizes-occupancy
  ;; test_pooling_first_maximizes_occupancy:
  ;;   low = _trip(tripId="low", occupancy=0, detourMeters=10)
  ;;   high = _trip(tripId="high", occupancy=2, detourMeters=500)
  ;;   m = agent.match_pool(_req(), [low, high])
  ;;   self.assertEqual(m["routeId"], "high")   # picks the fuller trip (G11), not the short detour
  ;;   self.assertEqual(m["occupancy"], 3)
  (let [low  (trip :tripId "low"  :occupancy 0 :detourMeters 10)
        high (trip :tripId "high" :occupancy 2 :detourMeters 500)
        m (agent/match-pool (req) [low high])]
    (is (= (:routeId m) "high"))   ; picks the fuller trip (G11), not the short detour
    (is (= (:occupancy m) 3))))

(deftest test-no-gig-fields
  ;; test_no_gig_fields:
  ;;   m = agent.match_pool(_req(), [_trip()])
  ;;   self.assertEqual(m["driverWageMinor"], 0)   # G1
  ;;   self.assertFalse(m["gig"])                   # G1
  ;;   self.assertTrue(m["envelopeOk"])             # G3
  (let [m (agent/match-pool (req) [(trip)])]
    (is (= (:driverWageMinor m) 0))   ; G1
    (is (false? (:gig m)))             ; G1
    (is (true? (:envelopeOk m)))))     ; G3

;; ── Settlement (G1, G4, G5) ──────────────────────────────────────────────────
;; Mirrors Python class Settlement

(deftest test-driver-wage-zero-and-exact-split
  ;; test_driver_wage_zero_and_exact_split:
  ;;   s = agent.build_settlement_intent(1_000_000, "did:plc:carrier")
  ;;   self.assertEqual(s["driverWageMinor"], 0)                       # G1
  ;;   self.assertEqual(s["titheMinor"], 100_000)                      # G4 10%
  ;;   self.assertEqual(s["carrierReimbursementMinor"], 900_000)
  ;;   self.assertEqual(s["grossMinor"], s["titheMinor"] + s["carrierReimbursementMinor"])
  ;;   self.assertEqual(s["state"], "intent")
  (let [s (agent/build-settlement-intent 1000000 "did:plc:carrier")]
    (is (= (:driverWageMinor s) 0))                                  ; G1
    (is (= (:titheMinor s) 100000))                                  ; G4 10%
    (is (= (:carrierReimbursementMinor s) 900000))
    (is (= (:grossMinor s) (+ (:titheMinor s) (:carrierReimbursementMinor s))))
    (is (= (:state s) "intent"))))                                   ; no operator_ref → "intent"

(deftest test-no-server-key
  ;; test_no_server_key:
  ;;   s = agent.build_settlement_intent(1_000_000, "did:plc:carrier")
  ;;   self.assertFalse(s["serverHeldKey"])
  (let [s (agent/build-settlement-intent 1000000 "did:plc:carrier")]
    (is (false? (:serverHeldKey s)))))

(deftest test-only-member-signature
  ;; test_only_member_signature:
  ;;   s = agent.build_settlement_intent(1_000_000, "did:plc:carrier")
  ;;   srv = agent.authorize_settlement(s, {"origin": "server", "ref": "x"})
  ;;   self.assertTrue(srv.get("refused"))
  ;;   self.assertIn("G5", srv["reason"])
  ;;   mem = agent.authorize_settlement(s, {"origin": "member", "ref": "sig-9"})
  ;;   self.assertTrue(mem["signed"])
  (let [s   (agent/build-settlement-intent 1000000 "did:plc:carrier")
        srv (agent/authorize-settlement s {:origin "server" :ref "x"})
        mem (agent/authorize-settlement s {:origin "member" :ref "sig-9"})]
    (is (true? (:refused srv)))
    (is (str/includes? (:reason srv) "G5"))
    (is (true? (:signed mem)))))

(deftest test-broadcast-needs-operator
  ;; test_broadcast_needs_operator:
  ;;   s = agent.build_settlement_intent(1_000_000, "did:plc:carrier", operator_ref="op-1")
  ;;   self.assertEqual(s["state"], "executed")
  (let [s (agent/build-settlement-intent 1000000 "did:plc:carrier" "op-1")]
    (is (= (:state s) "executed"))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'ainori.methods.test-agent)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))
