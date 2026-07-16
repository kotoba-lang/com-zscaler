(ns tatara.methods.test-analyze
  "tatara 鑪 — analyzer tests (ADR-2606171800).

  Covers the aggregate roll-ups (sector concentration / HHI, chokepoint export-dependence,
  country employment rollup, capacity rollup) AND the load-bearing charter invariant: tatara
  carries DISCLOSED AGGREGATE facility figures only — an individual worker is structurally
  unrepresentable (no :worker/* / :person/* attribute anywhere). G4."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [tatara.methods.analyze :as az]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))

(defn load* []
  (let [g (az/classify (az/load-edn seed))]
    [g (az/analyze g)]))

(deftest test-classify-buckets-the-seed
  (let [[g _] (load*)]
    (is (= 33 (count (:plants g))))
    (is (= 6  (count (:hubs g))))
    (is (= 33 (count (:flows g))))))

(deftest test-sector-counts
  (let [[_ a] (load*)]
    (is (= 6 (get-in a [:sector-stats :semiconductor :plants])))
    (is (= 6 (get-in a [:sector-stats :automotive :plants])))
    (is (= 5 (get-in a [:sector-stats :battery :plants])))
    (is (= 5 (get-in a [:sector-stats :steel :plants])))
    (is (= 2 (get-in a [:sector-stats :pharma :plants])))   ;; pharma sector covered
    (is (= 9 (count (:sector-stats a))))))

(deftest test-aggregate-employment-is-sum-of-disclosed-figures
  (let [[_ a] (load*)]
    ;; disclosed aggregate facility employment, summed — NEVER per-worker
    (is (= 825000 (:global-headcount a)))))

(deftest test-semiconductor-concentration
  (let [[_ a] (load*)
        st (get-in a [:sector-stats :semiconductor])]
    (is (= 0.33 (:hhi st)))           ;; TW 2, KR 2, US 2 → 3×(1/3)² = 0.33
    (is (= "KR" (:top-country st)))   ;; 3-way tie → lexically-smallest (deterministic)
    (is (= 0.33 (:top-share st)))
    (is (false? (:single-source st))))) ;; 0.33 < 0.6 threshold — honestly NOT flagged

(deftest test-chokepoint-export-dependence
  (let [[_ a] (load*)
        n (fn [cp] (count (get-in a [:choke-plants cp])))]
    (is (= 12 (n :malacca)))
    (is (= 8  (n :luzon-strait)))
    (is (= 5  (n :suez-red-sea)))
    (is (= 5  (n :gibraltar)))
    (is (= 3  (n :panama)))
    (is (= 1  (n :hormuz)))           ;; exercised by SABIC Jubail
    (is (= 1  (n :taiwan-strait)))    ;; exercised by TSMC Hsinchu
    ;; malacca is the top export-dependence chokepoint
    (is (= :malacca (first (az/chokes-by-load a))))))

(deftest test-country-employment-rollup
  (let [[_ a] (load*)]
    ;; KR holds 5 charted plants (Samsung, SK hynix, Hyundai, POSCO, HD Hyundai)
    (is (= 5 (get-in a [:country-roll "KR" :plants])))
    (is (= 6 (get-in a [:country-roll "US" :plants])))
    (is (= 4 (get-in a [:country-roll "CN" :plants])))
    (is (= 2 (get-in a [:country-roll "IN" :plants])))
    (is (= 16 (count (:country-roll a))))))             ;; +MX/VN/ZA/ID broaden geography

(deftest test-capacity-rollup-units-are-per-sector-consistent
  (let [[_ a] (load*)]
    (is (= :wafers-300mm-kpm (get-in a [:sector-capacity :semiconductor :unit])))
    (is (= :vehicles-yr (get-in a [:sector-capacity :automotive :unit])))
    ;; automotive total = 4.03M + Nissan Aguascalientes 0.6M = 4.63M vehicles/yr
    (is (= 4630000.0 (get-in a [:sector-capacity :automotive :value])))))

(deftest test-chokepoints-are-first-class-nodes-with-coords
  (let [[g a] (load*)]
    (is (= 7 (count (:chokepoints g))))
    (is (= 7 (:n-chokepoints a)))
    (is (= 7 (count (:choke-coords a))))
    (is (= 101.0 (:lon (get (:choke-coords a) :malacca))))
    ;; every chokepoint a flow traverses has a geographic node (the shared map anchor)
    (is (every? (:choke-coords a) (keys (:choke-plants a))))))

(deftest test-logistics-modal-and-commodity-split
  (let [[_ a] (load*)]
    ;; modal split over all 33 export flows
    (is (= 25 (get-in a [:flow-modes :sea])))
    (is (= 3  (get-in a [:flow-modes :air])))
    (is (= 3  (get-in a [:flow-modes :rail])))
    (is (= 1  (get-in a [:flow-modes :inland-water])))
    (is (= 1  (get-in a [:flow-modes :road])))           ;; Nissan Aguascalientes → LA (road)
    (is (= 33 (reduce + (vals (:flow-modes a)))))
    ;; commodity split
    (is (= 9 (get-in a [:flow-commodities :components])))
    (is (= 9 (get-in a [:flow-commodities :finished-goods])))
    (is (= 6 (get-in a [:flow-commodities :vehicles])))
    (is (= 33 (reduce + (vals (:flow-commodities a)))))))

(deftest test-no-per-worker-attribute-is-representable  ;; ── load-bearing G4 gate ──
  (let [[g _] (load*)]
    (doseq [p (:plants g)]
      (doseq [k (keys p)]
        (is (not (#{"worker" "person"} (namespace k)))
            (str "forbidden per-individual attribute leaked: " k)))
      ;; the aggregate SIZE is present and is a plain number, not a roster
      (is (number? (:plant/headcount-est p))))))

(deftest test-report-and-datoms-render
  (let [[g a] (load*)
        report (az/render-report g a)
        datoms (az/render-datoms a)]
    (is (re-find #"manufacturing concentration" report))
    (is (re-find #"malacca" report))
    (is (re-find #":concentration/derived true" datoms))
    (is (re-find #":concentration/sector :semiconductor" datoms))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-analyze)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
