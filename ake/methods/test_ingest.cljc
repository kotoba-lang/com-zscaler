(ns ake.methods.test-ingest
  "Tests for ingest.cljc — genesis revision history over the actor-profile SSoT.
  ADR-2606052100. Port of `methods/test_ingest.py` (imports ingest + revision),
  plus a Python↔Clojure parity test (the genesis bridge produces a byte-identical
  revision history on both implementations — there is no content-addressing in these
  modules, so parity is over the plain revision-history shape, not a CID).

  HERMETIC: asserted against the committed FIXTURE
  (20-actors/ake/data/sample-profile-seed.kotoba.edn) with exact, known counts."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [ake.methods.ingest :as ingest]
            [ake.methods.revision :as rev]))

(defn- repo-root []
  (let [cwd (io/file (System/getProperty "user.dir"))]
    (loop [d cwd]
      (cond
        (nil? d) cwd
        (.exists (io/file d "20-actors" "ake" "data" "sample-profile-seed.kotoba.edn")) d
        :else (recur (.getParentFile d))))))

(def ^:private fixture
  (io/file (repo-root) "20-actors" "ake" "data" "sample-profile-seed.kotoba.edn"))

(def ^:private methods-dir
  (io/file (repo-root) "20-actors" "ake" "methods"))

;; ── hermetic: exact behaviour on the committed fixture (3 records / 7 revisions) ──

(deftest fixture-record-and-revision-counts-are-exact
  (let [res (ingest/genesis-revisions fixture)]
    (is (= 3 (:records res)))
    (is (= 7 (count (:history res))))))   ;; ake 3 + sample-corp 3 + sample-svc 1

(deftest fixture-covers-every-record-with-a-description-genesis
  (let [res (ingest/genesis-revisions fixture)]
    (doseq [h ["ake" "sample-corp" "sample-svc"]]
      (is (seq (rev/history-of (:history res) h "description"))
          (str "no genesis for " h)))))

(deftest fixture-ake-genesis-is-authoritative-with-value
  (let [res (ingest/genesis-revisions fixture)
        cur (rev/current (:history res) "ake" "description")]
    (is (some? cur))
    (is (= ":authoritative" (get cur ":revision/sourcing")))
    (is (str/includes? (get cur ":revision/value") "community-edit membrane"))))

(deftest fixture-description-only-record-yields-one-revision
  (let [res (ingest/genesis-revisions fixture)]
    ;; sample-svc has no display-name fields → exactly one (description) genesis revision
    (is (= 1 (count (rev/history-of (:history res) "sample-svc" "description"))))
    (is (nil? (rev/current (:history res) "sample-svc" "display-name-ja")))))

(deftest genesis-is-append-only-member-edit-layers-on-top
  (let [res (ingest/genesis-revisions fixture)
        h (:history res)
        base-n (count (rev/history-of h "ake" "description"))
        genesis-at (get (rev/current h "ake" "description") ":revision/as-of")
        member-edit {":edit/target-entity" "ake"
                     ":edit/target-attr" ":actor/description"
                     ":edit/proposed-value" "(member-proposed tweak)"
                     ":edit/sourcing" ":representative"
                     ":edit/author" "did:web:etzhayyim.com:member:abel"
                     ":edit/op" ":assert"}
        h2 (rev/append-revision h member-edit (+ genesis-at 1000))]
    (is (= (inc base-n) (count (rev/history-of h2 "ake" "description"))))   ;; grew by one
    (is (= "(member-proposed tweak)" (get (rev/current h2 "ake" "description") ":revision/value")))
    ;; time-travel: before the member edit, the authoritative genesis is still current
    (is (= ":authoritative"
           (get (rev/as-of h2 "ake" "description" genesis-at) ":revision/sourcing")))))

(deftest as-of-base-is-deterministic
  (let [a (ingest/genesis-revisions fixture ingest/genesis-as-of-base)
        b (ingest/genesis-revisions fixture ingest/genesis-as-of-base)]
    (is (= (mapv #(get % ":revision/as-of") (:history a))
           (mapv #(get % ":revision/as-of") (:history b))))))

(deftest report-renders-from-fixture
  (let [md (ingest/report (ingest/genesis-revisions fixture))]
    (is (str/includes? md "genesis revision history"))
    (is (str/includes? md "| ake |"))))

;; ── soft: membrane-over-REAL-data, validated only when ake is registered in the shared seed ──

(deftest real-repo-seed-integration-when-registered
  (let [res (ingest/genesis-revisions)]   ;; default = the REAL 00-contracts/.../actor-profile-seed
    (if-not (some #{"ake"} (:actors res))
      (is true "soft pass: ake not yet registered in the shared seed")
      (do
        (is (>= (:records res) 19))       ;; the real seed registers the full actor fleet
        (let [cur (rev/current (:history res) "ake" "description")]
          (is (some? cur))
          (is (= ":authoritative" (get cur ":revision/sourcing"))))))))

;; ── (removed) Python↔Clojure genesis-parity ──
;; The migration-verification deftest shelled out to ingest.py to assert the cljc port matched
;; the Python byte-for-byte. ake's Python is fully pruned (ADR-2606160842 py->clj port wave), so
;; the parity reference no longer exists; the cljc genesis bridge is exercised directly above
;; (genesis-fixture-counts + real-repo-seed-integration-when-registered).
