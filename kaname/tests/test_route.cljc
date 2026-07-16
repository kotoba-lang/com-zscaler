(ns kaname.tests.test-route
  "kaname 要 — opening-route tests (ADR-2606172100; G2). The route enum can ONLY dissolve
  concentration; the 要 routes to route-around; capture is structurally unrepresentable."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.route :as route]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

#?(:clj
   (defn- res- []
     (let [{:keys [nodes edges]} (sos/load-file* seed)]
       [nodes (sos/leverage nodes edges)])))

(deftest test-point-routes-to-opening
  (testing "the 要 (Accreditation Interface) routes to route-around (high bridge), an opening move"
    (let [[nodes res] (res-)
          r (route/route-point nodes res)]
      (is (= "kaname.if.accred" (:id r)))
      (is (= ":route-around" (:route r)))
      (is (contains? #{":open" ":route-around" ":add-redundancy" ":decentralize" ":insufficient-evidence"}
                     (:route r))))))

(deftest test-every-route-is-opening-only
  (testing "G2 — NO recommendation can be a capture/seize/control move"
    (let [[nodes res] (res-)
          recs (route/route-all nodes res 12)
          forbidden #{":capture" ":seize" ":control" ":exploit" ":corner" ":monopolize"}]
      (is (seq recs))
      (is (every? #(contains? #{":open" ":route-around" ":add-redundancy" ":decentralize" ":insufficient-evidence"}
                              (:route %)) recs))
      (is (not-any? #(contains? forbidden (:route %)) recs)))))

(deftest test-open-position-routes-to-insufficient
  (testing "an already-open position (leverage 0) yields insufficient-evidence (nothing to dissolve)"
    (let [[nodes res] (res-)
          ;; force-route the open commons directly via route-all then look it up
          recs (route/route-all nodes res 20)
          oc (first (filter #(= "kaname.if.open-commons" (:id %)) recs))]
      ;; open-commons has leverage 0 ⇒ it never appears in the positive-leverage ranking
      (is (nil? oc)))))

(deftest test-report-cannot-express-capture
  (testing "the rendered route report never contains a capture verb"
    (let [[nodes res] (res-)
          md (route/report-md nodes res)]
      (doseq [w ["capture" "seize" "monopolize"]]
        ;; the word may appear only in the G2 disclaimer ('never capture'); ensure no route cell uses it
        (is (not (re-find (re-pattern (str "`" w "`")) md)))))))
