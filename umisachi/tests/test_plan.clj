#!/usr/bin/env bb
(ns umisachi.tests.test-plan
  "umisachi 海幸 — restoration mission planner invariants (ADR-2606074200).

  Guards the actionable 'routed to RESTORATION' layer: every recommendation is PROTECT/REBUILD,
  OPEN-ACCESS, or ROUTE-TO-ACCOUNTABILITY — there is NO catch/harvest/interdiction kind by
  construction (G1/N1, a restoration map never a catch-target list). Protect targets stocks,
  open targets only ENCLOSED markets, accountability targets the 取-holders.

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_plan.clj"
  (:require [umisachi.methods.plan :as p]
            [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn") .getAbsolutePath))
(defn- graph [] (a/load-file (seed-path)))
(defn- plan [] (let [[n e] (graph)] (p/build-plan n (a/analyze n e))))

(def ^:private restoration-kinds #{:protect-rebuild :open-access :route-to-accountability})

(deftest plan-counts-and-kinds
  (let [recs (plan)
        by (fn [k] (count (filter #(= (:plan/kind %) k) recs)))]
    (is (= 14 (count recs)))
    (is (= 8 (by :protect-rebuild)))
    (is (= 1 (by :open-access)))
    (is (= 5 (by :route-to-accountability)) "top-5 of the 取-holders")))

(deftest only-restoration-kinds-exist-by-construction
  ;; the G1/N1 invariant: NO recommendation is a catch/harvest/interdiction action.
  (let [kinds (set (map :plan/kind (plan)))]
    (is (every? restoration-kinds kinds))
    (is (empty? (filter #(re-find #"catch|harvest|interdict|exploit|fish-here|cut"
                                  (str (name %))) kinds)))))

(deftest protect-targets-stocks-open-targets-enclosed-markets
  (let [[nodes _] (graph)
        recs (plan)
        protect (filter #(= :protect-rebuild (:plan/kind %)) recs)
        open (filter #(= :open-access (:plan/kind %)) recs)]
    (is (every? #(= :stock (get-in nodes [(:plan/target %) :organism/kind])) protect))
    (is (every? #(= :market (get-in nodes [(:plan/target %) :organism/kind])) open))
    ;; the :commons market (already open) is NOT routed to open-access — only enclosed/proprietary
    (is (not-any? #(= :market.local-commons (:plan/target %)) open))
    (is (some #(= :market.export-sushi (:plan/target %)) open) "proprietary channel is opened")))

(deftest accountability-targets-holders-and-routes-power-mirror
  (let [account (filter #(= :route-to-accountability (:plan/kind %)) (plan))]
    (is (= :pressure.industrial-overfishing (:plan/target (first account))) "highest-outbound 取-holder leads")
    (is (every? #(= ["danjo" "tsumugi" "kabuto"] (:plan/routes %)) account)
        "routed to the power-mirror accountability lineage")))

(deftest every-rec-is-deterministic-and-restoration-routed
  (is (= (mapv :plan/id (plan)) (mapv :plan/id (plan))) "deterministic")
  (doseq [r (plan)]
    (is (seq (:plan/routes r)) "every rec routes to a sibling actor")
    (is (str/includes? (:plan/g1-note r) "G1") "every rec carries the G1 invariant note")
    (is (number? (:plan/priority r)))))

(deftest renderers-state-the-g1-invariant
  (let [recs (plan)
        edn (p/render-edn recs) md (p/render-md recs)]
    (is (str/includes? edn "Never a catch-target"))
    (is (str/includes? (str/lower-case md) "never a catch-target list"))
    (is (str/includes? (str/lower-case md) "no catch / harvest / interdiction"))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-plan)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
