(ns himotoki.methods.test-request
  "test_request.py — himotoki 繙き disclosure-request generator tests.
  1:1 Clojure port of methods/test_request.py (stdlib pytest-style → clojure.test).

  Proves every charter gate fires: G3 own-data-only DSAR, G4 true-requester/no-pretext,
  G6 PII-as-encrypted-envelope (never plaintext), G8 no mass-filing, G14 verify-before-
  dispatch, G10 outbound-gated."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [himotoki.methods.request :as req]))

;; Resolve registry/targets.seed.json relative to THIS file — *file* is bound during require here.
(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))
(def ^:private reg (req/load-registry (str (io/file (actor-root) "registry" "targets.seed.json"))))
(def ^:private member
  {"requesterDid" "did:web:etzhayyim.com:member:alice" "ownDataOnly" true
   "subjectEnvelopeRef" "com.etzhayyim.encrypted:env:alice"})

(defn- a-dsar-target []
  (first (filter req/is-dsar (vals reg))))

(deftest test-registry-loads-with-stable-ids
  (is (and (seq reg) (contains? reg "Discord Inc.:ccpa-110"))))

(deftest test-dsar-classification
  (is (and (req/is-dsar {"regime" "ccpa-110"}) (req/is-dsar {"regime" "gdpr-15"})))
  (is (req/is-dsar {"regime" "appi-33"}))
  (is (not (req/is-dsar {"regime" "us-foia"}))))

(deftest test-dsar-requires-own-data-only-g3
  (let [t (a-dsar-target)
        bad (assoc member "ownDataOnly" false)]
    (is (try (req/build-request t bad) false
             (catch clojure.lang.ExceptionInfo e (str/includes? (.getMessage e) "G3"))))))

(deftest test-request-requires-true-requester-g4
  (let [t (a-dsar-target)]
    (is (try (req/build-request t {"ownDataOnly" true
                                   "subjectEnvelopeRef" "com.etzhayyim.encrypted:env:x"})
             false
             (catch clojure.lang.ExceptionInfo e (str/includes? (.getMessage e) "G4"))))))

(deftest test-pretext-field-refused-g4
  (let [t (a-dsar-target)
        bad (assoc member "sockpuppet" "fake-alice")]
    (is (try (req/build-request t bad) false
             (catch clojure.lang.ExceptionInfo e (str/includes? (.getMessage e) "G4"))))))

(deftest test-plaintext-pii-refused-g6
  (let [t (a-dsar-target)
        bad (assoc member "email" "alice@example.com")]
    (is (try (req/build-request t bad) false
             (catch clojure.lang.ExceptionInfo e (str/includes? (.getMessage e) "G6"))))))

(deftest test-non-envelope-subject-ref-refused-g6
  (let [t (a-dsar-target)
        bad (assoc member "subjectEnvelopeRef" "alice plaintext")]
    (is (try (req/build-request t bad) false
             (catch clojure.lang.ExceptionInfo e (str/includes? (.getMessage e) "G6"))))))

(deftest test-valid-draft-carries-envelope-not-plaintext
  (let [d (req/build-request (a-dsar-target) member)]
    (is (str/starts-with? (get d "subjectEnvelopeRef") "com.etzhayyim.encrypted:"))
    (is (and (not (contains? d "name")) (not (contains? d "email"))
             (false? (get d "dispatchReady"))))))

(deftest test-dispatch-refused-against-unverified-target-g14
  (let [t (a-dsar-target)                            ; all seed targets are unverified
        [allowed reason] (req/can-dispatch t true)]
    (is (and (false? allowed) (str/includes? reason "G14")))))

(deftest test-dispatch-refused-without-operator-gate-g10
  (let [t (assoc (a-dsar-target) "verificationStatus" "verified")
        [allowed reason] (req/can-dispatch t false)]
    (is (and (false? allowed) (str/includes? reason "G10")))))

(deftest test-dispatch-allowed-when-verified-and-gated
  (let [t (assoc (a-dsar-target) "verificationStatus" "verified")
        [allowed _] (req/can-dispatch t true)]
    (is (true? allowed))))

(deftest test-mass-filing-refused-g8
  (let [ids (vec (take (inc req/MAX-BATCH) (keys reg)))]
    (is (try (req/build-batch ids member reg) false
             (catch clojure.lang.ExceptionInfo e (str/includes? (.getMessage e) "G8"))))))

(deftest test-render-edn-marks-invariants
  (let [edn (req/render-edn [(req/build-request (a-dsar-target) member)])]
    (is (and (str/includes? edn ":himotoki.req/own-data-only")
             (str/includes? edn ":himotoki.req/dispatch-ready false")))
    (is (and (str/includes? edn "encrypted") (str/includes? edn "gated")))))

#?(:clj (defn -main [& _] (run-tests 'himotoki.methods.test-request)))
