(ns kabuto.methods.test-social
  "test_social.py — kabuto 兜 social composer + Charter-Rider gate tests. ADR-2606022000.
  1:1 Clojure port of methods/test_social.py.

  Covers the Charter-Rider §2(a)-(h) deny-scan (the content gate every post must pass) and the G2
  framing invariant: every composed post is aggregate-first, public-facts-only, and framed as
  resilience/accountability — never a target-list."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [kabuto.methods.kabuto-edn :as kedn]
            [kabuto.methods.social :as social]))

(def ^:private seed
  (io/file (-> *file* io/file .getParentFile .getParentFile) "data" "seed-public-companies.kotoba.edn"))

(defn- load-classified []
  (let [rows (kedn/load-edn seed)]
    (kedn/classify rows)))

;; def test_charter_rider_rejects_prohibited_content
(deftest test-charter-rider-rejects-prohibited-content
  (is (= false (social/charter-rider-clean "where to cut the supply chain")))
  (is (= false (social/charter-rider-clean "weapon design for the foundry")))
  (is (= false (social/charter-rider-clean "buy adsense placement"))))

;; def test_charter_rider_accepts_clean_resilience_text
(deftest test-charter-rider-accepts-clean-resilience-text
  (is (= true (social/charter-rider-clean
               "Disclosed supply dependency — diversify to build resilience."))))

;; def test_compose_posts_pass_the_rider_gate
(deftest test-compose-posts-pass-the-rider-gate
  (let [{:keys [companies edges]} (load-classified)
        posts (vec (social/compose companies edges "top jurisdictions US/JP/CN"))]
    (is (seq posts) "expected composed posts")
    (doseq [[_subject _kind text] posts]
      (is (social/charter-rider-clean text) (str "composed post violates the Rider: " (pr-str text))))))

;; def test_compose_headline_is_not_a_target_list
(deftest test-compose-headline-is-not-a-target-list
  (let [{:keys [companies edges]} (load-classified)
        posts (vec (social/compose companies edges "summary"))
        headline (vec (for [[_s kind t] posts :when (= kind "intel-report")] t))]
    (is (and (seq headline) (str/includes? (first headline) "not a target-list")))))

;; def test_compose_supply_edges_frame_diversification
(deftest test-compose-supply-edges-frame-diversification
  (let [{:keys [companies edges]} (load-classified)
        edge-posts (vec (for [[_s kind t] (social/compose companies edges "") :when (= kind "supply-edge")] t))]
    (is (seq edge-posts))
    (doseq [t edge-posts]
      (is (or (str/includes? (str/lower-case t) "resilience")
              (str/includes? (str/lower-case t) "diversify"))))))

;; def test_post_record_is_well_formed_atproto_post
(deftest test-post-record-is-well-formed-atproto-post
  (let [rec (social/post-record "hello" ["en"])]
    (is (= (get rec "$type") "app.bsky.feed.post"))
    (is (and (<= (count (get rec "text")) 300) (str/ends-with? (get rec "createdAt") "Z")))))

#?(:clj
   (do
     (defn -main [& _] (run-tests 'kabuto.methods.test-social))
     (when (= *file* (System/getProperty "babashka.file")) (-main))))
