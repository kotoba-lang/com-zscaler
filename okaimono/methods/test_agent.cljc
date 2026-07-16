(ns okaimono.methods.test-agent
  "okaimono 御買物 — agent logic tests. 1:1 port of py/test_agent.py. Verifies the invariants that
  distinguish okaimono from an Amazon clone: commons-first ring ordering (G4/G12), Wellbecoming
  ranking beats price (G3/G4), 10% tithe internal-only (G7), external 代理 refused without gate
  (G2/G11), Ring-2 handoff no-affiliate (G3), R1 SBT economy, R2 catalog, R3 member-principal checkout."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [okaimono.methods.agent :as agent]))

(deftest test-commons-first-ordering
  (let [st (agent/handle-discover {"need_text" "warm bedding" "candidates" []})]
    (is (contains? #{"commons" "internal" "external" "unresolved"} (get st "resolved_ring")))))

(deftest test-wellbecoming-beats-price
  (let [durable {"priceMinor" 18000000 "durabilityYears" 5.0 "repairability" 8 "laborProvenance" "etzhayyim-dignity" "carbonKg" 3.2}
        cheap {"priceMinor" 1290000 "durabilityYears" 1.0 "repairability" 1 "laborProvenance" "unknown" "carbonKg" 14.0}
        out (agent/handle-compare {"products" [cheap durable]})]
    (is (= durable (first (get out "ranked"))))))

(deftest test-tithe-internal-only
  (let [lines [{"priceMinor" 10000000 "qty" 1 "ring" "internal"} {"priceMinor" 5000000 "qty" 1 "ring" "external"}]
        out (agent/handle-basket {"lines" lines})]
    (is (= 1000000 (get out "titheMinor")))
    (is (= (+ 10000000 5000000 1000000) (get out "landedMinor")))))

(deftest test-external-proxy-refused-without-gate
  (let [out (agent/handle-provision {"ring" "external" "requestProxy" true})]
    (is (= "proxy-gated" (get out "settlement")))
    (is (= true (get out "refused")))))

(deftest test-external-proxy-allowed-with-gate
  (let [out (agent/handle-provision {"ring" "external" "requestProxy" true "gateRef" "council-lv7-2026xxxx"})]
    (is (= "proxy-gated" (get out "settlement")))
    (is (not (= true (get out "refused"))))))

(deftest test-external-default-is-handoff
  (let [out (agent/handle-provision {"ring" "external"})]
    (is (= "self-checkout-handoff" (get out "settlement")))
    (is (= 0 (get out "titheMinor")))))

(deftest test-internal-settles-usdc-warifu-with-tithe
  (let [out (agent/handle-provision {"ring" "internal" "titheMinor" 2400000})]
    (is (= "usdc-warifu" (get out "settlement")))
    (is (= 2400000 (get out "titheMinor")))))

(deftest test-commons-no-settlement
  (let [out (agent/handle-provision {"ring" "commons"})]
    (is (= "commons-none" (get out "settlement")))
    (is (= 0 (get out "titheMinor")))))

(deftest test-lifecycle-no-terminal-state
  (let [out (agent/handle-lifecycle {"lifecycleRoute" "hodoki"})]
    (is (not= "consumed" (get out "stage")))
    (is (= "hodoki" (get out "routeActor")))))

;; ── R1 ──
(def BUYER "did:plc:member-001")
(def REG {BUYER true "did:web:etzhayyim.com:makura" true "did:web:etzhayyim.com:mitsuho" true})

(deftest test-sbt-eligibility-both-active (is (= true (get (agent/check-sbt-eligibility BUYER "makura" REG) "eligible"))))
(deftest test-sbt-eligibility-buyer-not-holder (is (= false (get (agent/check-sbt-eligibility "did:plc:outsider" "makura" REG) "eligible"))))
(deftest test-sbt-eligibility-non-producing-actor (is (= false (get (agent/check-sbt-eligibility BUYER "amazon" REG) "eligible"))))

(deftest test-tithe-split-is-exact
  (let [s (agent/build-settlement-intent 18000000 "makura")]
    (is (= 1800000 (get s "titheMinor")))
    (is (= 16200000 (get s "makerPayoutMinor")))
    (is (= (get s "grossMinor") (+ (get s "titheMinor") (get s "makerPayoutMinor"))))
    (is (= "intent" (get s "state")))))

(deftest test-tithe-split-remainder-absorbed-by-payout
  (let [s (agent/build-settlement-intent 9999999 "mitsuho")]
    (is (= (get s "grossMinor") (+ (get s "titheMinor") (get s "makerPayoutMinor"))))))

(deftest test-settlement-executes-only-with-operator-ref
  (is (= "executed" (get (agent/build-settlement-intent 5000000 "makura" "council-op-2026xxxx") "state"))))

(deftest test-place-order-refuses-ineligible
  (is (= "refused" (get (agent/place-order "did:plc:outsider" "makura" 18000000 "bulky" REG) "state"))))

(deftest test-place-order-eligible-reaches-settle-intent
  (let [out (agent/place-order BUYER "makura" 18000000 "bulky" REG)]
    (is (= "settle-intent" (get out "state")))
    (is (= 1800000 (get-in out ["settlement" "titheMinor"])))
    (is (= "haraedo" (get out "fulfillmentActor")))))

(deftest test-fulfillment-never-gig
  (is (= "sarutahiko" (agent/assign-fulfillment "heavy")))
  (is (= "wadachi" (agent/assign-fulfillment "road")))
  (is (= "haraedo" (agent/assign-fulfillment "bulky"))))

(deftest test-order-advance-caps-at-in-use
  (is (= "in-use" (get (agent/advance-order {"state" "in-use"}) "state")))
  (is (= "settle-intent" (get (agent/advance-order {"state" "placed"}) "state"))))

;; ── R2 ──
(deftest test-strip-affiliate-amazon
  (let [out (agent/strip-affiliate "https://www.amazon.co.jp/dp/B0XXXX/ref=as_li_ss_tl?tag=etz-22&linkCode=ll1&psc=1&th=1")]
    (is (and (not (str/includes? out "tag=")) (not (str/includes? out "linkCode=")) (not (str/includes? out "psc="))))
    (is (not (str/includes? out "/ref=")))
    (is (str/includes? out "th=1"))
    (is (str/starts-with? out "https://www.amazon.co.jp/dp/B0XXXX"))))

(deftest test-strip-affiliate-utm-and-click-ids
  (let [out (agent/strip-affiliate "https://shop.example/p/123?utm_source=x&utm_medium=aff&gclid=abc&fbclid=def&q=pillow&aff_id=99")]
    (doseq [bad ["utm_source" "utm_medium" "gclid" "fbclid" "aff_id"]] (is (not (str/includes? out bad))))
    (is (str/includes? out "q=pillow"))))

(deftest test-strip-affiliate-idempotent-and-clean-url-untouched
  (let [clean "https://shop.example/p/123?q=pillow&sku=AB12"]
    (is (= clean (agent/strip-affiliate clean)))
    (is (= clean (agent/strip-affiliate (agent/strip-affiliate clean))))))

(deftest test-normalize-external-is-data-only
  (let [raw {"gtin" "04901234567894" "title" "down comforter" "unspsc" "52121500"
             "url" "https://shop.example/p/9?tag=etz-22&utm_campaign=x" "priceMinor" 1290000 "currency" "JPY"
             "availability" "in-stock" "affiliateLink" "https://aff.example/redirect?tag=etz-22"
             "commissionBps" 300 "sponsoredRank" 1 "trackingPixel" "https://px.example/x.gif"}
        p (agent/normalize-external raw "api-data-only")]
    (is (and (= "external" (get p "ring")) (= "api-data-only" (get p "source"))))
    (is (= "representative" (get p "sourcing")))
    (is (and (not (str/includes? (get p "retailerUrl") "tag=")) (not (str/includes? (get p "retailerUrl") "utm_campaign"))))
    (doseq [f ["affiliateLink" "commissionBps" "sponsoredRank" "trackingPixel"]] (is (not (contains? p f))))))

(deftest test-normalize-external-rejects-unknown-source
  (is (thrown? clojure.lang.ExceptionInfo (agent/normalize-external {"id" "x"} "blackhat-scrape"))))

(deftest test-external-handoff-has-no-tithe-and-clean-uri
  (let [h (agent/build-external-handoff {"retailerUrl" "https://shop.example/p/9?tag=etz-22&q=z"})]
    (is (= "self-checkout-handoff" (get h "settlement")))
    (is (= 0 (get h "titheMinor")))
    (is (and (not (str/includes? (get h "handoffUri") "tag=")) (str/includes? (get h "handoffUri") "q=z")))))

(deftest test-scrape-gate-denies-robots-disallow
  (let [g (agent/scrape-gate "https://site.example/private/x" ["/private"] {})]
    (is (and (= false (get g "allowed")) (= "denied" (get g "verdict"))))))

(deftest test-scrape-gate-policy-ok-but-operator-gated
  (let [g (agent/scrape-gate "https://site.example/public/x" ["/private"] {"_limit" 30})]
    (is (and (= true (get g "allowed")) (= "gated" (get g "verdict"))))))

(deftest test-scrape-gate-fetch-with-operator
  (let [g (agent/scrape-gate "https://site.example/public/x" ["/private"] {"_limit" 30} "council-op-xxxx")]
    (is (= "fetch" (get g "verdict")))))

(deftest test-scrape-gate-rate-budget
  (let [g (agent/scrape-gate "https://site.example/p" [] {"site.example" 30 "_limit" 30})]
    (is (and (= false (get g "allowed")) (str/includes? (get g "reason") "rate budget")))))

(deftest test-landed-cost-external
  (let [lc (agent/landed-cost-external 1290000 80000 1000)]
    (is (= 129000 (get lc "tariffMinor")))
    (is (= (+ 1290000 80000 129000) (get lc "landedMinor")))))

;; ── R3 ──
(def MEMBER "did:plc:member-001")
(def SIG-MEMBER {"origin" "member" "ref" "sig:passkey:abc"})
(def SIG-SERVER {"origin" "server" "ref" "sig:platform:xyz"})

(deftest test-payment-intent-is-unsigned-member-principal-no-server-key
  (let [pi (agent/build-payment-intent MEMBER "shop.example" 4200 "USD" "member-external-card")]
    (is (= "member" (get pi "principal")))
    (is (= false (get pi "serverHeldKey")))
    (is (= false (get pi "signed")))
    (is (str/starts-with? (get pi "requiredSigner") "member"))))

(deftest test-payment-authorize-requires-member-signature
  (let [pi (agent/build-payment-intent MEMBER "shop.example" 4200 "USD" "member-external-card")
        refused (agent/authorize-payment pi SIG-SERVER)
        ok (agent/authorize-payment pi SIG-MEMBER)]
    (is (and (= true (get refused "refused")) (= false (get refused "signed"))))
    (is (= true (get ok "signed")))))

(deftest test-warifu-external-trips-its-own-gate
  (is (= true (get (agent/build-payment-intent MEMBER "shop.example" 4200 "USD" "warifu" true) "requiresWarifuExternalGate")))
  (is (not (contains? (agent/build-payment-intent MEMBER "int" 4200 "USD" "warifu" false) "requiresWarifuExternalGate"))))

(deftest test-payment-intent-rejects-unknown-instrument
  (is (thrown? clojure.lang.ExceptionInfo (agent/build-payment-intent MEMBER "shop" 1 "USD" "stolen-card"))))

(deftest test-seal-encrypted-never-leaks-plaintext
  (let [env (agent/seal-encrypted {"pan" "4111111111111111" "cvv" "123" "name" "A B"} MEMBER)
        blob (pr-str env)]
    (is (and (not (str/includes? blob "4111111111111111")) (not (str/includes? blob "A B"))))
    (is (str/starts-with? (get env "envelopeRef") "com.etzhayyim.encrypted:"))
    (is (= ["cvv" "name" "pan"] (get env "sealedFields")))))

(deftest test-assist-checkout-awaits-member-without-signature
  (let [p {"retailerUrl" "https://shop.example/p?tag=etz-22" "priceMinor" 4200 "currency" "USD"}
        out (agent/assist-checkout MEMBER p {"address" "123 Secret St, Apt 9"})]
    (is (= "awaiting-member-authorization" (get out "state")))
    (is (and (= "member" (get out "principal")) (= 0 (get out "titheMinor"))))
    (is (not (str/includes? (get out "handoffUri") "tag=")))
    (is (not (str/includes? (pr-str (get out "encrypted")) "123 Secret St")))))

(deftest test-assist-checkout-member-authorized-pending-operator
  (let [p {"retailerUrl" "https://shop.example/p" "priceMinor" 4200 "currency" "USD"}
        out (agent/assist-checkout MEMBER p {"address" "x"} SIG-MEMBER nil)]
    (is (= "authorized-pending-operator" (get out "state")))))

(deftest test-assist-checkout-submits-with-member-sig-and-operator
  (let [p {"retailerUrl" "https://shop.example/p" "priceMinor" 4200 "currency" "USD"}
        out (agent/assist-checkout MEMBER p {"address" "x"} SIG-MEMBER "council-op-1")]
    (is (and (= "submitted" (get out "state")) (= true (get-in out ["paymentIntent" "signed"]))))))

(deftest test-assist-checkout-refuses-server-signature
  (let [p {"retailerUrl" "https://shop.example/p" "priceMinor" 4200 "currency" "USD"}
        out (agent/assist-checkout MEMBER p {"address" "x"} SIG-SERVER "council-op-1")]
    (is (= "refused" (get out "state")))))

(deftest test-arrange-delivery-prefers-no-gig
  (let [d (agent/arrange-delivery {"itemClass" "bulky"} "jp")
        d2 (agent/arrange-delivery {} "us")]
    (is (and (= "etzhayyim-logistics" (get d "mode")) (= false (get d "gig")) (= "haraedo" (get d "carrier"))))
    (is (and (= "retailer-shipping" (get d2 "mode")) (= false (get d2 "gig"))))))

;; ── R1 live settlement broadcast ──
(deftest test-build-user-op-no-server-key
  (let [intent (agent/build-settlement-intent 10000000 "mitsuho")
        op (agent/build-user-op intent "did:web:etzhayyim.com:member:abc")]
    (is (= "erc4337-user-op" (get op "rail")))
    (is (= false (get op "serverHeldKey")))
    (is (= "member-smart-account" (get op "requiredSigner")))
    (is (= 1000000 (get op "titheMinor")))
    (is (= (get op "grossMinor") (+ (get op "titheMinor") (get op "makerPayoutMinor"))))))

(deftest test-submit-refuses-server-signature-g15
  (let [out (agent/submit-settlement (agent/build-settlement-intent 10000000 "mitsuho") {"origin" "server" "ref" "x"})]
    (is (= true (get out "refused")))
    (is (str/includes? (get out "reason") "no-server-key"))))

(deftest test-submit-member-signed-pending-operator-g11
  (let [out (agent/submit-settlement (agent/build-settlement-intent 10000000 "mitsuho") {"origin" "member" "ref" "sig:1" "memberDid" "did:m:1"})]
    (is (= "authorized-pending-operator" (get out "state")))
    (is (= true (get-in out ["userOp" "signed"])))))

(deftest test-submit-member-signed-with-operator-broadcasts
  (let [out (agent/submit-settlement (agent/build-settlement-intent 10000000 "mitsuho") {"origin" "member" "ref" "sig:1" "memberDid" "did:m:1"} "op:1")]
    (is (= "submitted" (get out "state")))
    (is (= "sig:1" (get-in out ["userOp" "signatureRef"])))))

(deftest test-submit-refuses-non-intent-state
  (let [out (agent/submit-settlement (agent/build-settlement-intent 10000000 "mitsuho" "op:1") {"origin" "member" "ref" "s"})]
    (is (= true (get out "refused")))))
