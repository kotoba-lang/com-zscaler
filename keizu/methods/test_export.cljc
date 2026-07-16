(ns keizu.methods.test-export
  "test_export.cljc — 系図 (keizu) → kanae render payload + round-trip. ADR-2606066000.
  1:1 Clojure port of `methods/test_export.py` (clojure.test). Every Python assertion ported,
  EXCEPT test_round_trip_through_bridge_preserves_kind — bridge.cljc is not yet ported, so that
  bridge-dependent test is DEFERRED (see report). Seed I/O is at the #?(:clj) edge."
  (:require [clojure.test :refer [deftest is run-tests]]
            [keizu.methods.weave :as w]
            [keizu.methods.export :as x]
            #?(:clj [keizu.methods.edn :as e])))

(def seed-path "20-actors/keizu/data/seed-relation-graph.kotoba.edn")

#?(:clj (defn- g [] (w/weave (e/load-edn seed-path))))

;; ── test_fiscal_money_maps_to_kanae_flow ──────────────────────────────────────────────────────
#?(:clj
   (deftest test-fiscal-money-maps-to-kanae-flow
     (let [f (x/to-kanae-flow {":money/id" "m1" ":money/kind" ":procurement-award"
                               ":money/payer" "a" ":money/payee" "b" ":money/amount" 100.0
                               ":money/currency" "JPY" ":money/sources" ["u" "v"]})]
       (is (= "procurement" (get f "flowType")))
       (is (= "a" (get f "donor")))
       (is (= "b" (get f "recipient"))))))

;; ── test_political_donation_not_a_kanae_flow ──────────────────────────────────────────────────
#?(:clj
   (deftest test-political-donation-not-a-kanae-flow
     (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not a kanae fiscal flow"
                           (x/to-kanae-flow {":money/id" "m" ":money/kind" ":political-donation"})))))

;; ── test_to_kanae_flows_skips_donations ───────────────────────────────────────────────────────
;; the seed has 1 political-donation (m-donation-jp-1) → skipped, the rest exported
#?(:clj
   (deftest test-to-kanae-flows-skips-donations
     (let [kf (x/to-kanae-flows (g))]
       (is (= 1 (get kf "skipped_count")))
       (is (= (dec (count (get (g) "money"))) (count (get kf "flows"))))
       (is (every? #(contains? (set (vals x/KEIZU-KIND-TO-KANAE)) (get % "flowType"))
                   (get kf "flows"))))))

;; ── test_render_payload_is_json_serializable ──────────────────────────────────────────────────
;; the render-json string round-trips structurally (no sets/tuples); checks the loaded fields.
#?(:clj
   (deftest test-render-payload-is-json-serializable
     (let [c (w/concentration (g))
           s (x/render-json c)
           obj (cheshire.core/parse-string s false)]
       (is (= "keizu" (get obj "actor")))
       (is (true? (get obj "isMirror")))
       (is (true? (get obj "nonAdjudicating")))
       (is (>= (get-in obj ["counts" "node_count"]) 15)))))

;; ── test_render_payload_empty_graph_safe ──────────────────────────────────────────────────────
#?(:clj
   (deftest test-render-payload-empty-graph-safe
     (let [s (x/render-json (w/concentration (w/weave {})))
           obj (cheshire.core/parse-string s false)]
       (is (= 0 (get-in obj ["counts" "money_count"])))
       (is (= [] (get obj "money_by_payee")))
       (is (= 0 (get-in obj ["statement_index" "count"]))))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-export)))
