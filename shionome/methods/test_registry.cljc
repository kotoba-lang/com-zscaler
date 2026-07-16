(ns shionome.methods.test-registry
  "Cross-language oracle tests for shionome.methods.registry — the Clojure port
  of methods/registry.py.

  Ported 1:1 from the REAL Python test_registry.py (the cross-language oracle):
  every assertion exercises the committed seed registry/sources.seed.json, so a
  divergence between the cljc and Python readers would fail here. Concrete oracle
  values were confirmed by running the real Python (12 sources, all
  'unverified-seed' → :representative, us-fred jurisdiction 'us')."
  (:require [clojure.test :refer [deftest is testing]]
            [shionome.methods.registry :as registry]))

(deftest source-ids-nonempty
  (testing "the seed registry lists ≥10 sources (oracle: exactly 12)"
    (is (>= (count (registry/source-ids)) 10))
    (is (= 12 (count (registry/source-ids))))
    (is (contains? (set (registry/source-ids)) "us-fred"))))

(deftest get-source-known
  (testing "us-fred resolves with jurisdiction 'us'"
    (is (= "us" (get (registry/get-source "us-fred") "jurisdiction")))))

(deftest get-source-unknown-raises
  (testing "an unknown id throws with 'no such source' (mirror of Python KeyError)"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                          #"no such source"
                          (registry/get-source "nope")))))

(deftest sourcing-unverified-is-representative
  (testing "a known but unverified-seed source stays :representative (G11)"
    (is (= ":representative" (registry/sourcing-for "us-fred")))))

(deftest sourcing-unknown-is-representative
  (testing "an unknown source id is conservatively :representative, never :authoritative"
    (is (= ":representative" (registry/sourcing-for "nope")))))

(deftest assert-source-allowed-blocks-terminal
  (testing "a commercial market-data terminal citation raises (Rider §2(e)/N5)"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                          #"Rider"
                          (registry/assert-source-allowed "via refinitiv eikon")))))

(deftest assert-source-allowed-passes-public
  (testing "a public-source citation passes (returns nil, no throw)"
    (is (nil? (registry/assert-source-allowed "https://fred.stlouisfed.org/")))))
