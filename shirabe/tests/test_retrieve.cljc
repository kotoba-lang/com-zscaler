(ns shirabe.tests.test-retrieve
  "shirabe — retrieve tests (dedup / rank / cap / provenance / G7). kotoba-clj."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [shirabe.methods.retrieve :as r]))

(deftest g7-fetcher-required
  (is (thrown? Exception (r/retrieve {:subqueries ["x"]} nil "t"))))

(def db
  {"営業時間" [{:title "島田 営業時間" :url "https://a/1" :snippet "営業時間 11:30 定休日 日曜"}
              {:title "無関係" :url "https://a/2" :snippet "全く関係ない話題"}]
   "場所" [{:title "島田 営業時間 場所" :url "https://a/1" :snippet "営業時間 場所 南青山"}
          {:title "地図" :url "https://a/3" :snippet "南青山 地図"}]})

(deftest dedup-rank-provenance
  (let [ev (r/retrieve {:subqueries ["営業時間" "場所"]} #(get db %) "2026-06-13")]
    (is (= 1 (count (filter #{"https://a/1"} (map :url ev)))) "url de-duplicated across sub-queries")
    (is (= "https://a/1" (:url (first ev))) "highest token-overlap result ranks first")
    (is (= (range 1 (inc (count ev))) (map :rank ev)) "ranks 1..n contiguous")
    (is (every? #(and (str/starts-with? (:snippet-cid %) "b") (= 33 (count (:snippet-cid %)))) ev)
        "every snippet content-addressed")
    (is (every? #(= "2026-06-13" (:retrieved-at %)) ev) "retrieved-at stamped (injected clock)")))

(deftest cap-top-k
  (let [big (mapv (fn [i] {:title (str "t" i) :url (str "https://b/" i) :snippet (str "x" i)}) (range 50))
        ev (r/retrieve {:subqueries ["q"]} (constantly big) "t")]
    (is (= r/top-k (count ev)) "evidence capped at top-k (G5)")))

(deftest fail-soft
  (let [f (fn [q] (if (= q "bad") (throw (RuntimeException. "boom"))
                      [{:title "ok" :url "https://c/1" :snippet "ok"}]))
        ev (r/retrieve {:subqueries ["good" "bad"]} f "t")]
    (is (= 1 (count ev)) "a dead sub-query is skipped, not fatal (G5 fail-soft)")))
