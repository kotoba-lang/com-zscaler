(ns omise.methods.agent
  "omise 御店 — seller-side storefront commons (the Shopify layer for charter-clean internal
  sellers). 1:1 port of py/agent.py. Listings are okaimono Ring-1 products by construction (G11).
  Structural invariants: ZERO platform commission (G2 — commissionMinor ≡ 0, gross = tithe +
  sellerNet exactly), kotoba-EAVT-native (G6), no-server-key (G12 — only a member signature
  authorizes), okaimono Ring-1 coherence (G11 — to-okaimono-product maps onto the canonical product
  shape with no glue). The optional `from kotoba import datalog, llm` host binding is unused and is
  the omitted leg.

  Note: build-settlement-intent returns state \"executed\" (R2 Autonomous — operator_ref no longer
  required); listing/order ids derive from a deterministic hash (the Python builtin hash() was
  per-process-salted, so only format + same-input→same-id are relied on)."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)   ; 10% TitheRouter auto-split (G7), basis points
;; Sellers are SBT-gated (G3): a producing actor OR an active Adherent SBT member.
(def ^:private PRODUCING-ACTORS
  #{"makura" "mitsuho" "yakushi" "tsutae" "futawa" "hikari" "sanae" "hataori"})
;; Order as-of trajectory (G13: caps at :in-use, never terminal).
(def ORDER-STATES ["cart" "placed" "settle-intent" "fulfilling" "delivered" "in-use"])
;; Item-class → etzhayyim logistics actor (G8: no gig labor).
(def ^:private FULFILLMENT {"heavy" "sarutahiko" "road" "todoke" "bulky" "haraedo"})
(def ^:private LABOR-RANK {"etzhayyim-dignity" 3 "verified-fair" 2 "disclosed" 1 "unknown" 0})

(defn- last-seg [s] (last (str/split s #":")))
(defn- id16 [s] (format "%04x" (bit-and (hash s) 0xFFFF)))

(defn seller-kind
  "Classify a seller. A storefront may be opened only by a producing actor or an active Adherent
  SBT member (SBT↔SBT carve-out, G3)."
  [seller-did sbt-registry]
  (let [actor-id (when (str/starts-with? seller-did "did:web:etzhayyim.com:") (last-seg seller-did))]
    (cond
      (contains? PRODUCING-ACTORS actor-id)
      {"eligible" true "kind" "producing-actor" "reason" (str actor-id " is a producing actor")}
      (get sbt-registry seller-did false)
      {"eligible" true "kind" "sbt-member" "reason" "active Adherent SBT member"}
      :else
      {"eligible" false "kind" nil
       "reason" "seller is neither a producing actor nor an active SBT member (G3); external onboarding is Council Lv7+"})))

(defn open-storefront
  "Open a storefront for a gated seller (G3). No subscription/listing fee exists (G2)."
  [seller-did name sbt-registry]
  (let [sk (seller-kind seller-did sbt-registry)]
    (if-not (get sk "eligible")
      {"state" "refused" "reason" (get sk "reason")}
      {"state" "open" "storefrontId" (str "omise." (last-seg seller-did))
       "sellerDid" seller-did "sellerKind" (get sk "kind") "name" name
       "subscriptionMinor" 0})))   ; G2: no platform subscription, ever

(defn create-listing
  "Create a listing on an open storefront. ring is constant 'internal' and there is NO commission/
  take-rate field (G2). Shape-compatible with okaimono's product record (G11)."
  [storefront title price-minor & {:keys [maker-actor inventory durability-years repairability
                                          labor-provenance carbon-kg lifecycle-route item-class]
                                   :or {inventory 0 durability-years 0.0 repairability 0
                                        labor-provenance "disclosed" carbon-kg 0.0
                                        lifecycle-route "hodoki" item-class "road"}}]
  (let [seller-did (get storefront "sellerDid")
        maker (or maker-actor (if (= (get storefront "sellerKind") "producing-actor")
                                (last-seg seller-did) "member"))]
    {"listingId" (str (get storefront "storefrontId") "." (id16 title))
     "storefrontId" (get storefront "storefrontId") "sellerDid" seller-did "title" title
     "makerActor" maker "priceMinor" (long price-minor) "currency" "USDC"
     "inventory" (long inventory) "durabilityYears" (double durability-years)
     "repairability" (long repairability) "laborProvenance" labor-provenance
     "carbonKg" (double carbon-kg) "lifecycleRoute" lifecycle-route
     "fulfilmentActor" (get FULFILLMENT item-class "todoke")
     "ring" "internal" "sourcing" "authoritative"}))   ; ring const (G11)

(defn to-okaimono-product
  "Map an omise listing onto the canonical com.etzhayyim.okaimono.product :ring 'internal' shape
  (G11) — exact key set, no integration glue."
  [listing]
  {"productId" (str "int." (get listing "makerActor") "." (last (str/split (get listing "listingId") #"\.")))
   "title" (get listing "title") "ring" "internal" "unspsc" (get listing "unspsc" "")
   "makerActor" (get listing "makerActor") "source" "internal-actor"
   "priceMinor" (get listing "priceMinor") "currency" "USDC"
   "durabilityYears" (get listing "durabilityYears") "repairability" (get listing "repairability")
   "laborProvenance" (get listing "laborProvenance") "carbonKg" (get listing "carbonKg")
   "lifecycleRoute" (get listing "lifecycleRoute") "sourcing" (get listing "sourcing")})

(defn- wellbecoming-score
  "Higher = better. Durability + repairability + dignified labor, lightly penalize carbon + price.
  NEVER engagement/upsell (G5)."
  [p]
  (- (+ (* (double (get p "durabilityYears" 0.0)) 2.0)
        (* (double (get p "repairability" 0)) 1.5)
        (* (get LABOR-RANK (get p "laborProvenance" "unknown") 0) 3.0))
     (* (double (get p "carbonKg" 0.0)) 0.1)
     (* (/ (double (get p "priceMinor" 0)) 1000000.0) 0.05)))

(defn storefront-ordering
  "Order a storefront's listings by Wellbecoming (G5) — never by paid placement (no such field)."
  [listings]
  (vec (sort-by wellbecoming-score > listings)))

(defn build-settlement-intent
  "USDC settlement with TitheRouter 10% auto-split (G7) and ZERO platform commission (G2):
  gross = tithe + sellerNet exactly. G10 (FINDING 260617): execution is gated — WITH an
  operator-ref the intent is operator-executed (state 'executed'); WITHOUT one it stays an
  'intent' that only a MEMBER signature can execute (authorize-settlement). The server never
  auto-executes (G12 no-server-key, never relaxed)."
  ([gross-minor seller-did] (build-settlement-intent gross-minor seller-did nil))
  ([gross-minor seller-did operator-ref]
   (let [gross (long gross-minor)
         tithe (quot (* gross TITHE-BPS) 10000)
         seller-net (- gross tithe)]   ; tithe rounds down ⇒ sellerNet absorbs remainder; sum exact
     {"rail" "usdc-base-l2" "grossMinor" gross
      "commissionMinor" 0             ; G2: structural zero — the platform takes nothing
      "titheMinor" tithe "sellerNetMinor" seller-net "sellerDid" seller-did
      "titheRouter" "50-infra/etzhayyim-tithe-router"
      "serverHeldKey" false           ; G12 invariant
      ;; G10: operator-gated execution; absent an operator it stays an intent a member must sign
      "state" (if operator-ref "executed" "intent")
      "operatorRef" (or operator-ref "autonomous_r2") "signed" false})))

(defn available-inventory
  "On-hand inventory minus the quantity reserved by still-active orders (a cancelled order releases
  its units). The honest available count (G5) + the basis of the no-oversell guard."
  ([listing] (available-inventory listing nil))
  ([listing open-orders]
   (let [reserved (reduce + 0 (for [o (or open-orders [])
                                    :when (and (= (get o "listingId") (get listing "listingId"))
                                               (not= (get o "state") "cancelled"))]
                                (long (get o "qty" 0))))]
     (- (long (get listing "inventory" 0)) reserved))))

(defn place-order
  "Ring-1 order entry. Requires buyer consent (G1) + active buyer SBT (G3), computes a zero-
  commission settlement intent (G2/G7) + a non-gig fulfilment (G8). Refuses if qty exceeds AVAILABLE
  inventory (no-oversell, G5)."
  ([buyer-did listing qty consent-ref sbt-registry]
   (place-order buyer-did listing qty consent-ref sbt-registry nil))
  ([buyer-did listing qty consent-ref sbt-registry open-orders]
   (cond
     (not (seq consent-ref))
     {"state" "refused" "reason" "missing DID-signed consent (G1)" "ring" "internal"}
     (not (get sbt-registry buyer-did false))
     {"state" "refused" "reason" "buyer is not an active Adherent SBT holder (§3/G3)" "ring" "internal"}
     (> (long qty) (available-inventory listing open-orders))
     {"state" "refused" "reason" "insufficient available inventory — no oversell (honest count, G5)" "ring" "internal"}
     :else
     (let [gross (* (long (get listing "priceMinor")) (long qty))
           settlement (build-settlement-intent gross (get listing "sellerDid"))]
       {"state" "settle-intent" "ring" "internal"
        "orderId" (str (get listing "listingId") ".ord." (id16 (str buyer-did consent-ref)))
        "buyerDid" buyer-did "listingId" (get listing "listingId") "qty" (long qty)
        "consentRef" consent-ref "subtotalMinor" gross "settlement" settlement
        "fulfilmentActor" (get listing "fulfilmentActor") "recordEnc" true}))))   ; G9

(defn authorize-settlement
  "Authorize a settlement intent. ONLY a member-origin signature (G12 no-server-key); a server
  signature is refused."
  [settlement signature]
  (cond
    (not= (get signature "origin") "member")
    (merge settlement {"signed" false "refused" true
                       "reason" "only a member passkey/wallet signature authorizes settlement (G12 no-server-key)"})
    (get settlement "serverHeldKey")
    (merge settlement {"signed" false "refused" true
                       "reason" "settlement carries a server-held key — invariant violation (G12)"})
    :else
    ;; member signature authorizes → the intent transitions to executed (member is the write author)
    (merge settlement {"signed" true "state" "executed" "signatureRef" (get signature "ref")})))

(defn advance-order
  "Move an order one step along ORDER-STATES (caps at :in-use, never terminal, G13)."
  [order]
  (let [st (get order "state")]
    (if-not (contains? (set ORDER-STATES) st)
      order
      (let [i (.indexOf ORDER-STATES st)]
        (merge order {"state" (nth ORDER-STATES (min (inc i) (dec (count ORDER-STATES))))})))))

(defn cancel-order
  "Cancel an order, releasing its inventory reservation. A delivered/in-use order cannot be
  cancelled. Append-only state (G7)."
  [order]
  (if (contains? #{"delivered" "in-use"} (get order "state"))
    (merge order {"refused" true "reason" "cannot cancel an order already delivered"})
    (merge order {"state" "cancelled"})))

(defn build-fulfilment
  "Hand an order to an etzhayyim logistics actor; never a gig courier (G8). No server key (G12)."
  ([order] (build-fulfilment order "jp"))
  ([order region]
   {"orderId" (get order "orderId") "fulfilmentActor" (get order "fulfilmentActor" "todoke")
    "region" region "gig" false "serverSigned" false "state" "handed-off"}))
