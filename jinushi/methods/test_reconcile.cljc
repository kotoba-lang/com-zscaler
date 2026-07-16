(ns jinushi.methods.test-reconcile
  "jinushi 地主 — cross-source owner reconciliation (信頼度 payoff) tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [jinushi.methods.buildings :as b]
            [jinushi.methods.company-link :as company]
            [jinushi.methods.reconcile :as r]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))

(deftest test-gleif-wins-on-disagreement
  ;; Wikidata crowd label vs GLEIF authoritative legal name → GLEIF wins (higher trust).
  (let [bsnap {:owners {"Q1" {:label "Star Cruises" :lei "L1"}
                        "Q2" {:label "ACME INC" :lei "L2"}
                        "Q3" {:label "no-lei-owner"}}}
        gleif {"L1" {:legal-name "GENTING HONG KONG LIMITED" :jurisdiction "BM"}
               "L2" {:legal-name "ACME INC" :jurisdiction "US-DE"}}
        recs (r/reconcile-owners bsnap gleif)]
    (is (= 2 (count recs)) "only LEI-bearing owners present in GLEIF reconcile")
    (let [m (into {} (map (juxt :owner identity) recs))]
      (is (= "GENTING HONG KONG LIMITED" (:name (m "Q1"))) "GLEIF authoritative name wins")
      (is (= :gleif (:name-source (m "Q1"))))
      (is (false? (:name-agrees? (m "Q1"))) "disagreement recorded")
      (is (true? (:name-agrees? (m "Q2"))) "normalized-equal names agree"))))

(deftest test-report
  (let [bsnap {:owners {"Q1" {:label "Star Cruises" :lei "L1"} "Q2" {:label "ACME INC" :lei "L2"}}}
        gleif {"L1" {:legal-name "GENTING HONG KONG LIMITED"} "L2" {:legal-name "ACME INC"}}
        rep (r/report (r/reconcile-owners bsnap gleif))]
    (is (= 2 (:reconciled rep)))
    (is (= 2 (:name-from-gleif rep)) "name always from the higher-trust GLEIF")
    (is (= 1 (:name-disagreements rep)) "one disagreement (Star Cruises)")
    (is (= 0.5 (:agreement-rate rep)))))

(deftest test-real-reconcile
  (let [recs (r/reconcile-owners (b/load-snapshot data-dir) (company/load-gleif data-dir))
        rep (r/report recs)]
    (is (>= (:reconciled rep) 100) "many owners reconciled on real data")
    (is (every? :lei recs) "every reconciled record keyed by LEI")
    (is (every? #(= :gleif (:name-source %)) recs) "authoritative name source throughout")
    (is (pos? (:name-disagreements rep)) "real crowd↔authoritative naming gaps surfaced")))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-reconcile)]
    (System/exit (+ (or fail 0) (or error 0)))))
