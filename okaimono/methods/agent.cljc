(ns okaimono.methods.agent
  "okaimono 御買物 — provisioning-commons cell (the Amazon inversion). 1:1 port of py/agent.py.
  Three concentric rings (commons → internal → external) over R0–R3: discover/compare/basket/
  provision/lifecycle + Ring-1 internal economy (SBT↔SBT, G2/G7) + Ring-2 external catalog (G3
  affiliate-strip, data-only) + R3 assisted member-principal checkout (G14/G15/G9). Pure compute;
  the Murakumo llm rerank + datalog catalog are omitted legs (no-op / empty when absent); the abaki
  anti-monopoly policy is read from disk only if present (graceful skip)."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            #?(:clj [cheshire.core :as json])))

(def RING-ORDER ["commons" "internal" "external"])
(def TITHE-BPS 1000)
(def ^:private LABOR-RANK {"etzhayyim-dignity" 3 "verified-fair" 2 "disclosed" 1 "unknown" 0})
(def ^:private MAKER-ACTORS #{"makura" "mitsuho" "yakushi" "tsutae" "futawa" "hikari"})
(def ^:private ORDER-STATES ["cart" "placed" "settle-intent" "fulfilling" "delivered" "in-use"])
(def ^:private FULFILLMENT {"heavy" "sarutahiko" "road" "wadachi" "bulky" "haraedo"})

(defn- wellbecoming-score [p]
  (let [durability (double (get p "durabilityYears" 0.0))
        repair (double (get p "repairability" 0))
        labor (get LABOR-RANK (get p "laborProvenance" "unknown") 0)
        carbon (double (get p "carbonKg" 0.0))
        price (/ (double (get p "priceMinor" 0)) 1000000.0)]
    (- (+ (* durability 2.0) (* repair 1.5) (* labor 3.0)) (* carbon 0.1) (* price 0.05))))

;; ── discover (commons-first three-ring) ───────────────────────────────────────
(defn- catalog-candidates [_need-text] [])   ; datalog host binding omitted → empty
(defn handle-discover [state]
  (let [cands (catalog-candidates (get state "need_text" ""))
        by-ring (into {} (map (fn [r] [r (filterv #(= (get % "ring") r) cands)]) RING-ORDER))
        resolved (or (some (fn [r] (when (seq (get by-ring r)) r)) RING-ORDER) "unresolved")]
    (merge state {"candidates" cands "resolved_ring" resolved})))

(defn handle-compare [state]
  (merge state {"ranked" (vec (sort-by wellbecoming-score > (get state "products" [])))}))

(defn handle-basket [state]
  (let [lines (get state "lines" [])
        items (reduce + 0 (map #(* (long (get % "priceMinor" 0)) (long (get % "qty" 1))) lines))
        shipping (reduce + 0 (map #(long (get % "shippingMinor" 0)) lines))
        tariff (reduce + 0 (map #(long (get % "tariffMinor" 0)) lines))
        internal-items (reduce + 0 (for [l lines :when (= (get l "ring") "internal")]
                                     (* (long (get l "priceMinor" 0)) (long (get l "qty" 1)))))
        tithe (quot (* internal-items TITHE-BPS) 10000)]
    (merge state {"landedMinor" (+ items shipping tariff tithe) "titheMinor" tithe})))

(defn- abaki-blocked-reason [supplier-did supplier-name]
  (try
    (let [f (io/file "20-actors/abaki/out/routing-policy.json")]
      (when (.exists f)
        (let [policy (json/parse-string (slurp f))
              blocked (map #(get % "id") (get policy "blocked_entities" []))]
          (some (fn [bid] (when (or (str/includes? (str supplier-did) bid) (str/includes? (str supplier-name) bid))
                            (str "Provider blocked by abaki Anti-Monopoly policy (React mechanism). Route Around " bid " activated.")))
                blocked))))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn handle-provision [state]
  (if-let [reason (abaki-blocked-reason (get state "supplierDid" "") (get state "supplierName" ""))]
    (merge state {"settlement" "proxy-gated" "refused" true "reason" reason})
    (let [ring (get state "ring")]
      (cond
        (= ring "commons") (merge state {"settlement" "commons-none" "titheMinor" 0})
        (= ring "internal") (merge state {"settlement" "usdc-warifu" "titheMinor" (get state "titheMinor" 0)})
        (= ring "external")
        (if (get state "requestProxy")
          (if-not (get state "gateRef")
            (merge state {"settlement" "proxy-gated" "refused" true
                          "reason" "external 代理-purchase requires Council Lv7+ amendment OR vendor arm + operator (G2/G11)"})
            (merge state {"settlement" "proxy-gated" "gateRef" (get state "gateRef")}))
          (merge state {"settlement" "self-checkout-handoff" "titheMinor" 0}))
        :else (merge state {"settlement" "commons-none"})))))

(defn handle-lifecycle [state]
  (merge state {"stage" (get state "stage" "in-use") "routeActor" (get state "lifecycleRoute" "hodoki")}))

;; ── R1 — Ring 1 internal economy ──────────────────────────────────────────────
(defn check-sbt-eligibility [buyer-did maker-actor sbt-registry]
  (cond
    (not (contains? MAKER-ACTORS maker-actor)) {"eligible" false "reason" (str maker-actor " is not a Ring 1 producing actor")}
    (not (get sbt-registry buyer-did false)) {"eligible" false "reason" "buyer is not an active Adherent SBT holder (§3/G2)"}
    (not (get sbt-registry (str "did:web:etzhayyim.com:" maker-actor) false)) {"eligible" false "reason" (str "maker " maker-actor " SBT not active (§3/G2)")}
    :else {"eligible" true "reason" "both parties active Adherent SBT (SBT↔SBT carve-out)"}))

(defn build-settlement-intent
  ([gross-minor maker-actor] (build-settlement-intent gross-minor maker-actor nil))
  ([gross-minor maker-actor operator-ref]
   (let [gross (long gross-minor) tithe (quot (* gross TITHE-BPS) 10000) payout (- gross tithe)]
     {"rail" "usdc-base-l2" "grossMinor" gross "titheMinor" tithe "makerPayoutMinor" payout
      "titheRouter" "50-infra/etzhayyim-tithe-router" "makerActor" maker-actor
      "state" (if operator-ref "executed" "intent") "operatorRef" operator-ref})))

(defn build-user-op [intent member-did]
  {"rail" "erc4337-user-op" "sender" member-did "grossMinor" (get intent "grossMinor")
   "titheMinor" (get intent "titheMinor") "makerPayoutMinor" (get intent "makerPayoutMinor")
   "titheRouter" (get intent "titheRouter") "requiredSigner" "member-smart-account"
   "serverHeldKey" false "signed" false})

(defn submit-settlement
  ([intent member-signature] (submit-settlement intent member-signature nil))
  ([intent member-signature operator-ref]
   (cond
     (not (contains? #{"intent" nil} (get intent "state")))
     (merge intent {"refused" true "reason" (str "settlement not in :intent state (" (get intent "state") ")")})
     (not= (get member-signature "origin") "member")
     (merge intent {"refused" true "reason" "only a member smart-account signature can authorize (G15 no-server-key)"})
     :else
     (let [user-op (assoc (build-user-op intent (get member-signature "memberDid" ""))
                          "signed" true "signatureRef" (get member-signature "ref"))]
       (if-not operator-ref
         (merge intent {"state" "authorized-pending-operator" "userOp" user-op})
         (merge intent {"state" "submitted" "userOp" user-op "operatorRef" operator-ref}))))))

(defn assign-fulfillment [item-class] (get FULFILLMENT item-class "wadachi"))

(defn place-order [buyer-did maker-actor gross-minor item-class sbt-registry]
  (let [elig (check-sbt-eligibility buyer-did maker-actor sbt-registry)]
    (if-not (get elig "eligible")
      {"state" "refused" "reason" (get elig "reason") "ring" "internal"}
      {"state" "settle-intent" "ring" "internal" "buyerDid" buyer-did "makerActor" maker-actor
       "settlement" (build-settlement-intent gross-minor maker-actor)
       "fulfillmentActor" (assign-fulfillment item-class)})))

(defn advance-order [order]
  (let [st (get order "state")]
    (if-not (contains? (set ORDER-STATES) st)
      order
      (let [i (.indexOf ORDER-STATES st)]
        (merge order {"state" (nth ORDER-STATES (min (inc i) (dec (count ORDER-STATES))))})))))

;; ── R2 — Ring 2 external catalog ──────────────────────────────────────────────
(def ^:private AFFILIATE-PARAMS
  #{"aff" "affid" "aff_id" "affiliate" "affiliate_id" "partner" "partner_id" "pid" "click_id" "clickid"
    "cjevent" "irclickid" "irgwc" "ranmid" "raneaid" "ransiteid" "siteid" "subid" "sub_id"
    "tag" "ascsubtag" "linkcode" "linkid" "creativeasin" "camp" "creative" "smid" "psc"
    "scid" "sc2id" "rafcid" "icm_cid" "icm_acid"
    "gclid" "fbclid" "msclkid" "dclid" "yclid" "twclid" "ttclid"
    "mc_cid" "mc_eid" "ref" "ref_" "referrer" "_branch_match_id"})
(def ^:private AFFILIATE-PREFIXES ["utm_" "aff_" "pk_" "_hs" "spm"])
(def ^:private EXTERNAL-SOURCES #{"open-standard" "vendor-direct" "api-data-only" "scraped"})

(defn strip-affiliate
  "Remove affiliate + tracking parameters from a retailer URL (G3); functional params + order kept;
  also drops Amazon-style /ref=... path segments."
  [url]
  (let [[_ scheme netloc path query]
        (re-matches #"(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\?([^#]*))?(?:#.*)?" (or url ""))
        pairs (if (and query (not= query ""))
                (map (fn [p] (let [i (str/index-of p "=")] (if i [(subs p 0 i) (subs p (inc i))] [p ""])))
                     (str/split query #"&"))
                [])
        kept (filter (fn [[k _]]
                       (let [kl (str/lower-case k)]
                         (and (not (AFFILIATE-PARAMS kl)) (not (some #(str/starts-with? kl %) AFFILIATE-PREFIXES)))))
                     pairs)
        opath (or path "")
        p* (str/join "/" (remove #(str/starts-with? % "ref=") (str/split opath #"/" -1)))
        p* (if (and (str/ends-with? opath "/") (not (str/ends-with? p* "/"))) (str p* "/") p*)
        q (str/join "&" (map (fn [[k v]] (str k "=" v)) kept))
        base (str (when (seq scheme) (str scheme "://")) netloc p*)]
    (if (= q "") base (str base "?" q))))

(defn normalize-external [raw source]
  (when-not (contains? EXTERNAL-SOURCES source)
    (throw (ex-info (str "unknown external source " (pr-str source)) {:source source})))
  (let [retailer-url (or (get raw "url") (get raw "retailerUrl") "")]
    {"productId" (str "ext." (or (get raw "gtin") (get raw "id") "unknown"))
     "title" (get raw "title" "") "ring" "external" "source" source
     "gtin" (get raw "gtin") "unspsc" (get raw "unspsc")
     "retailerUrl" (if (seq retailer-url) (strip-affiliate retailer-url) "")
     "priceMinor" (long (get raw "priceMinor" 0)) "currency" (get raw "currency" "USD")
     "availability" (get raw "availability" "unknown")
     "durabilityYears" (double (get raw "durabilityYears" 0.0)) "repairability" (long (get raw "repairability" 0))
     "laborProvenance" (get raw "laborProvenance" "unknown") "carbonKg" (double (get raw "carbonKg" 0.0))
     "lifecycleRoute" (get raw "lifecycleRoute" "haraedo") "sourcing" "representative"}))

(defn build-external-handoff [product]
  {"ring" "external" "settlement" "self-checkout-handoff"
   "handoffUri" (strip-affiliate (get product "retailerUrl" "")) "titheMinor" 0})

(defn scrape-gate
  ([url robots-disallow rate-state] (scrape-gate url robots-disallow rate-state nil))
  ([url robots-disallow rate-state operator-ref]
   (let [[_ _ netloc path] (re-matches #"(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\?[^#]*)?(?:#.*)?" url)
         host netloc
         path (if (seq path) path "/")]
     (cond
       (some #(str/starts-with? path %) robots-disallow) {"allowed" false "verdict" "denied" "reason" (str "robots.txt disallows " path)}
       (>= (long (get rate-state host 0)) (long (get rate-state "_limit" 30))) {"allowed" false "verdict" "denied" "reason" (str "rate budget exhausted for " host)}
       (not operator-ref) {"allowed" true "verdict" "gated" "reason" "robots-ok; live fetch is operator-gated (G11)"}
       :else {"allowed" true "verdict" "fetch" "reason" "robots-ok + operator authorized"}))))

(defn landed-cost-external [price-minor shipping-minor tariff-bps]
  (let [price (long price-minor) tariff (quot (* price (long tariff-bps)) 10000)]
    {"priceMinor" price "shippingMinor" (long shipping-minor) "tariffMinor" tariff
     "landedMinor" (+ price (long shipping-minor) tariff)}))

;; ── R3 — assisted secure checkout (member-principal) ──────────────────────────
(def ^:private PAYMENT-INSTRUMENTS #{"member-external-card" "warifu"})

(defn seal-encrypted [fields recipient-did]
  (let [keysig (str/join "+" (sort (keys fields)))
        ref (str "com.etzhayyim.encrypted:" (format "%08x" (bit-and (hash keysig) 0xFFFFFFFF)))]
    {"envelopeRef" ref "recipientDid" recipient-did "sealedFields" (vec (sort (keys fields)))}))

(defn build-payment-intent
  ([member-did retailer amount-minor currency instrument]
   (build-payment-intent member-did retailer amount-minor currency instrument true))
  ([member-did retailer amount-minor currency instrument external]
   (when-not (contains? PAYMENT-INSTRUMENTS instrument)
     (throw (ex-info (str "unknown instrument " (pr-str instrument)) {:instrument instrument})))
   (cond-> {"memberDid" member-did "retailer" retailer "amountMinor" (long amount-minor) "currency" currency
            "instrument" instrument "rail" (if (= instrument "warifu") "erc4337-user-op" "member-card-direct")
            "principal" "member" "serverHeldKey" false "requiredSigner" "member-passkey-or-smart-account" "signed" false}
     (and (= instrument "warifu") external) (assoc "requiresWarifuExternalGate" true))))

(defn authorize-payment [intent signature]
  (cond
    (not= (get signature "origin") "member")
    (merge intent {"signed" false "refused" true "reason" "only a member passkey/wallet signature can authorize (G15 no-server-key)"})
    (get intent "serverHeldKey")
    (merge intent {"signed" false "refused" true "reason" "intent carries a server-held key — invariant violation (G15)"})
    :else (merge intent {"signed" true "signatureRef" (get signature "ref")})))

(defn assist-checkout
  ([member-did product profile-fields] (assist-checkout member-did product profile-fields nil nil))
  ([member-did product profile-fields member-signature operator-ref]
   (let [envelope (seal-encrypted profile-fields member-did)
         amount (long (get product "priceMinor" 0))
         instrument (get product "instrument" "member-external-card")
         intent (build-payment-intent member-did (get product "retailer" "") amount (get product "currency" "USD") instrument)
         handoff (build-external-handoff product)
         base {"ring" "external" "mode" "assisted-secure-checkout" "principal" "member"
               "encrypted" envelope "handoffUri" (get handoff "handoffUri") "paymentIntent" intent "titheMinor" 0}]
     (if (nil? member-signature)
       (merge base {"state" "awaiting-member-authorization"})
       (let [authed (authorize-payment intent member-signature)]
         (cond
           (get authed "refused") (merge base {"state" "refused" "reason" (get authed "reason")})
           (not operator-ref) (merge base {"state" "authorized-pending-operator" "paymentIntent" authed})
           :else (merge base {"state" "submitted" "paymentIntent" authed "operatorRef" operator-ref})))))))

(defn arrange-delivery [product region]
  (let [serviceable (contains? #{"jp" "shibuya"} region)]
    (if serviceable
      {"carrier" (assign-fulfillment (get product "itemClass" "road")) "mode" "etzhayyim-logistics"
       "gig" false "lifecycleRoute" (get product "lifecycleRoute" "haraedo")}
      {"carrier" "retailer-ship" "mode" "retailer-shipping" "gig" false
       "lifecycleRoute" (get product "lifecycleRoute" "haraedo")})))
