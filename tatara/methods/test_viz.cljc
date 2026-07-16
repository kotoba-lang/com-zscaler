(ns tatara.methods.test-viz
  "tatara 鑪 — viz data-builder tests (ADR-2606171800).

  Covers the PURE viz-data fns in build_viz (the generator's geometry/JS is validated out-of-band
  by node --check): chokepoint bars + the static↔dynamic composition (tatara plant
  export-dependence + watari live craft transit) + chokepoint geographic markers."
  (:require [clojure.test :refer [deftest is run-tests]]
            [tatara.viz.build-viz]))

;; the pure data fns are ns-private — reach them via their vars
(def choke-bars       @#'tatara.viz.build-viz/choke-bars)
(def composition-bars @#'tatara.viz.build-viz/composition-bars)
(def choke-points     @#'tatara.viz.build-viz/choke-points)

(def a-fixture
  {:choke-plants {:malacca #{"p1" "p2" "p3"} :hormuz #{"p4"}}
   :choke-coords {:malacca {:lat 2.5 :lon 101.0 :name "Strait of Malacca" :id "chokepoint.malacca"}
                  :hormuz  {:lat 26.6 :lon 56.4 :name "Strait of Hormuz" :id "chokepoint.hormuz"}}})

(deftest test-choke-bars-ordered-by-export-dependence
  (let [bars (choke-bars a-fixture)]
    (is (= ["malacca" "hormuz"] (map :name bars)))    ;; descending by plant count
    (is (= [3 1] (map :value bars)))
    (is (every? :col bars))))

(deftest test-composition-bars-overlay-craft-and-cable
  (let [transit {"malacca" #{"c1" "c2"} "hormuz" #{}}
        cable {"malacca" #{"st1" "st2" "st3"}}
        bars (composition-bars a-fixture transit cable)]
    ;; 静 plants (:value) · 動 craft (:sub) · 静-infra cable stations (:sub2) over the SAME keyword
    (is (= {:name "malacca" :value 3 :sub 2 :sub2 3}
           (-> bars first (select-keys [:name :value :sub :sub2]))))
    (is (= 0 (:sub (second bars))))                   ;; hormuz: no live craft this wave
    (is (= 0 (:sub2 (second bars))))                  ;; hormuz: no cable station this seed
    ;; a chokepoint absent from the transit/cable maps still renders (0), never throws
    (is (= 2 (count bars)))))

(deftest test-choke-points-are-geographic-markers
  (let [pts (choke-points a-fixture)
        m (into {} (map (juxt :cc identity) pts))]
    (is (= 2 (count pts)))
    (is (every? #(= "chokepoint" (:kind %)) pts))
    (is (= 101.0 (:lon (get m "malacca"))))
    (is (= 9 (:w (get m "malacca"))))                 ;; w = n² (3²) so the marker scales by load
    (is (= 1 (:w (get m "hormuz"))))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-viz)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
