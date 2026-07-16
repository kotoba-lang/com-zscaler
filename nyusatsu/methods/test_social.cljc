(ns nyusatsu.methods.test-social
  "test_social.cljc — dry-run, member-signed, multilingual, non-adjudicating posts. ADR-2606271700."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [nyusatsu.methods.social :as social]))

(def bid
  {":bid/ocid" "ocds-ua-1" ":bid/jurisdiction" "UA"
   ":bid/issuer-name" "Rep Agency" ":bid/method" ":open"
   ":bid/status" ":active" ":bid/value-amount" 4200000 ":bid/value-currency" "UAH"
   ":bid/tender-end" "2026-07-01" ":bid/source-url" "https://public.api.openprocurement.org/x"
   ":bid/source-lang" "uk"
   ":bid/sources" ["https://public.api.openprocurement.org/x"]})

(deftest post-is-dry-run-member-signed
  (let [p (social/bid->post bid)]
    (testing "G8 dry-run, never published"
      (is (= ":dry-run" (get p ":post/status"))))
    (testing "G7 server never signs"
      (is (false? (get p ":post/server-held-key"))))
    (testing "G2 mirror + non-adjudicating"
      (is (true? (get p ":post/is-mirror")))
      (is (true? (get p ":post/non-adjudicating-notice")))
      (is (str/includes? (get p ":post/body") "not a verdict")))))

(deftest post-is-multilingual
  (let [p (social/bid->post bid)]
    (is (= ["uk" "en"] (get p ":post/langs")))
    (is (str/includes? (get p ":post/body") "Tender:"))
    (is (str/includes? (get p ":post/body") "入札公告:"))))

(deftest g3-post-needs-source
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3"
        (social/bid->post (assoc bid ":bid/sources" [])))))

(deftest posts-summary-plus-per-bid
  (let [bids [bid (assoc bid ":bid/ocid" "ocds-gb-1" ":bid/jurisdiction" "GB"
                         ":bid/value-currency" "GBP" ":bid/source-lang" "en")]
        ps (social/posts bids)]
    (testing "1 summary + 1 per bid"
      (is (= 3 (count ps)))
      (is (= "nyusatsu:summary" (get (first ps) ":post/id"))))
    (testing "every post is dry-run + member-signed (G7/G8 hold across the batch)"
      (is (every? #(= ":dry-run" (get % ":post/status")) ps))
      (is (every? #(false? (get % ":post/server-held-key")) ps)))
    (testing "summary mentions both jurisdictions"
      (is (str/includes? (get (first ps) ":post/body") "GB"))
      (is (str/includes? (get (first ps) ":post/body") "UA")))))
