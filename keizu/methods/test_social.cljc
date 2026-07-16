(ns keizu.methods.test-social
  "test_social.py — 系図 (keizu) dry-run social-post invariants. ADR-2606066000.
  1:1 Clojure port (stdlib _t harness → clojure.test). String-keyed post records;
  ':ns/name' keyword strings stay strings; assertRaises → (is (thrown? ...))."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [keizu.methods.social :as social]))

(def ^:private FINDING
  {"committee" "c1" "label" "demo委員会" "member_count" 3
   "distinct_organs" 2 "organs" ["A" "B"]})
(def ^:private SRCS ["https://a.gov/" "https://b.gov/"])

(deftest test-committee-post-pins-invariants
  (let [p (social/draft-committee-post FINDING SRCS)]
    (is (= ":dry-run" (get p ":post/status")))
    (is (true? (get p ":post/is-mirror")))
    (is (true? (get p ":post/non-adjudicating-notice")))
    (is (false? (get p ":post/server-held-key")))
    (is (str/starts-with? (get p ":post/body") (subs social/DISCLAIMER 0 8)))))

(deftest test-post-carries-sources
  (let [p (social/draft-committee-post FINDING SRCS)]
    (is (>= (count (get p ":post/sources")) 2))))

(deftest test-g3-under-sourced-post-refused
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G3"
                        (social/draft-committee-post FINDING ["only-one"]))))

(deftest test-post-refuses-commercial-gov-intel-source
  ;; the deny-list must also gate the OUTBOUND social-post path (Rider §2(e)/N5)
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"Rider §2\(e\)"
                        (social/draft-committee-post
                         FINDING ["https://about.bloomberg.com/government" "https://x.gov/"]))))

(deftest test-money-post-dry-run
  (let [mc {"hhi" 0.5 "total" 100.0 "shares" [["payee-x" 0.6] ["payee-y" 0.4]]}
        p (social/draft-money-post mc SRCS)]
    (is (= ":dry-run" (get p ":post/status")))
    (is (str/includes? (get p ":post/body") "HHI"))))

(deftest test-g8-live-post-refused
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G8"
                        (social/build-live))))

(deftest test-money-post-empty-shares-safe
  (let [p (social/draft-money-post {"hhi" 0.0 "total" 0.0 "shares" []} SRCS)]
    (is (= ":dry-run" (get p ":post/status")))
    ;; no IndexError on empty concentration
    (is (str/includes? (get p ":post/body") "(none)"))))

(deftest test-committee-post-blank-author-is-fine
  (let [p (social/draft-committee-post FINDING SRCS "")]
    ;; author only required for a (gated) live post
    (is (= "" (get p ":post/author")))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-social)))
