(ns tate.tests.test-coverage-publish
  "tate 盾 — tests for the PUBLIC anonymized coverage digest (mesh-distributable).
  Asserts: aggregate-only (allowlist), G1 member-data-free (structural), CID stable +
  idempotent + ipfs-parity-shaped, and the JSON wrapper round-trips the CID."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [tate.methods.coverage-publish :as pub]))

(deftest test-digest-is-aggregate-allowlist-only
  (let [d (pub/aggregate-digest)
        allowed #{"covered_count" "jurisdictions" "coverage_ratio" "un_member_states"
                  "us_states_covered" "us_states_total" "procedure_tracks" "track_matrix"
                  "patterns_by_jurisdiction" "procedures_by_jurisdiction"
                  "clause_total" "clause_source_url_count" "proc_total" "proc_source_url_count"
                  "worklist_remaining" "named_gaps" "critical_deadline_count" "actor" "kind"}]
    ;; every key is in the explicit aggregate allowlist — nothing else can leak
    (is (every? allowed (keys d)) (str "unexpected key: " (remove allowed (keys d))))
    (is (= "tate" (get d "actor")))
    (is (pos? (get d "clause_total")))
    (is (pos? (get d "covered_count")))))

(deftest test-g1-member-data-free
  ;; the published digest carries NO member-document marker (G1, structural)
  (let [d (pub/aggregate-digest)]
    (is (false? (pub/member-leak? d)) "member-document marker leaked into the public digest"))
  ;; a deliberately poisoned body IS caught (the guard actually works)
  (is (true? (pub/member-leak? (assoc (pub/aggregate-digest) "x" {":doc/text" "secret"})))))

(deftest test-cid-stable-and-idempotent
  (let [d (pub/aggregate-digest)
        a (pub/content-id d)
        b (pub/content-id d)]
    (is (= a b) "CID must be deterministic for identical content")
    (is (str/starts-with? a "bafkrei") "CIDv1/raw/sha2-256 base32 begins with bafkrei")
    ;; canonical form is order-independent: shuffling top-level key order yields the same CID
    (is (= a (pub/content-id (into (sorted-map) d))))))

(deftest test-coverage-json-roundtrips-cid
  (let [d (pub/aggregate-digest)
        cidv (pub/content-id d)
        js (pub/coverage-json d)]
    (is (str/includes? js (str "\"cid\": \"" cidv "\"")) "JSON wrapper embeds the content-address")
    (is (str/includes? js "member-data-free") "JSON wrapper records the G1 status")
    (is (str/includes? js "\"actor\": \"tate\""))
    ;; the digest body is embedded under "digest"
    (is (str/includes? js "\"digest\":"))))
