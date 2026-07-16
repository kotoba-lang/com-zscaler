#!/usr/bin/env bb
;; Working Clojure test for methods/ingest.clj (no Python test existed; new coverage).
(ns kabuto.methods.test-ingest
  "Tests for the kabuto 兜 public-company ingest bridge (methods/ingest.clj).

  Guards the bridge mapping (public record → :company/* :representative), the seed-wins dedup
  merge, and the G7 outward gate (full-universe live sources skipped without the operator gate).

  Run:  bb --classpath 20-actors 20-actors/kabuto/methods/test_ingest.clj"
  (:require [kabuto.methods.ingest :as ing]
            [clojure.test :refer [deftest is run-tests]]))

;; String-keyed input records (this ns's house convention — bridge-public-json reads
;; string keys like a parsed JSON dict, NOT Clojure keywords).
(def ^:private recs
  [{"id" "org.corp.jp.toyota" "name" "Toyota" "ticker" "7203" "exchange" "TSE"
    "country" "JP" "sector" "Automotive" "status" "listed" "lei" "abc123"}
   {"name" "NoIdCo" "ticker" "ZZ" "country" "US" "sector" "Tech"}])

(deftest bridge-maps-explicit-id-and-fields
  (let [[a _] (ing/bridge-public-json recs)]
    (is (= (get a ":company/id") "org.corp.jp.toyota"))
    (is (= (get a ":company/name") "Toyota"))
    (is (= (get a ":company/status") ":listed"))
    (is (= (get a ":company/exchange") ":tse"))        ; ":" + lower-cased
    (is (= (get a ":company/sector") ":automotive"))
    (is (= (get a ":company/lei") "abc123"))))

(deftest bridge-generates-cid-when-missing
  (let [[_ b] (ing/bridge-public-json recs)]
    (is (= (get b ":company/id") "org.corp.us.zz"))   ; org.corp.<country>.<ticker|lei|unknown>
    (is (= (get b ":company/name") "NoIdCo"))))

(deftest bridge-forces-representative-sourcing
  (is (every? #(= (get % ":company/sourcing") ":representative") (ing/bridge-public-json recs))))

(deftest merge-is-seed-wins
  (let [seed {"org.corp.jp.toyota" {":company/id" "org.corp.jp.toyota"}}
        bridged (ing/bridge-public-json recs)
        extra (ing/merge-bridged seed bridged)]
    ;; toyota already in seed → only the generated-cid company is extra
    (is (= (count extra) 1))
    (is (= (get (first extra) ":company/id") "org.corp.us.zz"))))

(deftest g7-gates-full-universe-sources
  (is (true? (ing/gated-source? "gleif" false)) "gleif live fetch gated without operator gate")
  (is (true? (ing/gated-source? "edgar" false)))
  (is (false? (ing/gated-source? "gleif" true)) "operator gate opens the live source")
  (is (false? (ing/gated-source? "file" false)) "offline file bridge is never gated")
  (is (false? (ing/gated-source? nil false))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'kabuto.methods.test-ingest)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
