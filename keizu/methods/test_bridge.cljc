(ns keizu.methods.test-bridge
  "test_bridge.py — 系図 (keizu) cross-actor compose (danjo + kanae). ADR-2606066000.
  1:1 Clojure port (stdlib _t harness → clojure.test). String-keyed maps; ':ns/name'
  keyword strings stay strings; assertRaises → (is (thrown? ...))."
  (:require [clojure.test :refer [deftest is run-tests]]
            [keizu.methods.bridge :as bridge]
            [keizu.methods.weave :as w]))

(def ^:private KANAE-OK
  {"id" "f1" "flowType" "appropriation" "donor" "jp-mof" "recipient" "jp-meti"
   "amount" 1.0e9 "currency" "JPY" "asOf" 20250401
   "sources" ["https://a.gov/" "https://b.gov/"]})

(def ^:private DANJO-OK
  {"id" "x1" "linkType" "awardee-officer-ubo-link" "from" "jp-vendor-x"
   "to" "jp-fsc-biz-1" "sourceRecordCids" ["cid:a" "cid:b"]})

(deftest test-kanae-flow-maps-to-money
  (let [m (bridge/bridge-kanae-flow KANAE-OK)]
    (is (= ":budget-outlay" (get m ":money/kind")))
    (is (= "jp-mof" (get m ":money/payer")))
    (is (= "jp-meti" (get m ":money/payee")))
    (is (clojure.string/starts-with? (get m ":money/id") "kanae:"))))

(deftest test-kanae-unknown-flowtype-refused
  (let [bad (assoc KANAE-OK "flowType" "mystery")]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"unknown kanae flowType"
                          (bridge/bridge-kanae-flow bad)))))

(deftest test-kanae-under-sourced-refused-by-keizu-gate
  (let [bad (assoc KANAE-OK "sources" ["only-one"])]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G3"
                          (bridge/bridge-kanae-flow bad)))))

(deftest test-danjo-crossref-maps-to-rel
  (let [r (bridge/bridge-danjo-crossref DANJO-OK)]
    (is (= ":co-membership" (get r ":rel/kind")))
    (is (true? (get r ":rel/non-adjudicating-notice")))
    (is (clojure.string/starts-with? (get r ":rel/id") "danjo:"))))

(deftest test-danjo-verdict-category-refused-at-import
  (let [bad (assoc DANJO-OK "linkType" "corruption")]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"verdict"
                          (bridge/bridge-danjo-crossref bad)))))

(deftest test-danjo-unmapped-linktype-refused
  (let [bad (assoc DANJO-OK "linkType" "some-new-thing")]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"unmapped"
                          (bridge/bridge-danjo-crossref bad)))))

(deftest test-danjo-under-sourced-refused
  (let [bad (assoc DANJO-OK "sourceRecordCids" ["only-one"])]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"G3"
                          (bridge/bridge-danjo-crossref bad)))))

(deftest test-batch-composes-both
  (let [out (bridge/bridge-batch {"kanae" [KANAE-OK] "danjo" [DANJO-OK]})]
    (is (= 1 (count (get out "money"))))
    (is (= 1 (count (get out "rels"))))))

(deftest test-batch-fails-whole-on-one-violation
  ;; a single bad record aborts the batch — no partial smuggling
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"verdict"
                        (bridge/bridge-batch {"danjo" [DANJO-OK (assoc DANJO-OK "linkType" "bribe")]}))))

(deftest test-bridged-records-weave-clean
  ;; the bridged datoms must pass the SAME validation the seed does
  (let [out (bridge/bridge-batch {"kanae" [KANAE-OK] "danjo" [DANJO-OK]})]
    (w/validate-money (first (get out "money")))
    (w/validate-rel (first (get out "rels")))
    (is true)))

#?(:clj (defn -main [& _] (run-tests 'keizu.methods.test-bridge)))
