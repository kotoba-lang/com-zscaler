(ns shionome.methods.test-export
  "Cross-language oracle tests for shionome.methods.export — the Clojure port of
  methods/export.py.

  Ported 1:1 from the REAL Python test_export.py (the cross-language oracle):
  the seed-driven assertions (8 capital flows kept, 3 observation-only skipped,
  regime 'risk-on') exercise the committed seed-capital-flow-graph.kotoba.edn
  through the already-ported weave/concentration, so any divergence in the export
  shaping fails here."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shionome.methods.export :as export]
            [shionome.methods.weave :as weave]
            [shionome.methods.edn :as edn]
            #?(:clj [cheshire.core :as json])))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")

(defn- g [] (weave/weave (edn/load-edn seed-path)))

(deftest to-kanae-flow-ok
  (testing "a capital-movement :flow maps to a kanae fundFlowEdge with noTrade"
    (let [e (export/to-kanae-flow {":flow/id" "f" ":flow/source" "a" ":flow/target" "b"
                                   ":flow/kind" ":rotation" ":flow/magnitude" 5.0
                                   ":flow/unit" "usd-bn" ":flow/sources" ["x" "y"]})]
      (is (= "rotation" (get e "flowType")))
      (is (= true (get e "noTrade"))))))

(deftest to-kanae-flow-rejects-observation-kind
  (testing "an observation-only kind raises (not a capital amount)"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                          #"observation"
                          (export/to-kanae-flow {":flow/kind" ":cross-correlation"})))))

(deftest to-kanae-flows-skips-observations
  (testing "seed: 8 capital flows kept, 3 observation-only skipped (2 correlation + 1 price-move)"
    (let [kf (export/to-kanae-flows (g))]
      (is (= 3 (get kf "skipped_count")))
      (is (= 8 (count (get kf "flows")))))))

(deftest render-payload-flags
  (testing "the render payload carries the mirror/no-trade flags + actor id"
    (let [p (export/render-payload (weave/concentration (g)))]
      (is (= true (get p "isMirror")))
      (is (= true (get p "noTrade")))
      (is (= "shionome" (get p "actor"))))))

#?(:clj
   (deftest render-json-serializable
     (testing "render-json round-trips: regime risk-on, inflow_shares is a list"
       (let [s   (export/render-json (weave/concentration (g)))
             obj (json/parse-string s)]
         (is (= "risk-on" (get-in obj ["regime" "regime"])))
         (is (vector? (get obj "inflow_shares")))))))

#?(:clj
   (deftest render-payload-has-no-per-bucket-score
     (testing "G4 — the render payload never exposes a per-bucket rating/signal/score"
       (let [s (str/lower-case (export/render-json (weave/concentration (g))))]
         (doseq [forbidden ["\"rating\"" "\"signal\"" "\"target_price\"" "\"recommendation\""]]
           (is (not (clojure.string/includes? s forbidden))))))))
