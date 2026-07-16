(ns omise.methods.test-agent
  "omise 御店 — seller storefront tests. 1:1 port of py/test_agent.py. Verifies ADR-2606071400:
  G2 zero commission (gross = tithe + sellerNet), G3 seller-gating, G7 tithe 10%, G11 okaimono
  coherence, G12 no-server-key, G5 wellbecoming ordering, G13 order trajectory caps at :in-use.

  G10/G12 settlement-state (FINDING 260617): an earlier 'R2 Autonomous' edit had made
  build-settlement-intent unconditionally state=\"executed\" (an unsigned settlement auto-executed),
  and this test had been 'corrected' to ratify that bypass. Restored: no operator ⇒ \"intent\"
  (only a member signature executes it, via authorize-settlement); operator present ⇒ operator-gated
  \"executed\"; the server never auto-executes (G12 no-server-key)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [omise.methods.agent :as agent]))

(def SBT {"did:web:etzhayyim.com:mitsuho" true   ; producing actor (also gated by name)
          "did:plc:buyer-alice" true             ; active SBT member buyer
          "did:plc:seller-bob" true              ; active SBT member seller
          "did:plc:lapsed" false})

(defn- open-actor-storefront [] (agent/open-storefront "did:web:etzhayyim.com:mitsuho" "Mitsuho Rice" SBT))

;; ── seller gating ──
(deftest test-producing-actor-opens
  (let [sf (open-actor-storefront)]
    (is (= "open" (get sf "state")))
    (is (= "producing-actor" (get sf "sellerKind")))))

(deftest test-sbt-member-opens
  (let [sf (agent/open-storefront "did:plc:seller-bob" "Bob's Goods" SBT)]
    (is (= "open" (get sf "state")))
    (is (= "sbt-member" (get sf "sellerKind")))))

(deftest test-non-member-refused
  (let [sf (agent/open-storefront "did:plc:stranger" "Random Shop" SBT)]
    (is (= "refused" (get sf "state")))
    (is (str/includes? (get sf "reason") "G3"))))

(deftest test-lapsed-member-refused
  (is (= "refused" (get (agent/open-storefront "did:plc:lapsed" "Lapsed" SBT) "state"))))

(deftest test-no-subscription-fee
  (is (= 0 (get (open-actor-storefront) "subscriptionMinor"))))

;; ── listing ──
(defn- a-listing []
  (agent/create-listing (open-actor-storefront) "Koshihikari 5kg" 8000000
                        :inventory 40 :durability-years 1.0 :repairability 0
                        :labor-provenance "etzhayyim-dignity" :carbon-kg 2.1
                        :lifecycle-route "commons-return" :item-class "road"))

(deftest test-ring-is-internal-const
  (is (= "internal" (get (a-listing) "ring"))))

(deftest test-no-commission-field
  (let [l (a-listing)]
    (is (every? #(not (str/includes? (str/lower-case %) "commission")) (keys l)))
    (is (every? #(not (str/includes? (str/replace (str/lower-case %) "_" "") "takerate")) (keys l)))))

(deftest test-no-sponsored-field
  (let [l (a-listing)]
    (is (every? #(not (str/includes? (str/lower-case %) "sponsor")) (keys l)))
    (is (every? #(not (str/includes? (str/lower-case %) "boost")) (keys l)))))

(deftest test-fulfilment-is-non-gig-actor
  (is (= "todoke" (get (a-listing) "fulfilmentActor"))))

(deftest test-okaimono-coherence-shape
  (let [prod (agent/to-okaimono-product (a-listing))]
    (is (= #{"productId" "title" "ring" "unspsc" "makerActor" "source" "priceMinor" "currency"
             "durabilityYears" "repairability" "laborProvenance" "carbonKg" "lifecycleRoute" "sourcing"}
           (set (keys prod))))
    (is (= "internal" (get prod "ring")))
    (is (= "internal-actor" (get prod "source")))
    (is (= "mitsuho" (get prod "makerActor")))
    (is (= 8000000 (get prod "priceMinor")))))

;; ── ordering ──
(deftest test-wellbecoming-not-price
  (let [durable {"durabilityYears" 10 "repairability" 9 "laborProvenance" "etzhayyim-dignity"
                 "carbonKg" 5 "priceMinor" 20000000}
        throwaway {"durabilityYears" 0.5 "repairability" 0 "laborProvenance" "unknown"
                   "carbonKg" 8 "priceMinor" 2000000}
        ranked (agent/storefront-ordering [throwaway durable])]
    (is (= durable (first ranked)))))

;; ── settlement ──
(deftest test-zero-commission-and-exact-split
  (let [s (agent/build-settlement-intent 10000000 "did:web:etzhayyim.com:mitsuho")]
    (is (= 0 (get s "commissionMinor")))                            ; G2
    (is (= 1000000 (get s "titheMinor")))                           ; G7 10%
    (is (= 9000000 (get s "sellerNetMinor")))
    (is (= (get s "grossMinor") (+ (get s "titheMinor") (get s "sellerNetMinor"))))
    (is (= "intent" (get s "state")))))                             ; G10/G12: no operator ⇒ unsigned INTENT, not auto-executed

(deftest test-remainder-absorbed-no-loss
  (let [s (agent/build-settlement-intent 10000007 "did:plc:seller-bob")]
    (is (= (get s "grossMinor") (+ (get s "titheMinor") (get s "sellerNetMinor"))))))

(deftest test-broadcast-needs-operator
  (is (= "executed" (get (agent/build-settlement-intent 1000000 "did:plc:seller-bob" "op-ref-1") "state"))))

(deftest test-no-server-key-invariant
  (is (= false (get (agent/build-settlement-intent 1000000 "did:plc:seller-bob") "serverHeldKey"))))

(deftest test-only-member-signature-authorizes
  (let [s (agent/build-settlement-intent 1000000 "did:plc:seller-bob")
        server (agent/authorize-settlement s {"origin" "server" "ref" "x"})
        member (agent/authorize-settlement s {"origin" "member" "ref" "sig-123"})]
    (is (get server "refused"))
    (is (str/includes? (get server "reason") "G12"))
    (is (get member "signed"))
    (is (= "executed" (get member "state")))            ; G10/G12: a member signature is what executes an intent
    (is (= "sig-123" (get member "signatureRef")))))

;; ── order flow ──
(defn- flow-listing [] (agent/create-listing (open-actor-storefront) "Koshihikari 5kg" 8000000 :inventory 40))

(deftest test-happy-path-settle-intent
  (let [o (agent/place-order "did:plc:buyer-alice" (flow-listing) 2 "consent-abc" SBT)]
    (is (= "settle-intent" (get o "state")))
    (is (= 16000000 (get o "subtotalMinor")))
    (is (= 0 (get-in o ["settlement" "commissionMinor"])))          ; G2
    (is (= "todoke" (get o "fulfilmentActor")))                     ; G8
    (is (get o "recordEnc"))))                                      ; G9

(deftest test-consent-required
  (let [o (agent/place-order "did:plc:buyer-alice" (flow-listing) 1 "" SBT)]
    (is (= "refused" (get o "state")))
    (is (str/includes? (get o "reason") "G1"))))

(deftest test-buyer-must-be-sbt
  (let [o (agent/place-order "did:plc:stranger" (flow-listing) 1 "consent-abc" SBT)]
    (is (= "refused" (get o "state")))
    (is (str/includes? (get o "reason") "G3"))))

(deftest test-inventory-enforced
  (let [o (agent/place-order "did:plc:buyer-alice" (flow-listing) 999 "consent-abc" SBT)]
    (is (= "refused" (get o "state")))
    (is (str/includes? (get o "reason") "inventory"))))

(deftest test-trajectory-caps-at-in-use
  (let [o0 (agent/place-order "did:plc:buyer-alice" (flow-listing) 1 "consent-abc" SBT)
        o (reduce (fn [o _] (agent/advance-order o)) o0 (range 10))]
    (is (= "in-use" (get o "state")))))                             ; G13

;; ── no-oversell ──
(defn- inv3-listing [] (agent/create-listing (open-actor-storefront) "Koshihikari 5kg" 8000000 :inventory 3))

(deftest test-available-minus-active-reservations
  (let [l (inv3-listing)
        orders [{"listingId" (get l "listingId") "qty" 2 "state" "settle-intent"}]]
    (is (= 1 (agent/available-inventory l orders)))))

(deftest test-cancelled-order-releases-inventory
  (let [l (inv3-listing)
        orders [{"listingId" (get l "listingId") "qty" 3 "state" "cancelled"}]]
    (is (= 3 (agent/available-inventory l orders)))))

(deftest test-oversell-refused
  (let [l (inv3-listing)
        existing [{"listingId" (get l "listingId") "qty" 2 "state" "settle-intent"}]
        out (agent/place-order "did:plc:buyer-alice" l 2 "c" SBT existing)]
    (is (= "refused" (get out "state")))
    (is (str/includes? (get out "reason") "oversell"))))

(deftest test-order-within-available-ok
  (let [l (inv3-listing)
        existing [{"listingId" (get l "listingId") "qty" 2 "state" "settle-intent"}]
        out (agent/place-order "did:plc:buyer-alice" l 1 "c" SBT existing)]
    (is (= "settle-intent" (get out "state")))))

(deftest test-cancel-then-reorder
  (let [l (inv3-listing)
        cancelled [{"listingId" (get l "listingId") "qty" 3 "state" "cancelled"}]
        out (agent/place-order "did:plc:buyer-alice" l 3 "c" SBT cancelled)]
    (is (= "settle-intent" (get out "state")))))

;; ── order cancel ──
(deftest test-cancel-sets-state
  (is (= "cancelled" (get (agent/cancel-order {"state" "settle-intent" "orderId" "o1"}) "state"))))

(deftest test-cannot-cancel-delivered
  (is (get (agent/cancel-order {"state" "delivered" "orderId" "o1"}) "refused")))

;; ── fulfilment ──
(deftest test-non-gig-handoff
  (let [f (agent/build-fulfilment {"orderId" "o1" "fulfilmentActor" "todoke"})]
    (is (= "todoke" (get f "fulfilmentActor")))
    (is (= false (get f "gig")))            ; G8
    (is (= false (get f "serverSigned")))   ; G12
    (is (= "handed-off" (get f "state")))))
