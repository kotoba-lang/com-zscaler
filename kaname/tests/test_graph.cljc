(ns kaname.tests.test-graph
  "kaname 要 — langgraph-clj actor tests (ADR-2606172100 R1). The StateGraph compiles, the node fns
  flow state correctly, and a full perceive→…→persist run over the real mirrors produces the 要 and
  a verified commit-DAG (guarded — skips if sibling mirror outputs are absent)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.graph :as graph]
            [kotoba.datom :as kd]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-sos.kotoba.edn")))

(deftest test-build-compiles
  (testing "the StateGraph compiles to an invokable graph"
    (is (some? (graph/build)))))

(deftest test-node-fns-flow-state
  (testing "leverage/route/osekkai node fns produce their keys on a constructed world (no perceive)"
    (let [{:keys [nodes edges] :as world} (sos/load-file* seed)
          s0 {:world world}
          s1 (merge s0 (graph/leverage s0))
          s2 (merge s1 (graph/route-point s1))
          s3 (merge s2 (graph/osekkai-handoff s2))]
      (is (contains? s1 :res1))
      (is (= "kaname.if.accred" (first (:point s1))))          ; the seed 要
      (is (= ":route-around" (get-in s2 [:route :route])))
      (is (false? (get-in s3 [:proposal :sent]))))))           ; advisory/unsent

#?(:clj
   (deftest test-full-run-persists
     (let [base (io/file actor-dir "..")
           chie (io/file base "chie" "out" "ai-ecosystem-datoms.kotoba.edn")]
       (if (.exists chie)
         (let [tmp (str (java.io.File/createTempFile "kaname-graph" ".edn"))]
           (.delete (io/file tmp))
           (let [out (graph/run {:base-dir (str base) :log-path tmp
                                 :tx-id "g0" :as-of "as-of:0" :live? false})]
             (testing "世界認識 perceived ≥2 mirrors into a multilayer world model"
               (is (>= (count (:loaded out)) 2))
               (is (pos? (count (get-in out [:world :nodes])))))
             (testing "the run produced a 要 and persisted a verified tx"
               (is (some? (:point out)))
               (is (true? (get-in out [:persist :appended])))
               (is (true? (:ok (kd/verify-chain tmp))))))
           (.delete (io/file tmp)))
         (testing "(skipped — sibling mirror outputs not present)" (is true))))))
