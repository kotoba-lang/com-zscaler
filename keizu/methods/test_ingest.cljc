(ns keizu.methods.test-ingest
  "test_ingest.py — 系図 (keizu) offline normalizer + G8 live refusal. ADR-2606066000.
  1:1 Clojure port (stdlib _t harness → clojure.test). The Python os.environ mutation for the
  with-gate path is expressed by binding the `*allow-live*` dynamic var."
  (:require [clojure.test :refer [deftest is run-tests]]
            [keizu.methods.ingest :as ingest]))

(deftest test-normalize-node-public-seat
  (let [n (ingest/normalize-node {"id" "s1" "scope" "public-role" "label" "会長 (seat)"
                                  "jurisdiction" "jp" "organ" "財務省" "sourcing" "representative"})]
    (is (= ":public-role" (get n ":node/scope")))
    (is (= "財務省" (get n ":node/organ")))))

(deftest test-normalize-node-rejects-private-scope
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G1"
                        (ingest/normalize-node {"id" "s1" "scope" "private-person"}))))

(deftest test-normalize-node-rejects-pii-field
  ;; G9 no-doxxing must bite on the INGEST path, not only on the seed
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"no-doxxing"
                        (ingest/normalize-node {"id" "s1" "scope" "public-role" "email" "a@b.jp"}))))

(deftest test-normalize-node-rejects-power-score
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G4"
                        (ingest/normalize-node {"id" "s1" "scope" "public-role" "power-score" 9}))))

(deftest test-normalize-committee
  (let [c (ingest/normalize-committee {"id" "c1" "label" "x" "jurisdiction" "jp" "organ" "m"
                                       "members" ["s1" "s2"] "term_from" 20250101
                                       "sources" ["https://x.gov/"]})]
    (is (= ["s1" "s2"] (get c ":committee/members")))
    (is (= ":representative" (get c ":committee/sourcing")))))

(deftest test-committee-needs-members
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G1"
                        (ingest/normalize-committee {"id" "c1" "members" [] "sources" ["u"]}))))

(deftest test-committee-needs-source
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G3"
                        (ingest/normalize-committee {"id" "c1" "members" ["s1"] "sources" []}))))

(deftest test-normalize-rel-validates
  (let [r (ingest/normalize-rel {"id" "r1" "source" "a" "target" "b" "kind" "funding-tie"
                                 "as_of" 20250101 "sources" ["u1" "u2"]})]
    (is (true? (get r ":rel/non-adjudicating-notice")))))

(deftest test-normalize-rel-rejects-verdict
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G2"
                        (ingest/normalize-rel {"id" "r1" "source" "a" "target" "b"
                                               "kind" "bribe" "sources" ["u1" "u2"]}))))

(deftest test-normalize-money-validates
  (let [m (ingest/normalize-money {"id" "m1" "payer" "a" "payee" "b" "kind" "subsidy"
                                   "amount" 1.0 "currency" "JPY" "sources" ["u1" "u2"]})]
    (is (= ":subsidy" (get m ":money/kind")))))

(deftest test-batch
  (let [out (ingest/normalize-batch
             {"nodes" [{"id" "s1" "scope" "public-role" "sourcing" "representative"}]
              "committees" [{"id" "c1" "members" ["s1"] "sources" ["u"]}]
              "rels" [{"id" "r1" "source" "s1" "target" "c1" "kind" "committee-membership"
                       "sources" ["u1" "u2"]}]
              "money" [{"id" "m1" "payer" "m" "payee" "s1" "kind" "procurement-award"
                        "amount" 1.0 "currency" "JPY" "sources" ["u1" "u2"]}]})]
    (is (= 1 (count (get out "nodes"))))
    (is (= 1 (count (get out "committees"))))
    (is (= 1 (count (get out "rels"))))
    (is (= 1 (count (get out "money"))))))

(deftest test-batch-aborts-on-bad-node
  ;; a PII-bearing node aborts the whole batch — no partial ingest
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"no-doxxing"
                        (ingest/normalize-batch {"nodes" [{"id" "s1" "scope" "public-role" "phone" "x"}]}))))

(deftest test-sourceid-drives-sourcing-registry-wins
  ;; a record naming an unverified-seed source is :representative EVEN IF it claims authoritative
  (let [r (ingest/normalize-rel {"id" "r1" "source" "a" "target" "b" "kind" "funding-tie"
                                 "sources" ["u1" "u2"] "sourceId" "jpn-procurement-pportal"
                                 "sourcing" "authoritative"})]
    (is (= ":representative" (get r ":rel/sourcing")))))   ;; registry (unverified) overrides the claim

(deftest test-no-sourceid-honors-caller-sourcing
  (let [r (ingest/normalize-rel {"id" "r1" "source" "a" "target" "b" "kind" "funding-tie"
                                 "sources" ["u1" "u2"] "sourcing" "authoritative"})]
    (is (= ":authoritative" (get r ":rel/sourcing")))))    ;; no registry source → caller's claim honored

(deftest test-money-sourceid-drives-sourcing
  (let [m (ingest/normalize-money {"id" "m1" "payer" "a" "payee" "b" "kind" "subsidy"
                                   "amount" 1.0 "currency" "JPY" "sources" ["u1" "u2"]
                                   "sourceId" "usa-fec" "sourcing" "authoritative"})]
    (is (= ":representative" (get m ":money/sourcing")))))

(deftest test-g8-live-refused-without-gate
  (binding [ingest/*allow-live* nil]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G8" (ingest/ingest-live)))))

(deftest test-g8-live-refused-even-with-gate
  (binding [ingest/*allow-live* "1"]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"not wired" (ingest/ingest-live)))))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-ingest)))
