#!/usr/bin/env bb
(ns umisachi.tests.test-integration
  "umisachi 海幸 — end-to-end pipeline integration (ADR-2606074200 / 2605312345).

  The unit suites prove each method in isolation; this proves they COMPOSE as one coherent
  pipeline — ingest → merge → analyze → plan + social → persist → verify — over a fixture layered
  on the curated seed. Catches integration bugs no unit test sees: ingest output analyze can't
  consume, plan/social disagreeing with the analyze surface, a persisted graph that won't verify.
  Also confirms the charter gates hold on the MERGED (not just the seed) graph.

  Run:  bb --classpath 20-actors 20-actors/umisachi/tests/test_integration.clj"
  (:require [umisachi.methods.ingest :as ing]
            [umisachi.methods.analyze :as a]
            [umisachi.methods.plan :as p]
            [umisachi.methods.social :as s]
            [umisachi.methods.kotoba :as k]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private this-file *file*)
(defn- seed-path []
  (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile
      (io/file "data" "seed-umisachi-graph.kotoba.edn") .getAbsolutePath))

;; a fixture adding ONE new stock + ONE destructive-method fishery (G4 → reclassified to :pressure)
(def ^:private fixture-json
  (str "{\"records\":[\n"
       "{\"id\":\"anchovy-peru\",\"kind\":\"stock\",\"species\":\"Engraulis ringens\",\"status\":\"overfished\",\"region\":\"FAO-87\"},\n"
       "{\"id\":\"supertrawler-x\",\"kind\":\"fishery\",\"method\":\"factory-fishing\",\"scale\":\"industrial\"}\n"
       "]}\n"))

(defn- run-pipeline [tmp-fix]
  (spit tmp-fix fixture-json)
  (let [seed   (edn/read-string (slurp (seed-path)))
        bridged (:nodes (ing/bridge-source (.getAbsolutePath tmp-fix)))
        merged  (ing/merge-graph seed bridged)
        [nodes edges] (a/load-graph (pr-str merged))
        res    (a/analyze nodes edges)]
    {:seed seed :bridged bridged :merged merged :nodes nodes :edges edges :res res
     :recs (p/build-plan nodes res) :posts (s/compose nodes res 9)}))

(defn- with-pipeline [f]
  (let [fix (java.io.File/createTempFile "umi-int-" ".json")]
    (try (f (run-pipeline fix)) (finally (.delete fix)))))

(deftest ingest-flows-into-analyze
  (with-pipeline
    (fn [{:keys [merged nodes]}]
      ;; the two fixture records land in the merged graph (dedup-safe) and classify into nodes
      (is (= 57 (count merged)) "55 seed forms + 2 bridged nodes")
      (is (contains? nodes :stock.anchovy-peru) "new stock flows ingest→merge→analyze")
      ;; G4: the factory-fishing fishery is reclassified to a :pressure node, never a :fishery
      (is (contains? nodes :pressure.supertrawler-x))
      (is (not (contains? nodes :fishery.supertrawler-x))))))

(deftest charter-gates-hold-on-the-merged-graph
  (with-pipeline
    (fn [{:keys [nodes recs posts]}]
      ;; every merged node is charter-clean (analyze's load-time guard already passed it; re-assert)
      (is (every? #(= % (a/assert-charter-clean %)) (vals nodes)))
      ;; plan over the MERGED graph is still restoration-only by construction (no catch/interdiction)
      (is (every? #{:protect-rebuild :open-access :route-to-accountability} (map :plan/kind recs)))
      ;; every composed post over the MERGED graph passes the Charter §2 + G1 scan
      (is (every? #(s/charter-rider-clean (nth % 2)) posts)))))

(deftest persist-the-merged-graph-and-verify-chain
  (with-pipeline
    (fn [{:keys [nodes edges res]}]
      (let [tmp (java.io.File/createTempFile "umi-int-log-" ".kotoba.edn") path (.getAbsolutePath tmp)]
        (try
          (.delete tmp)
          (let [datoms (vec (concat (k/graph-datoms nodes edges) (k/derived-datoms res)))
                tx (k/make-tx datoms :tx-id 1 :as-of 1 :prev-cid "")
                cid (k/append-tx tx path)]
            (is (pos? (count datoms)) "merged graph emits datoms")
            (is (= cid (k/head-cid path)))
            (is (true? (:ok (k/verify-chain path))) "persisted merged graph verifies")
            ;; the new stock's node datoms are in the persisted log
            (is (some #(= :stock.anchovy-peru (nth % 1)) datoms) "fixture stock persisted"))
          (finally (.delete (io/file path))))))))

(deftest pipeline-is-deterministic-end-to-end
  ;; two full runs (fresh fixtures) → identical analyze surfaces + plan ids + persisted head-cid.
  (with-pipeline
    (fn [r1]
      (with-pipeline
        (fn [r2]
          (is (= (:res r1) (:res r2)) "analyze surfaces identical")
          (is (= (mapv :plan/id (:recs r1)) (mapv :plan/id (:recs r2))) "plan deterministic")
          (let [cid (fn [{:keys [nodes edges res]}]
                      (k/tx-cid (vec (concat (k/graph-datoms nodes edges) (k/derived-datoms res)))))]
            (is (= (cid r1) (cid r2)) "persisted graph CID deterministic")))))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'umisachi.tests.test-integration)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
