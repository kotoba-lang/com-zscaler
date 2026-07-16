(ns meyasu.methods.test-cohort-e2e
  "Cohort end-to-end: kakaku → mitooshi → meyasu (the whole price-intel pipeline). 1:1 port of
  py/test_cohort_e2e.py. The Python capstone loads each actor's agent.py under a unique module
  name via importlib; the cljc twin instead requires the ported namespaces directly (no importlib):

    kakaku   offers → handle-arbitrage (spread) + handle-supply-demand (index now)
    mitooshi bridge-kakaku (sd → series) → forecast-next (distribution, :resilience)
    meyasu   handle-fuse ({kakaku, mitooshi} → unified card) → handle-publish (aggregate post)

  Proves the cohort composes across actor boundaries with every gate held — and that the cohort's
  coverage is real cljc code, not per-actor claims."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [kakaku.methods.agent :as kakaku]
            [meyasu.methods.agent :as meyasu]
            [mitooshi.methods.bridge-kakaku :as bk]
            [mitooshi.methods.forecast :as fc]))

(def PID "jan_4901777300443")
(def SID "s-jan-4901777300443-supply-demand")
(defn- round4 [x] (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn- kakaku-card
  "kakaku leg: cross-merchant offers → spread + present supply/demand index."
  []
  (let [offers [{"merchantId" "a_com" "price" 3000 "shippingFee" 200 "availability" "out-of-stock" "region" "jp"}
                {"merchantId" "b_com" "price" 2700 "shippingFee" 1200 "availability" "backorder" "region" "us"}
                {"merchantId" "c_com" "price" 3500 "shippingFee" 0 "availability" "out-of-stock" "region" "jp"}]
        ;; a rising price history → demand pressure (drives supply/demand index up)
        history (mapv (fn [t] {"observedAt" (str "2026-06-0" t) "totalPrice" (+ 3000 (* 60 t))}) (range 1 8))
        arb (kakaku/handle-arbitrage {"offers" offers})
        sd (kakaku/handle-supply-demand {"offers" offers "priceHistory" history})]
    {"spread" (get arb "spread") "spreadFraction" (get arb "spreadFraction") "notable" (get arb "notable")
     "cheapestMerchant" (get arb "cheapestMerchant")
     "supplyDemandIndex" (get sd "supplyDemandIndex") "reading" (get sd "reading")}))

(defn- mitooshi-forecast
  "mitooshi leg: bridge a rising supply-demand series → forecast a distribution."
  [now-index]
  (let [acc (reduce (fn [a t]
                      (let [idx (round4 (+ now-index (* 0.12 (- t 7))))   ; ends at now-index at t=7, rising
                            b (bk/bridge-kakaku [{":sd/product" PID ":sd/index" idx}] t)]
                        (-> a (update :series merge (get b "series")) (update :obs into (get b "obs")))))
                    {:series {} :obs []} (range 1 8))
        rows (into (vec (vals (:series acc))) (:obs acc))
        hist (get (fc/series-histories rows) SID)
        f (fc/forecast-next SID hist 8)]
    {"mean" (:mean f) "sd" (:sd f) "target" 8 "use" (:use f) "pointAsserted" (:point-asserted f)}))

(deftest test-full-cohort-composes-into-one-card
  (let [k (kakaku-card)
        f (mitooshi-forecast (get k "supplyDemandIndex"))
        fused (meyasu/handle-fuse {"items" [{"productId" PID "kakaku" k "mitooshi" f}]})
        c (first (get fused "cards"))]
    (is (= 1 (count (get fused "cards"))))
    ;; the card carries data from ALL THREE actors
    (is (= (get k "spread") (get c "priceSpread")))                          ; kakaku
    (is (= (get k "supplyDemandIndex") (get c "supplyDemandNow")))           ; kakaku
    (is (some? (get c "forecastBand")))                                      ; mitooshi
    (is (= "buyer-transparency+supply-resilience" (get c "intent")))))       ; meyasu G1

(deftest test-cohort-publish-is-aggregate-draft
  (let [k (kakaku-card)
        f (mitooshi-forecast (get k "supplyDemandIndex"))
        cards (get (meyasu/handle-fuse {"items" [{"productId" PID "kakaku" k "mitooshi" f}]}) "cards")
        out (meyasu/handle-publish {"cards" cards})]
    (is (= "draft" (get (first (get out "posts")) "state")))      ; operator-gated (no-server-key)
    (is (= "aggregate" (get (first (get out "posts")) "shape")))  ; G3
    (is (= 100 (get out "aggregateSharePct")))))

(deftest test-cohort-forecast-is-distribution-not-point-g2
  (let [f (mitooshi-forecast 0.1)]
    (is (= false (get f "pointAsserted")))                         ; mitooshi G1
    (is (= ":resilience" (get f "use")))                           ; mitooshi G2
    ;; and meyasu would refuse it if it were a point assertion
    (let [bad (meyasu/handle-fuse {"items" [{"productId" PID "kakaku" (kakaku-card)
                                             "mitooshi" (assoc f "pointAsserted" true)}]})]
      (is (and (= [] (get bad "cards"))
               (str/includes? (get (first (get bad "refused")) "reason") "G2"))))))
