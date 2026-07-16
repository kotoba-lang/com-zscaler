(ns jinushi.methods.test-verify
  "jinushi 地主 — provenance-integrity verify tests."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [jinushi.methods.verify :as v]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def repo-root (-> actor-dir .getParentFile .getParentFile))
(def data-dir (io/file repo-root "80-data" "jinushi-land"))

(deftest test-committed-state-verifies
  (let [{:keys [ok checks]} (v/verify-provenance data-dir)]
    (is ok "committed snapshots match the CID+sha256 recorded in ingest-provenance.json")
    (is (every? :ok checks) "every checked artifact matches")
    (is (some #(= "wikidata-national-parks.kotoba.edn" (:artifact %)) checks) "NP snapshot is checked")))

(deftest test-sha256-hex-file
  (let [f (io/file data-dir "wikidata-national-parks.kotoba.edn")
        h (v/sha256-hex-file f)]
    (is (= 64 (count h)) "sha256 hex is 64 chars")
    (is (re-matches #"[0-9a-f]{64}" h) "lowercase hex")
    (is (= h (v/sha256-hex-file f)) "deterministic")))

(deftest test-detects-mismatch
  ;; a recorded entry with a wrong CID must fail the check (tamper/drift detection).
  (let [bad (v/check-artifact data-dir {:artifact "wikidata-national-parks.kotoba.edn"
                                        :cidv1 "bafkreiwrongwrongwrongwrongwrongwrongwrongwrongwrongwrongwro"
                                        :sha256 "deadbeef"})]
    (is (false? (:ok bad)) "wrong recorded CID is detected as a mismatch")
    (is (false? (:cid-ok bad)))))

(deftest test-missing-file
  (let [m (v/check-artifact data-dir {:artifact "does-not-exist.kotoba.edn" :cidv1 "x" :sha256 "y"})]
    (is (false? (:ok m)) "a missing artifact fails")
    (is (false? (:present m)))))

(when (= *file* (System/getProperty "babashka.file"))
  (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-verify)]
    (System/exit (+ (or fail 0) (or error 0)))))
