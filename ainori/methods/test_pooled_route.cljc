(ns ainori.methods.test-pooled-route
  "test_pooled_route — pins ainori's pooled sequencing to the REUSED todoke route core.

  1:1 Clojure port of methods/test_pooled_route.py (stdlib unittest → clojure.test).

  The headline test (`test-parity-with-todoke`) is the proof of ADR-2606071500's reuse claim:
  on a shared fixture, ainori's `sequence-stops` returns the SAME visiting order as todoke's
  `plan-last-mile`. If anyone forks a second routing engine into ainori, this test breaks.

  Two Python tests are object-IDENTITY assertions with no Clojure analogue:
    - `test_reuses_todoke_primitives` (`pr.Stop IS todoke.Stop`) — there is no Stop class in the
      Clojure port; reuse is structural (this ns literally requires todoke.methods.last-mile) and
      is what the parity test proves. Ported as `test-reuses-todoke-engine`.
    - `test_uses_real_agent_cost_share` (`pr.cost_share IS agent.cost_share`) — ainori's agent has
      no Clojure port; `cost-share` is inlined verbatim from py/agent.py. Ported as a value-behavior
      check against that inlined fn.

  Run: bb -e \"(require 'clojure.test 'ainori.methods.test-pooled-route)
                (clojure.test/run-tests 'ainori.methods.test-pooled-route)\""
  (:require [clojure.test :refer [deftest is run-tests]]
            [ainori.methods.pooled-route :as pr]
            [todoke.methods.last-mile :as todoke]))

(defn- fixture []
  ;; pedestrian-zone fixture so todoke.plan-last-mile's envelope accepts it
  [{:id 0 :x 0.0 :y 0.0 :zone "sidewalk"}
   {:id 1 :x 3.0 :y 0.0 :zone "sidewalk"}
   {:id 2 :x 3.0 :y 3.0 :zone "sidewalk"}
   {:id 3 :x 0.0 :y 3.0 :zone "sidewalk"}
   {:id 4 :x 1.0 :y 1.0 :zone "sidewalk"}])

;; ── Parity ───────────────────────────────────────────────────────────────────
(deftest test-parity-with-todoke
  (let [stops (fixture)
        [order-a len-a] (pr/sequence-stops stops)
        [order-t len-t] (todoke/plan-last-mile stops :sae-level 4 :commanded-mps 1.5)]
    (is (= order-a order-t))                       ; SAME engine, not a fork
    (is (< (Math/abs (- (double len-a) (double len-t))) 1e-9))))

(deftest test-reuses-todoke-engine
  ;; sequence-stops delegates to the actual todoke engine (identity of result order/length).
  (let [stops (fixture)
        [order-a len-a] (pr/sequence-stops stops)
        [order-t len-t] (todoke/plan-last-mile stops)]
    (is (= order-a order-t))
    (is (< (Math/abs (- (double len-a) (double len-t))) 1e-9))))

;; ── PooledRoute ────────────────────────────────────────────────────────────────
(deftest test-origin-pinned-first
  (let [out (pr/pooled-route [0.0 0.0]
                             [{"id" 1 "x" 5.0 "y" 0.0} {"id" 2 "x" 1.0 "y" 0.0}])]
    (is (= 0 (first (get out "order"))))           ; carrier origin pinned
    (is (= 2 (get out "occupancy")))))

(deftest test-vehicular-zone-sequences
  ;; ainori uses road/arterial zones — sequencing works WITHOUT todoke's pedestrian envelope
  (let [out (pr/pooled-route [0.0 0.0]
                             [{"id" 1 "x" 10.0 "y" 0.0 "zone" "expressway"}
                              {"id" 2 "x" 2.0 "y" 0.0 "zone" "arterial"}])]
    (is (= [0 2 1] (get out "order")))             ; nearest-first sequencing
    (is (> (get out "lengthM") 0))))

(deftest test-empty
  (is (= [[] 0.0] (pr/sequence-stops []))))

;; ── PlanPooledTrip ───────────────────────────────────────────────────────────────
(deftest test-composes-route-and-cost-share
  (let [out (pr/plan-pooled-trip [0.0 0.0]
                                 [{"id" 1 "x" 5.0 "y" 0.0} {"id" 2 "x" 1.0 "y" 0.0}]
                                 1200000)]
    (is (= 0 (first (get out "order"))))           ; routing (todoke core)
    (is (= 2 (get out "occupancy")))
    (is (= 600000 (get out "costSharePerRiderMinor")))))  ; cost-share split (no surge)

(deftest test-no-profit-invariant
  ;; odd cost: per-rider rounds down; total collected ≤ real fuel/wear (carrier absorbs rest)
  (let [out (pr/plan-pooled-trip [0.0 0.0]
                                 [{"id" 1 "x" 5.0 "y" 0.0} {"id" 2 "x" 1.0 "y" 0.0}
                                  {"id" 3 "x" 3.0 "y" 0.0}]
                                 1000000)]
    (is (<= (get out "totalCollectedMinor") (get out "fuelWearMinor")))))

(deftest test-pooling-lowers-each-share
  (let [two (pr/plan-pooled-trip [0.0 0.0]
                                 [{"id" 1 "x" 1.0 "y" 0.0} {"id" 2 "x" 2.0 "y" 0.0}]
                                 1200000)
        three (pr/plan-pooled-trip [0.0 0.0]
                                   [{"id" 1 "x" 1.0 "y" 0.0} {"id" 2 "x" 2.0 "y" 0.0}
                                    {"id" 3 "x" 3.0 "y" 0.0}]
                                   1200000)]
    (is (< (get three "costSharePerRiderMinor") (get two "costSharePerRiderMinor")))))

(deftest test-uses-real-agent-cost-share
  ;; composition, not duplication: pr/cost-share is the inlined ainori agent no-surge split.
  (is (= 600000 (pr/cost-share 1200000 2)))
  (is (= 333333 (pr/cost-share 1000000 3))))       ; floor-division (G1)

#?(:clj (defn -main [& _] (run-tests 'ainori.methods.test-pooled-route)))
