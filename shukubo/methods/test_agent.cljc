(ns shukubo.methods.test-agent
  "shukubo 宿坊 — pilgrim-lodging tests. 1:1 port of py/test_agent.py. Verifies the structural
  invariants of ADR-2606071600: G2 no-commission (no field; Ring1 gross = tithe + hostNet; Ring2
  handoff), G4 commons-first ordering, G7 tithe 10%, G8 no-server-key, G12 hospitality-dignity (no
  person score; space habitability only), G13 no-surge, G14 privacy (noSurveil)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [shukubo.methods.agent :as agent]))

(def SBT {"did:plc:pilgrim" true "did:plc:lapsed" false})

(defn- stay* [ring & kvs]
  (apply agent/list-stay :host-did "did:plc:host" :ring ring :kind "member-room" :title "Quiet room"
         :capacity 2 :cost-mode "fixed" :cost-minor 5000000 kvs))

;; ── list_stay ──
(deftest test-no-commission-field
  (is (every? #(not (str/includes? (str/lower-case %) "commission")) (keys (stay* "internal")))))

(deftest test-no-surge-field
  (let [ks (keys (stay* "internal"))]
    (is (every? #(not (str/includes? (str/lower-case %) "surge")) ks))
    (is (every? #(not (str/includes? (str/lower-case %) "dynamic")) ks))))

(deftest test-no-person-score-field
  (let [s (stay* "internal")]
    (is (every? #(not (str/includes? (str/lower-case %) "score")) (keys s)))
    (is (every? #(not (str/includes? (str/lower-case %) "rating")) (keys s)))
    (is (contains? s "habitability"))))

(deftest test-no-surveil-invariant
  (is (true? (get (stay* "internal") "noSurveil"))))   ; G14

;; ── discover ──
(deftest test-commons-first
  (let [stays [(stay* "external" :cost-minor 3000000) (stay* "commons" :cost-minor 0)
               (stay* "internal" :cost-minor 5000000)]
        out (agent/discover-stays "need a bed" stays)]
    (is (= "commons" (get out "resolved_ring")))
    (is (= "commons" (get (first (get out "candidates")) "ring")))   ; G4 ordering
    (is (= ["commons" "internal" "external"] (mapv #(get % "ring") (get out "candidates"))))))

;; ── settlement ──
(deftest test-zero-commission-exact-split
  (let [s (agent/build-settlement-intent 5000000 "did:plc:host")]
    (is (= 0 (get s "commissionMinor")))                ; G2
    (is (= 500000 (get s "titheMinor")))                ; G7
    (is (= 4500000 (get s "hostNetMinor")))
    (is (= (get s "grossMinor") (+ (get s "titheMinor") (get s "hostNetMinor"))))
    (is (= "intent" (get s "state")))                    ; G11/G8: no operator ⇒ unsigned INTENT, not auto-executed
    (is (= "executed"                                    ; G11: an operator-ref gates execution
           (get (agent/build-settlement-intent 5000000 "did:plc:host" "op-ref-1") "state")))))

(deftest test-no-server-key
  (is (= false (get (agent/build-settlement-intent 1 "h") "serverHeldKey"))))

(deftest test-only-member-signature
  (let [s (agent/build-settlement-intent 1000000 "did:plc:host")
        srv (agent/authorize-settlement s {"origin" "server" "ref" "x"})
        mem (agent/authorize-settlement s {"origin" "member" "ref" "sig"})]
    (is (get srv "refused"))
    (is (str/includes? (get srv "reason") "G8"))
    (is (get mem "signed"))
    (is (= "executed" (get mem "state")))))             ; G11/G8: a member signature is what executes an intent

;; ── booking ──
(deftest test-consent-required
  (let [b (agent/book (stay* "internal") "did:plc:pilgrim" "d1" "d2" "" SBT)]
    (is (= "refused" (get b "state")))
    (is (str/includes? (get b "reason") "G1"))))

(deftest test-commons-free-no-settlement
  (let [b (agent/book (stay* "commons" :cost-mode "free" :cost-minor 0)
                      "did:plc:anyone" "d1" "d2" "consent" SBT)]
    (is (= "confirmed" (get b "state")))
    (is (= "commons-none" (get b "settlement")))
    (is (= 0 (get b "titheMinor")))))

(deftest test-internal-requires-sbt-and-settles
  (let [ok (agent/book (stay* "internal") "did:plc:pilgrim" "d1" "d2" "consent" SBT)
        no (agent/book (stay* "internal") "did:plc:lapsed" "d1" "d2" "consent" SBT)]
    (is (= "settle-intent" (get ok "state")))
    (is (= 0 (get-in ok ["settlement" "commissionMinor"])))   ; G2
    (is (= 500000 (get ok "titheMinor")))                     ; G7
    (is (= "refused" (get no "state")))))

(deftest test-external-is-handoff-no-inflow
  (let [b (agent/book (stay* "external" :operator-url "https://inn.example/book")
                      "did:plc:pilgrim" "d1" "d2" "consent" SBT)]
    (is (= "self-book-handoff" (get b "state")))
    (is (= "member" (get b "principal")))                ; shukubo is NOT the buyer (G2)
    (is (= "external-none" (get b "settlement")))
    (is (= 0 (get b "titheMinor")))
    (is (= "https://inn.example/book" (get b "handoffUrl")))))

;; ── no-double-book ──
(deftest test-dates-overlap
  (is (agent/dates-overlap "2026-06-01" "2026-06-05" "2026-06-03" "2026-06-08")))

(deftest test-adjacent-not-overlap
  (is (not (agent/dates-overlap "2026-06-01" "2026-06-05" "2026-06-05" "2026-06-08"))))

(deftest test-stay-available-when-no-conflict
  (let [confirmed [{"stayId" "s1" "state" "confirmed" "checkIn" "2026-06-10" "checkOut" "2026-06-12"}]]
    (is (agent/stay-available "s1" "2026-06-01" "2026-06-05" confirmed))))

(deftest test-internal-booking-refused-on-overlap
  (let [confirmed [{"stayId" (get (stay* "internal") "stayId") "state" "confirmed"
                    "checkIn" "2026-06-01" "checkOut" "2026-06-05"}]
        out (agent/book (stay* "internal") "did:plc:pilgrim" "2026-06-03" "2026-06-07"
                        "consent" SBT confirmed)]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "no-double-book"))))

(deftest test-internal-booking-ok-when-free
  (let [confirmed [{"stayId" (get (stay* "internal") "stayId") "state" "confirmed"
                    "checkIn" "2026-06-10" "checkOut" "2026-06-12"}]
        out (agent/book (stay* "internal") "did:plc:pilgrim" "2026-06-01" "2026-06-05"
                        "consent" SBT confirmed)]
    (is (= "settle-intent" (get out "state")))))

(deftest test-external-not-blocked-by-availability
  (let [confirmed [{"stayId" (get (stay* "external") "stayId") "state" "confirmed"
                    "checkIn" "2026-06-01" "checkOut" "2026-06-30"}]
        out (agent/book (stay* "external" :operator-url "https://inn/x") "did:plc:pilgrim"
                        "2026-06-02" "2026-06-04" "consent" SBT confirmed)]
    (is (= "self-book-handoff" (get out "state")))))

;; ── host registration ──
(deftest test-registers-with-habitability
  (let [out (agent/register-host "did:plc:host" (stay* "internal" :habitability "water+heat+egress"))]
    (is (= "registered" (get out "state")))
    (is (every? #(not (str/includes? (str/lower-case %) "score")) (keys out)))   ; G12
    (is (every? #(not (str/includes? (str/lower-case %) "rating")) (keys out)))))

(deftest test-missing-habitability-refused
  (let [out (agent/register-host "did:plc:host" (stay* "internal" :habitability "water"))]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "G12"))))
