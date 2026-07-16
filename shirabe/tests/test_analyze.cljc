(ns shirabe.tests.test-analyze
  "shirabe — analyze/plan tests (question → research plan). kotoba-clj, runs under bb."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [shirabe.methods.analyze :as a]))

(deftest freshness-entity-question
  (let [p (a/plan "青山の島田は今日やっている?")]
    (is (= :ja (:lang p)))
    (is (true? (:freshness p)) "『今日…やっている』 is freshness-sensitive")
    (is (some #{"島田"} (:entities p)) (str "entities: " (:entities p)))
    (is (some #{"青山"} (:entities p)))
    (is (<= 1 (count (:subqueries p)) 4) "subqueries bounded ≤4 (G5)")
    (is (= "青山の島田は今日やっている" (first (:subqueries p))) "verbatim question is query #1")
    (is (some #(str/includes? % "営業") (:subqueries p)) "freshness → a 営業/定休 query")))

(deftest english-freshness
  (let [p (a/plan "Is Blue Bottle Aoyama open right now?")]
    (is (= :en (:lang p)))
    (is (true? (:freshness p)))))

(deftest definition-not-fresh
  (let [p (a/plan "kotoba の Datom log とは何?")]
    (is (= :definition (:qtype p)))
    (is (false? (:freshness p)))))

(deftest comparison
  (is (= :comparison (:qtype (a/plan "gemma4 と llama3 の違いは?")))))

(deftest deterministic
  (is (= (a/plan "青山の島田は今日やっている?") (a/plan "青山の島田は今日やっている?"))))
