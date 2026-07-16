(ns nyusatsu.methods.test-charter-invariants
  "test_charter_invariants.cljc — end-to-end over the :representative seed. ADR-2606271700.
  Loads the committed seed, re-validates every bid through the gates, and projects dry-run posts —
  proving the seed itself is charter-clean and the whole offline pipeline is green."
  (:require [clojure.test :refer [deftest is testing]]
            [nyusatsu.methods.edn :as edn]
            [nyusatsu.methods.normalize :as norm]
            [nyusatsu.methods.social :as social]))

(def seed-path "20-actors/nyusatsu/data/seed-procurement-graph.kotoba.edn")

(defn- seed-bids []
  (get (edn/load-edn seed-path) ":bids"))

(deftest seed-loads
  (let [bids (seed-bids)]
    (is (= 4 (count bids)))
    (is (= #{"UA" "GB" "MX" "JP-13"} (set (map #(get % ":bid/jurisdiction") bids))))))

(deftest every-seed-bid-passes-the-gates
  (doseq [b (seed-bids)]
    (is (= b (norm/validate-bid b))
        (str "seed bid " (get b ":bid/ocid") " must satisfy G1..G10"))))

(deftest awarded-seed-bid-has-two-sources
  (let [awarded (filter #(get % ":bid/awarded-supplier") (seed-bids))]
    (is (= 1 (count awarded)))
    (is (>= (count (get (first awarded) ":bid/sources")) 2) "G3: an award needs ≥2 primary sources")))

(deftest dedupe-is-idempotent-on-distinct-seed
  (let [bids (seed-bids)]
    (is (= (count bids) (count (norm/dedupe-bids bids)))
        "distinct ocids → dedupe is a no-op")))

(deftest seed-projects-clean-dry-run-posts
  (let [bids (seed-bids)
        ps (social/posts bids)]
    (testing "1 summary + 4 bid posts"
      (is (= 5 (count ps))))
    (testing "G8/G7 hold for every post"
      (is (every? #(= ":dry-run" (get % ":post/status")) ps))
      (is (every? #(false? (get % ":post/server-held-key")) ps)))
    (testing "no post asserts a winner/verdict field (G2)"
      (is (every? #(true? (get % ":post/non-adjudicating-notice")) ps)))))
