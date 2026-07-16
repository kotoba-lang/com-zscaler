(ns tatara.methods.test-maturity
  "tatara 鑪 — maturity scorecard tests (ADR-2606171800).

  The scorecard is GENERATED from the live graph + filesystem so it cannot drift; these tests
  pin that the derived metrics match analyze and that the renderer reflects checklist status
  honestly (✅ / 未)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tatara.methods.analyze :as az]
            [tatara.methods.crosscheck :as cc]
            [tatara.methods.maturity :as mat]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-plant-graph.kotoba.edn"))
(def kab-seed (io/file (.getParentFile actor-dir) "kabuto" "data" "seed-public-companies.kotoba.edn"))

(defn- a* [] (az/analyze (az/classify (az/load-edn seed))))

(deftest test-scorecard-derives-from-analyze
  (let [a (a*)
        sc (mat/scorecard a nil)]
    (is (= (:n-plants a) (:plants sc)))
    (is (= (count (:sector-stats a)) (:sectors sc)))
    (is (= (count (:country-roll a)) (:countries sc)))
    (is (= (:global-headcount a) (:employment sc)))
    ;; chokes carried in descending order, malacca first
    (is (= "malacca" (first (first (:chokes sc)))))
    (is (nil? (:linkage sc)))))

(deftest test-scorecard-carries-linkage-when-given
  (let [a (a*)
        cc-res (when (.exists kab-seed)
                 (cc/crosscheck (filter :plant/id (az/load-edn seed))
                                (filter :company/id (az/load-edn kab-seed))))
        sc (mat/scorecard a cc-res)]
    (when cc-res
      (is (= (:total cc-res) (get-in sc [:linkage :total])))
      (is (= (:resolved cc-res) (get-in sc [:linkage :resolved]))))))

(deftest test-render-reflects-checklist-status
  (let [sc (mat/scorecard (a*) nil)
        md-all-done (mat/render sc [["thing A" true] ["thing B" true]])
        md-mixed (mat/render sc [["thing A" true] ["thing B" false]])]
    (is (str/includes? md-all-done "maturity scorecard"))
    (is (str/includes? md-all-done "| plants | 33 |"))
    (is (str/includes? md-all-done "2/2"))          ;; both landed
    (is (str/includes? md-mixed "1/2"))             ;; one未
    (is (str/includes? md-mixed "| thing B | 未 |"))))

(deftest test-render-includes-deferred-honesty
  (let [md (mat/render (mat/scorecard (a*) nil) [["x" true]])]
    ;; the deferred section is always present — honest framing of what's NOT done
    (is (str/includes? md "Deferred / gated"))
    (is (str/includes? md "fleet cell placement"))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'tatara.methods.test-maturity)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
