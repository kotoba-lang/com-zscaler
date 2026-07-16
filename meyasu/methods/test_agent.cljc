(ns meyasu.methods.test-agent
  "meyasu 目安 — unified arbitrage orchestrator tests. 1:1 port of py/test_agent.py. Verifies the
  fusion + publication invariants that keep the integration charter-clean: fuse kakaku spread/SD +
  mitooshi forecast → one card; G2 refuses a point-asserted / speculative forecast; trajectory =
  forecast mean vs present index; attention = notable spread AND tightening → resilience planner
  (G4); publish is aggregate-first (G3), no nudge/affiliate (G1), operator-gated (no-server-key)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [meyasu.methods.agent :as agent]))

(defn- item*
  [& {:keys [notable mean now point use]
      :or {notable true mean 0.5 now 0.1 point false use ":resilience"}}]
  {"productId" "jan_x"
   "kakaku" {"spread" 700 "spreadFraction" 0.22 "notable" notable
             "cheapestMerchant" "a_com" "supplyDemandIndex" now "reading" "balanced"}
   "mitooshi" {"mean" mean "sd" 0.3 "target" 7 "use" use "pointAsserted" point}})

;; ── fuse ──────────────────────────────────────────────────────────────────
(deftest test-fuse-combines-spread-and-forecast
  (let [out (agent/handle-fuse {"items" [(item*)]})
        c (first (get out "cards"))]
    (is (= 1 (count (get out "cards"))))
    (is (= 700 (get c "priceSpread")))
    (is (= [0.2 0.8] (get c "forecastBand")))            ; mean 0.5 ± sd 0.3
    (is (= "buyer-transparency+supply-resilience" (get c "intent")))))

(deftest test-trajectory-tightening-easing-stable
  (is (= "tightening" (#'agent/trajectory 0.1 0.5)))
  (is (= "easing" (#'agent/trajectory 0.5 0.1)))
  (is (= "stable" (#'agent/trajectory 0.3 0.32))))

(deftest test-attention-routes-to-resilience-planner
  (let [c (first (get (agent/handle-fuse {"items" [(item* :notable true :mean 0.6 :now 0.1)]}) "cards"))]
    (is (= true (get c "attention")))
    (is (= agent/RESILIENCE-PLANNER (get c "routeTo")))))  ; danjo

(deftest test-non-attention-routes-to-buyer-planner
  ;; notable spread but NOT tightening → buyer side
  (let [c (first (get (agent/handle-fuse {"items" [(item* :notable true :mean 0.1 :now 0.1)]}) "cards"))]
    (is (= false (get c "attention")))
    (is (= agent/BUYER-PLANNER (get c "routeTo")))))      ; okaimono

(deftest test-fuse-refuses-point-asserted-forecast-g2
  (let [out (agent/handle-fuse {"items" [(item* :point true)]})]
    (is (= [] (get out "cards")))
    (is (str/includes? (get (first (get out "refused")) "reason") "G2"))))

(deftest test-fuse-refuses-speculative-use-g2
  (let [out (agent/handle-fuse {"items" [(item* :use ":trade")]})]
    (is (= [] (get out "cards")))
    (is (str/includes? (get (first (get out "refused")) "reason") "G2"))))

(deftest test-fuse-without-forecast-is-ok
  (let [item {"productId" "p" "kakaku" {"spread" 100 "spreadFraction" 0.1
                                        "notable" false "supplyDemandIndex" 0.0 "reading" "balanced"}}
        c (first (get (agent/handle-fuse {"items" [item]}) "cards"))]
    (is (nil? (get c "forecastBand")))
    (is (= "unknown" (get c "trajectory")))))

;; ── publish ───────────────────────────────────────────────────────────────
(deftest test-publish-default-draft-aggregate-no-nudge
  (let [cards (get (agent/handle-fuse {"items" [(item*)]}) "cards")
        out (agent/handle-publish {"cards" cards})
        p (first (get out "posts"))]
    (is (= "draft" (get p "state")))                     ; operator-gated
    (is (= "aggregate" (get p "shape")))                 ; G3
    (is (and (= false (get p "nudge")) (= false (get p "affiliate"))))  ; G1
    (is (= 100 (get out "aggregateSharePct")))))

(deftest test-publish-attention-card-creates-handoff
  (let [cards (get (agent/handle-fuse {"items" [(item* :notable true :mean 0.6 :now 0.1)]}) "cards")
        out (agent/handle-publish {"cards" cards})]
    (is (= 1 (count (get out "handoffs"))))
    (is (= agent/RESILIENCE-PLANNER (get (first (get out "handoffs")) "routeTo")))))

(deftest test-publish-posts-with-operator
  (let [cards (get (agent/handle-fuse {"items" [(item*)]}) "cards")
        out (agent/handle-publish {"cards" cards "operatorRef" "op:1"})]
    (is (= "posted" (get (first (get out "posts")) "state")))
    (is (= true (get out "broadcast")))))

;; ── persist (kotoba Datoms, no-server-key) ────────────────────────────────
(deftest test-persist-emits-card-datoms
  (let [cards (get (agent/handle-fuse {"items" [(item*)]}) "cards")
        out (agent/handle-persist {"cards" cards "observedAt" "2026-06-07T00:00:00Z"})
        kinds (set (map second (get out "datoms")))]
    (is (> (get out "datomCount") 0))
    (is (contains? kinds ":meyasu.card/product"))
    (is (contains? kinds ":meyasu.card/intent"))))

(deftest test-persist-writes-forecast-as-band-not-point-g1
  (let [cards (get (agent/handle-fuse {"items" [(item* :mean 0.5 :now 0.1)]}) "cards")
        out (agent/handle-persist {"cards" cards "observedAt" "t"})
        attrs (set (map second (get out "datoms")))]
    (is (contains? attrs ":meyasu.card/forecast-band-lo"))
    (is (contains? attrs ":meyasu.card/forecast-band-hi"))
    ;; there is NO point-value attribute — a band, never a point (G1/G2)
    (is (not (some (fn [a] (or (str/includes? a "forecast-point") (= a ":meyasu.card/forecast-mean"))) attrs)))))

(deftest test-persist-no-server-key-tx-only-without-operator
  (let [cards (get (agent/handle-fuse {"items" [(item*)]}) "cards")
        out (agent/handle-persist {"cards" cards})]
    (is (= "tx-only" (get out "writeState")))))          ; no-server-key

(deftest test-persist-commits-with-operator
  (let [cards (get (agent/handle-fuse {"items" [(item*)]}) "cards")
        out (agent/handle-persist {"cards" cards "operatorRef" "op:1"})]
    (is (= "committed" (get out "writeState")))))

(deftest test-card-to-datoms-uses-observed-at-in-id
  (let [card (first (get (agent/handle-fuse {"items" [(item*)]}) "cards"))
        datoms (agent/card-to-datoms card "2026-06-07T12:00:00Z")
        eid (first (first datoms))]
    (is (and (str/includes? eid "2026-06-07T12:00:00Z") (str/starts-with? eid "meyasu.card.")))))
