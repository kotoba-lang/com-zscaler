(ns jinushi.methods.test-value-trend
  "jinushi 地主 — property-value as-of trajectory (差分 on value) tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [jinushi.methods.value-trend :as vt]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))

(deftest test-trend-yoy
  ;; synthetic: 75105 apartments €100/m² in y1, €110/m² in y2 → +10%
  (let [y1 [{:type "Appartement" :price-eur 1000.0 :surface-bati-m2 10.0 :commune "X" :mutation "a"}]
        y2 [{:type "Appartement" :price-eur 1100.0 :surface-bati-m2 10.0 :commune "X" :mutation "b"}]
        t (vt/trend {"2021" y1 "2022" y2})
        yoy (first (:yoy (get t "X")))]
    (is (= "2021" (:from yoy))) (is (= "2022" (:to yoy)))
    (is (< 9.9 (:pct yoy) 10.1) "+10% €/m² YoY")))

(deftest test-load-years-real
  (let [ys (vt/load-years data-dir)]
    (is (contains? ys "2023") "2023 present (fr-dvf-75105.raw.csv)")
    (is (contains? ys "2022") "2022 present (timeseries file)")))

(deftest test-real-trajectory
  (let [t (vt/trend (vt/load-years data-dir))
        p (get t "75105")]
    (is (= 2 (count (:by-year p))) "two years for Paris-5e")
    (is (> (get-in p [:by-year "2022" :eur-m2]) (get-in p [:by-year "2023" :eur-m2]))
        "Paris-5e apartment €/m² softened 2022→2023 (real, ~-3%)")
    (is (neg? (:pct (first (:yoy p)))) "negative YoY recorded")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-value-trend)]
    (System/exit (+ (or fail 0) (or error 0)))))
