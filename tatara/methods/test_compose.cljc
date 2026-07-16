(ns tatara.methods.test-compose
  "tatara 鑪 — cross-actor composition tests (ADR-2606171800).

  The composition is the SSoT for the 'one maritime resilience picture': over each chokepoint
  keyword, 静 tatara plants · 動 watari craft · 静-infra watatsuna cable. Verifies the fused
  counts, the resilience framing (G2 — only counts representable, no target attr), and that the
  derived datoms are flagged."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tatara.methods.analyze :as az]
            [tatara.methods.compose :as compose]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def root (.getParentFile actor-dir))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))
(def watari-seed (io/file root "watari" "data" "seed-craft-graph.kotoba.edn"))
(def watatsuna-seed (io/file root "watatsuna" "data" "cable-graph.merged.kotoba.edn"))

(defn- comp* []
  (let [a (az/analyze (az/classify (az/load-edn seed)))
        ct (compose/watari-choke-transit watari-seed)
        cl (compose/watatsuna-choke-load watatsuna-seed)]
    [a (compose/choke-composition a ct cl)]))

(deftest test-composition-fuses-three-legs-per-chokepoint
  (let [[_ comp] (comp*)
        m (into {} (map (juxt :chokepoint identity) comp))]
    ;; 静 plants · 動 craft · 静-infra cable over the SAME keyword
    (is (= {:plants 12 :craft 3 :cable 2} (-> (m :malacca) (select-keys [:plants :craft :cable]))))
    (is (= {:plants 8 :craft 1 :cable 7}  (-> (m :luzon-strait) (select-keys [:plants :craft :cable]))))
    (is (= 17 (:exposure (m :malacca))))               ;; 12+3+2 coarse redundancy-priority cue
    ;; malacca leads tatara export-dependence → first in the composition order
    (is (= :malacca (:chokepoint (first comp))))))

(deftest test-every-chokepoint-present-and-counts-nonneg
  (let [[a comp] (comp*)]
    (is (= (count (:choke-plants a)) (count comp)))    ;; one composition row per chokepoint
    (doseq [c comp]
      (is (every? #(>= % 0) [(:plants c) (:craft c) (:cable c)]))
      (is (= (:exposure c) (+ (:plants c) (:craft c) (:cable c)))))))

(deftest test-resilience-framing-no-target-attr  ;; ── G2 ──
  (let [[_ comp] (comp*)
        datoms (compose/render-datoms comp)
        report (compose/render-report comp)]
    ;; only counts/keyword/derived are representable — never a 'where to cut' / target attr
    (is (every? (fn [c] (= #{:chokepoint :plants :craft :cable :exposure} (set (keys c)))) comp))
    (is (str/includes? datoms ":composition/derived true"))
    (is (str/includes? report "NEVER interdiction"))
    (is (not (re-find #"(?i)target|interdict|cut-here|destroy" datoms)))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-compose)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
