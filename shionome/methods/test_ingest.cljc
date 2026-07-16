(ns shionome.methods.test-ingest
  "Cross-language oracle tests for shionome.methods.ingest — the Clojure port of
  methods/ingest.py (the offline normalizer). Ported 1:1 from the REAL Python test_ingest.py.

  Completes shionome's method surface (the last unported method): every record crosses the
  same weave validate-* membrane (G1 person / G9 no-doxxing / G4 rating / G2 トレードはしない /
  G3 ≥2-sources), G11 registry-sourcing-wins, and the G8 live gate refuses by default."
  (:require [clojure.test :refer [deftest is testing]]
            [shionome.methods.ingest :as ingest]))

(deftest normalize-bucket-ok
  (let [b (ingest/normalize-bucket {"id" "eq" "scope" "asset-class" "label" "EQ"
                                    "asset_class" "equities" "region" "us" "risk" "risk"
                                    "sources" ["https://www.sec.gov/"]})]
    (is (= "eq" (get b ":bucket/id")))
    (is (= ":asset-class" (get b ":bucket/scope")))
    (is (= "equities" (get b ":bucket/asset-class")))))

(deftest normalize-bucket-refuses-person
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1" (ingest/normalize-bucket {"id" "p" "scope" "individual"}))))

(deftest normalize-bucket-surfaces-pii
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no-doxxing"
                        (ingest/normalize-bucket {"id" "p" "scope" "asset-class" "account" "1234"}))))

(deftest normalize-bucket-surfaces-rating
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"trade instruction"
                        (ingest/normalize-bucket {"id" "p" "scope" "asset-class" "rating" "A"}))))

(deftest normalize-flow-ok
  (let [f (ingest/normalize-flow {"id" "f" "source" "a" "target" "b" "kind" "rotation"
                                  "magnitude" 2.0 "unit" "usd-bn" "as_of" 20260601
                                  "sources" ["x" "y"]})]
    (is (= ":rotation" (get f ":flow/kind")))
    (is (= true (get f ":flow/no-trade-notice")))))

(deftest normalize-flow-refuses-trade-token
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"トレードはしない"
                        (ingest/normalize-flow {"id" "f" "kind" "buy" "magnitude" 1.0 "sources" ["x" "y"]}))))

(deftest normalize-flow-refuses-undersourced
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3"
                        (ingest/normalize-flow {"id" "f" "kind" "rotation" "magnitude" 1.0 "sources" ["x"]}))))

(deftest normalize-flow-default-external-ends
  (let [f (ingest/normalize-flow {"id" "f" "target" "b" "kind" "fund-inflow" "magnitude" 1.0
                                  "sources" ["x" "y"]})]
    (is (= "external" (get f ":flow/source")))))

(deftest normalize-snapshot-ok
  (let [s (ingest/normalize-snapshot {"id" "s" "bucket" "b" "metric" "return-pct"
                                      "value" 1.2 "as_of" 20260601 "sources" ["x"]})]
    (is (= ":return-pct" (get s ":snap/metric")))))

(deftest normalize-batch-counts
  (let [out (ingest/normalize-batch
             {"buckets" [{"id" "eq" "scope" "asset-class" "sources" ["s"]}]
              "flows" [{"id" "f" "source" "external" "target" "eq" "kind" "fund-inflow"
                        "magnitude" 1.0 "sources" ["x" "y"]}]
              "snapshots" [{"id" "s" "bucket" "eq" "metric" "return-pct" "value" 1.0 "sources" ["x"]}]})]
    (is (= 1 (count (get out "buckets"))))
    (is (= 1 (count (get out "flows"))))
    (is (= 1 (count (get out "snapshots"))))))

(deftest sourcing-unknown-source-representative
  (let [f (ingest/normalize-flow {"id" "f" "source" "a" "target" "b" "kind" "rotation"
                                  "magnitude" 1.0 "sourceId" "no-such-source" "sources" ["x" "y"]})]
    (is (= ":representative" (get f ":flow/sourcing")))))

(deftest sourcing-registry-wins-over-claim
  ;; an unverified-seed registry source forces :representative even if caller claims authoritative
  (let [f (ingest/normalize-flow {"id" "f" "source" "a" "target" "b" "kind" "rotation"
                                  "magnitude" 1.0 "sourceId" "us-fred" "sourcing" "authoritative"
                                  "sources" ["x" "y"]})]
    (is (= ":representative" (get f ":flow/sourcing")))))

(deftest live-ingest-gated-g8
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G8" (ingest/ingest-live :allow-live nil))))

(deftest live-ingest-still-unwired-with-flag
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not wired" (ingest/ingest-live :allow-live "1"))))
