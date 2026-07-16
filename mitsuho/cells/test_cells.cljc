(ns mitsuho.cells.test-cells
  "mitsuho — R0 cell scaffold conformance (clj port of cells/<name>/cell.py).

  Every cell is an R0 scaffold: `solve` MUST raise (no live actuation) until the
  per-cell Council activation gate is met (ADR-2605261015 / 2605252615). This pins
  the no-live-actuation contract the python scaffolds carried, in cljc."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [mitsuho.cells.field-cultivation.cell :as field-cultivation]
            [mitsuho.cells.alt-protein-fermentation.cell :as alt-protein-fermentation]
            [mitsuho.cells.aquaculture.cell :as aquaculture]
            [mitsuho.cells.autonomous-mobile.cell :as autonomous-mobile]
            [mitsuho.cells.food-preservation.cell :as food-preservation]
            [mitsuho.cells.harvest-robotics.cell :as harvest-robotics]))

(def ^:private cells
  [["field_cultivation"        field-cultivation/solve        :field-cultivation]
   ["alt_protein_fermentation" alt-protein-fermentation/solve :alt-protein-fermentation]
   ["aquaculture"              aquaculture/solve              :aquaculture]
   ["autonomous_mobile"        autonomous-mobile/solve        :autonomous-mobile]
   ["food_preservation"        food-preservation/solve        :food-preservation]
   ["harvest_robotics"         harvest-robotics/solve         :harvest-robotics]])

(deftest all-cells-r0-scaffold-raise
  (testing "every R0 cell scaffold raises on solve (no live actuation)"
    (doseq [[label solve-fn cell-kw] cells]
      (testing label
        (let [ex (is (thrown? clojure.lang.ExceptionInfo (solve-fn {})))]
          (when (instance? clojure.lang.ExceptionInfo ex)
            (is (= :r0-scaffold (:status (ex-data ex))))
            (is (= cell-kw (:cell (ex-data ex))))
            (is (re-find #"not activated" (.getMessage ^Throwable ex)))))))))

(defn -main [& _]
  (let [{:keys [fail error]} (run-tests 'mitsuho.cells.test-cells)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
