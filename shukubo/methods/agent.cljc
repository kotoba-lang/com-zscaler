(ns shukubo.methods.agent
  "shukubo 宿坊 — pilgrim-lodging commons (the Airbnb/Hotels inversion). 1:1 port of py/agent.py.
  Three concentric rings (commons → internal → external). Structural invariants: no commission
  (G2 — no take-rate field; Ring1 gross = tithe + hostNet exactly; Ring2 is a handoff), no surge
  (G13 — flat/cost-share only), hospitality-dignity (G12 — only the SPACE's habitability is
  attested, never a person score), privacy (G14/G9 — noSurveil ≡ true). The optional `from kotoba
  import datalog, llm` host binding is unused and is the omitted leg.

  Note: stay/booking ids derive from a deterministic hash of (host+title)/(guest+checkIn); the
  Python original used the per-process-salted builtin hash(), so callers only rely on the FORMAT
  and on same-input→same-id (which Clojure's deterministic hash preserves), never the exact value."
  (:require [clojure.string :as str]))

(def TITHE-BPS 1000)   ; 10% TitheRouter auto-split (G7), basis points
;; Ring ordering is constitutional (G4): covenantal hospitality before internal before external.
(def RING-ORDER ["commons" "internal" "external"])
(def ^:private REQUIRED-HABITABILITY ["water" "heat" "egress"])

(defn- id16 [s] (format "%04x" (bit-and (hash s) 0xFFFF)))

(defn list-stay
  "Register a lodging offer. NO commission, NO surge/dynamic price, NO guest/host score — only the
  SPACE's habitability is attested (G12). noSurveil is a constant invariant (G14)."
  [& {:keys [host-did ring kind title capacity cost-mode cost-minor habitability
             operator-url availability sourcing]
      :or {capacity 1 cost-mode "cost-share" cost-minor 0 habitability "water+heat+egress"
           operator-url "" availability "available" sourcing "authoritative"}}]
  (when-not (some #{ring} RING-ORDER)
    (throw (ex-info (str "unknown ring " (pr-str ring)) {:ring ring})))
  {"stayId" (str "shukubo." kind "." (id16 (str host-did title)))
   "ring" ring "kind" kind "hostDid" host-did "title" title
   "capacity" (long capacity)
   "costMode" cost-mode                 ; free | cost-share | fixed — never demand-priced (G13)
   "costMinor" (long cost-minor)
   "habitability" habitability          ; the SPACE is attested, never the person (G12)
   "noSurveil" true                     ; G14 invariant — no in-stay cameras/biometrics
   "operatorUrl" operator-url           ; external ring only: operator's OWN booking page
   "availability" availability
   "sourcing" sourcing})                ; G10 honesty

(defn discover-stays
  "need → Ring 0 commons → Ring 1 internal → Ring 2 external. Returns the first non-empty ring as
  resolved_ring (commons-first, G4) but carries the full ordered set."
  [_need-text stays]
  (let [by-ring (into {} (map (fn [r]
                                [r (vec (sort-by #(long (get % "costMinor" 0))
                                                 (filter #(= (get % "ring") r) stays)))])
                              RING-ORDER))
        resolved (or (some (fn [r] (when (seq (get by-ring r)) r)) RING-ORDER) "unresolved")
        ordered (vec (mapcat #(get by-ring %) RING-ORDER))]
    {"resolved_ring" resolved "candidates" ordered}))

(defn build-settlement-intent
  "Ring-1 stay settlement. gross = flat cost-share; tithe 10% (G7); hostNet = gross − tithe; NO
  platform commission (G2: gross = tithe + hostNet exactly).
  G11/G8 (FINDING 260617): execution is gated — WITH an operator-ref the intent is operator-executed
  (state 'executed'); WITHOUT one it stays an 'intent' that only a MEMBER signature can execute
  (authorize-settlement). The server never auto-executes (G8 no-server-key, never relaxed)."
  ([gross-minor host-did] (build-settlement-intent gross-minor host-did nil))
  ([gross-minor host-did operator-ref]
   (let [gross (long gross-minor)
         tithe (quot (* gross TITHE-BPS) 10000)
         host-net (- gross tithe)]
     {"rail" "usdc-base-l2" "grossMinor" gross
      "commissionMinor" 0               ; G2: structural zero — shukubo takes nothing
      "titheMinor" tithe "hostNetMinor" host-net "hostDid" host-did
      "titheRouter" "50-infra/etzhayyim-tithe-router"
      "serverHeldKey" false             ; G8 invariant
      ;; G11/G8: operator-gated execution; absent an operator it stays an intent a member must sign
      "state" (if operator-ref "executed" "intent")
      "operatorRef" (or operator-ref "autonomous_r2") "signed" false})))

(defn authorize-settlement
  "Only a member-origin signature authorizes (G8 no-server-key); server signature refused."
  [settlement signature]
  (cond
    (not= (get signature "origin") "member")
    (merge settlement {"signed" false "refused" true
                       "reason" "only a member passkey/wallet signature authorizes (G8 no-server-key)"})
    (get settlement "serverHeldKey")
    (merge settlement {"signed" false "refused" true
                       "reason" "settlement carries a server-held key — invariant violation (G8)"})
    :else
    ;; member signature authorizes → the intent transitions to executed (member is the write author)
    (merge settlement {"signed" true "state" "executed" "signatureRef" (get signature "ref")})))

(defn dates-overlap
  "Half-open date-interval overlap [checkIn, checkOut). Adjacent stays do NOT overlap. ISO date
  strings compare lexically."
  [in1 out1 in2 out2]
  (and (neg? (compare in1 out2)) (neg? (compare in2 out1))))

(defn stay-available
  "True iff no CONFIRMED booking for this stay overlaps the requested dates (no-double-book)."
  [stay-id check-in check-out confirmed-bookings]
  (not (some (fn [b]
               (and (= (get b "stayId") stay-id)
                    (contains? #{"confirmed" "settle-intent"} (get b "state"))
                    (dates-overlap check-in check-out (get b "checkIn" "") (get b "checkOut" ""))))
             confirmed-bookings)))

(defn book
  "Route a reservation by ring (Ring0 commons / Ring1 internal SBT↔SBT settle-intent / Ring2
  external self-book handoff). Requires consent (G1). Commons/internal stays refuse a date range
  overlapping a confirmed booking (no-double-book); external-mirror stays are not shukubo inventory."
  ([stay guest-did check-in check-out consent-ref sbt-registry]
   (book stay guest-did check-in check-out consent-ref sbt-registry nil))
  ([stay guest-did check-in check-out consent-ref sbt-registry confirmed-bookings]
   (let [ring (get stay "ring")]
     (cond
       (not (seq consent-ref))
       {"state" "refused" "reason" "missing DID-signed consent (G1)"}
       (and (contains? #{"commons" "internal"} ring) (seq confirmed-bookings)
            (not (stay-available (get stay "stayId") check-in check-out confirmed-bookings)))
       {"state" "refused" "reason" "stay already booked for those dates (no-double-book)"}
       :else
       (let [common {"bookingId" (str (get stay "stayId") ".bk." (id16 (str guest-did check-in)))
                     "stayId" (get stay "stayId") "guestDid" guest-did "ring" ring
                     "checkIn" check-in "checkOut" check-out "consentRef" consent-ref
                     "recordEnc" true}]   ; G9: booking PII encrypted
         (cond
           (= ring "commons")
           (merge common {"state" "confirmed" "costShareMinor" (long (get stay "costMinor" 0))
                          "settlement" "commons-none" "titheMinor" 0})
           (= ring "internal")
           (if-not (get sbt-registry guest-did false)
             (merge common {"state" "refused" "reason" "guest not an active Adherent SBT holder (§3)"})
             (let [settlement (build-settlement-intent (long (get stay "costMinor" 0)) (get stay "hostDid"))]
               (merge common {"state" "settle-intent" "settlement" settlement
                              "titheMinor" (get settlement "titheMinor")})))
           (= ring "external")
           (merge common {"state" "self-book-handoff" "principal" "member"
                          "handoffUrl" (get stay "operatorUrl" "") "settlement" "external-none"
                          "titheMinor" 0})
           :else
           (merge common {"state" "refused" "reason" (str "unknown ring " (pr-str ring))})))))))

(defn register-host
  "Register a stay's host. Attests the SPACE's habitability (G12) + enforces the privacy invariant
  (G14 noSurveil). A stay advertising in-stay surveillance, or lacking minimum habitability, is
  refused. No host/guest score field exists (G12)."
  [host-did stay]
  (if (not (true? (get stay "noSurveil")))
    {"state" "refused" "reason" "in-stay surveillance not permitted as a feature (G14)"}
    (let [habit (str/lower-case (or (get stay "habitability") ""))
          missing (filterv #(not (str/includes? habit %)) REQUIRED-HABITABILITY)]
      (if (seq missing)
        {"state" "refused" "reason" (str "habitability attestation missing " missing " (G12)")}
        {"state" "registered" "hostDid" host-did "stayId" (get stay "stayId")
         "ring" (get stay "ring") "habitability" (get stay "habitability") "noSurveil" true}))))
