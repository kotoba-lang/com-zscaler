(ns meyasu.methods.agent
  "meyasu 目安 — unified arbitrage / supply-demand intel orchestrator. 1:1 port of py/agent.py.

  meyasu FUSES three siblings' outputs into one per-product public-good intel surface; it
  computes no price/forecast math itself:
    handle-fuse     kakaku spread/SD + mitooshi forecast → one unified arbitrage-intel card
    handle-publish  cards → aggregate-first social post + planner handoff
    handle-persist  cards → kotoba Datoms (a forecast is a BAND, never a point — G1/G2)

  Constitutional posture: G1 non-speculative (intent = buyer-transparency+supply-resilience,
  never a trade) · G2 distribution-respecting (a point-asserted/speculative consumed forecast
  is REFUSED) · G3 aggregate-first · G4 non-adjudicating (routed to a planner) · no-server-key
  (live publication operator-gated, default :draft). The optional `from kotoba import datalog,
  llm` host binding is unused by these pure functions and is the omitted leg.")

(defn- roundn [x n]
  (let [f (Math/pow 10.0 n)]
    (/ (Math/round (* (double x) f)) f)))

;; G2 — a consumed forecast's use must be non-speculative (mirrors mitooshi ALLOWED_USE).
(def RESILIENCE-USES #{":resilience" ":planning" ":nowcast" ":early-warning" ":research"})
;; G4 — where an attention-flagged card is routed (meyasu never decides itself).
(def BUYER-PLANNER "okaimono")        ; provisioning-commons handles the buyer side
(def RESILIENCE-PLANNER "danjo")      ; accountability/resilience planner
;; trajectory threshold on the supply/demand index forecast vs the present reading.
(def TRAJECTORY-DELTA 0.1)

(defn- trajectory
  "Compare the forecast supply/demand mean to the present index → a plain-language direction.
  'tightening' = scarcity rising, 'easing' = glut rising, else 'stable'."
  [now-index forecast-mean]
  (if (or (nil? now-index) (nil? forecast-mean))
    "unknown"
    (let [d (- (double forecast-mean) (double now-index))]
      (cond (> d TRAJECTORY-DELTA) "tightening"
            (< d (- TRAJECTORY-DELTA)) "easing"
            :else "stable"))))

(defn fuse-one
  "Fuse one product's kakaku + mitooshi records into a unified arbitrage-intel card. Throws an
  ex-info (gate G2) if the forecast is a point assertion or a speculative use."
  [item]
  (let [k (get item "kakaku" {})
        f (get item "mitooshi" {})]
    (when (seq f)
      (when (get f "pointAsserted")
        (throw (ex-info "G2: consumed forecast is point-asserted (distribution-only)" {:gate "G2"})))
      (when (and (get f "use") (not (RESILIENCE-USES (get f "use"))))
        (throw (ex-info (str "G2: forecast use " (pr-str (get f "use")) " is not in the resilience set")
                        {:gate "G2"}))))
    (let [now-index (get k "supplyDemandIndex")
          mean (get f "mean")
          sd (get f "sd")
          traj (trajectory now-index mean)
          notable (boolean (get k "notable"))
          attention (and notable (= traj "tightening"))]
      {"productId" (get item "productId")
       "priceSpread" (get k "spread")
       "spreadFraction" (get k "spreadFraction")
       "notableSpread" notable
       "cheapestMerchant" (get k "cheapestMerchant")
       "supplyDemandNow" now-index
       "reading" (get k "reading")
       "forecastBand" (when (and (some? mean) (some? sd))
                        [(roundn (- mean sd) 4) (roundn (+ mean sd) 4)])
       "trajectory" traj
       "attention" attention
       "routeTo" (if attention RESILIENCE-PLANNER BUYER-PLANNER)   ; G4
       "intent" "buyer-transparency+supply-resilience"})))         ; G1

(defn handle-fuse
  "Fuse a batch of per-product {kakaku, mitooshi} records into unified cards; a forecast that
  violates G2 is refused per-item with a reason (never silently dropped)."
  [state]
  (let [acc (reduce (fn [a item]
                      (try
                        (update a :cards conj (fuse-one item))
                        (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
                          (update a :refused conj {"productId" (get item "productId")
                                                   "reason" (ex-message e)}))))
                    {:cards [] :refused []} (get state "items" []))]
    (merge state {"cards" (:cards acc) "refused" (:refused acc)})))

(defn compose-card-post
  "Compose ONE aggregate-first post from a unified card. Buyer transparency + resilience
  framing; no urgency, no affiliate, no purchase nudge, no trade call (G1/G3)."
  [card]
  (let [frac-pct (roundn (* (double (get card "spreadFraction" 0.0)) 100) 1)
        text (str "目安: " (get card "productId" "product") " の現在の最安価格差は約 " frac-pct "%、"
                  "供給/需要は " (get card "reading" "balanced") "、見通しは " (get card "trajectory" "unknown") "。"
                  " 公共的な価格・供給の透明化であり、売買の勧誘ではありません。")]
    {"text" text
     "shape" "aggregate"                ; G3
     "lexicon" "app.bsky.feed.post"
     "nudge" false                      ; G1 — no purchase/trade nudge
     "affiliate" false
     "routeTo" (get card "routeTo")}))

(defn handle-publish
  "Compose aggregate posts from fused cards and (optionally) publish. Attention cards are handed
  off to their planner (G4); publication is operator-gated (no-server-key): without operatorRef
  posts are :draft. Aggregate-share is 100% (never targets an individual)."
  [state]
  (let [operator-ref (get state "operatorRef")
        cards (get state "cards" [])
        posts (mapv (fn [c] (assoc (compose-card-post c) "state" (if operator-ref "posted" "draft"))) cards)
        handoffs (vec (keep (fn [c] (when (get c "attention")
                                      {"productId" (get c "productId") "routeTo" (get c "routeTo")
                                       "reason" "notable spread + tightening forecast → resilience review"}))
                            cards))]
    (merge state {"posts" posts "handoffs" handoffs "broadcast" (boolean operator-ref)
                  "aggregateSharePct" (if (seq posts) 100 0)})))

(defn card-to-datoms
  "Flatten a fused card into kotoba Datoms ([eid attr value]) over the meyasu schema. A forecast
  is written as a BAND (forecast-band-lo/hi), NEVER a point (G1/G2). Pure."
  [card observed-at]
  (let [pid (get card "productId" "unknown")
        eid (str "meyasu.card." pid "." observed-at)
        band (get card "forecastBand")
        base [[eid ":meyasu.card/id" eid]
              [eid ":meyasu.card/product" pid]
              [eid ":meyasu.card/price-spread" (long (or (get card "priceSpread") 0))]
              [eid ":meyasu.card/spread-fraction" (double (or (get card "spreadFraction") 0.0))]
              [eid ":meyasu.card/notable-spread" (boolean (get card "notableSpread"))]
              [eid ":meyasu.card/supply-demand-now" (double (or (get card "supplyDemandNow") 0.0))]
              [eid ":meyasu.card/reading" (str ":" (get card "reading" "balanced"))]
              [eid ":meyasu.card/trajectory" (str ":" (get card "trajectory" "unknown"))]
              [eid ":meyasu.card/attention" (boolean (get card "attention"))]
              [eid ":meyasu.card/route-to" (get card "routeTo" BUYER-PLANNER)]
              [eid ":meyasu.card/intent" (get card "intent" "buyer-transparency+supply-resilience")]
              [eid ":meyasu.card/observed-at" observed-at]]]
    (if band
      (conj base
            [eid ":meyasu.card/forecast-band-lo" (double (nth band 0))]
            [eid ":meyasu.card/forecast-band-hi" (double (nth band 1))])
      base)))

(defn handle-persist
  "Build the kotoba Datom transaction for the fused cards. no-server-key: the tx is RETURNED,
  not written, unless an operatorRef is present (G6/G11 outward-gated). The G1 invariant holds
  in the datoms — a forecast is a band, never a point assertion."
  [state]
  (let [observed-at (get state "observedAt" "1970-01-01T00:00:00Z")
        datoms (vec (mapcat #(card-to-datoms % observed-at) (get state "cards" [])))
        operator-ref (get state "operatorRef")]
    (merge state {"datoms" datoms
                  "datomCount" (count datoms)
                  "writeState" (if operator-ref "committed" "tx-only")   ; no-server-key
                  "operatorRef" operator-ref})))
