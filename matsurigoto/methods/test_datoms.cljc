(ns matsurigoto.methods.test-datoms
  "Tests for the R1.B datom-persistence layer (matsurigoto 政, ADR-2606062300).
  1:1 Clojure port of `methods/test_datoms.py`.

  Drives faithful ports of the REAL modules (tax-assess / civil-registry / corp-registry /
  credential-issue) to produce outputs, then verifies the EAVT conversion + the structural
  invariants (G1 unsigned, G2 spec-basis, G3 authority, G5 append-only, G8 gated).

  Module outputs are string-keyed maps mirroring the Python dicts exactly so datoms.cljc
  (also string-keyed) consumes them byte-for-byte the same. The module helpers below are
  minimal ports of just the functions the Python test calls (assess_from_return /
  assess_income_tax / register_birth / register_incorporation + validate_lei / issue_passport)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [matsurigoto.methods.datoms :as D]))

;; ── tax-assess port (assess_income_tax + assess_from_return + JPN.income table) ──
(def ^:private jpn-income-brackets
  [[0 0.05] [1950000 0.10] [3300000 0.20] [6950000 0.23]
   [9000000 0.33] [18000000 0.40] [40000000 0.45]])

(def ^:private rate-tables
  {"JPN.income"    {"currency" "JPY" "source" "所得税法 / 国税庁 速算表 (:representative)"
                    "brackets" jpn-income-brackets}
   "FLAT20.income" {"currency" "XXX" "source" "illustrative flat 20% (:representative)"
                    "brackets" [[0 0.20]]}})

(defn- py-round
  "Python round(x, n): round-half-even via exact BigDecimal of the double."
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      (.doubleValue)))

(defn- unsigned-receipt [amount currency]
  {"assessed_amount" amount "currency" currency "proof" nil
   "server_held_authority" false "status" "assessed-unsigned"})

(defn- assess-income-tax [taxable-income brackets]
  (when (< taxable-income 0) (throw (ex-info "taxable_income must be >= 0" {})))
  (when (empty? brackets) (throw (ex-info "brackets must be non-empty" {})))
  (let [n (count brackets)
        total (reduce
               (fn [acc i]
                 (let [[lower rate] (nth brackets i)
                       upper (if (< (inc i) n) (first (nth brackets (inc i)))
                                 Double/POSITIVE_INFINITY)]
                   (if (> taxable-income lower)
                     (+ acc (* (- (min (double taxable-income) upper) lower) rate))
                     acc)))
               0.0 (range n))]
    {"taxable_income" taxable-income
     "liability" (py-round total 2)
     "effective_rate" (if (and (number? taxable-income) (not (zero? taxable-income)))
                        (py-round (/ total taxable-income) 6) 0.0)}))

(defn- assess-from-return [gross deductions table-key]
  (when-not (contains? rate-tables table-key)
    (throw (ex-info (str "unknown rate table '" table-key "'") {})))
  (let [table (get rate-tables table-key)
        taxable (max 0.0 (- gross deductions))
        out (assess-income-tax taxable (get table "brackets"))]
    (assoc out
           "currency" (get table "currency")
           "rate_table" table-key
           "rate_table_source" (get table "source")
           "receipt" (unsigned-receipt (get out "liability") (get table "currency")))))

;; ── civil-registry port (register_birth) ──
(defn- register-birth [record-id child parents place occurred-at now]
  (when-not (seq child) (throw (ex-info "birth: child is required" {})))
  (when-not (seq parents) (throw (ex-info "birth: at least one parent is required" {})))
  (when-not (seq place) (throw (ex-info "birth: place is required" {})))
  (when (pos? (compare occurred-at now))
    (throw (ex-info "birth: occurrence cannot be in the future" {})))
  {"record" {"record_id" record-id "vital_kind" "birth" "occurred_at" occurred-at
             "fields" {"child" child "parents" (vec parents) "place" place}
             "immutable" true}
   "certificate" {"@context" ["https://www.w3.org/ns/credentials/v2"]
                  "type" ["VerifiableCredential" "BirthCertificate"]
                  "credentialSubject" {"id" child "record" record-id}
                  "proof" nil "server_held_authority" false "status" "issued-unsigned"}})

;; ── corp-registry port (LEI ISO 7064 MOD 97-10 + register_incorporation) ──
(defn- to-digits [s]
  (apply str (map (fn [ch]
                    (cond
                      (Character/isDigit ch) (str ch)
                      (and (>= (int ch) (int \A)) (<= (int ch) (int \Z))) (str (- (int ch) 55))
                      :else (throw (ex-info (str "LEI char must be [0-9A-Z], got " ch) {}))))
                  s)))

(defn- compute-lei-check-digits [base18]
  (when-not (= 18 (count base18)) (throw (ex-info "LEI base must be 18 chars" {})))
  (let [m (long (mod (biginteger (to-digits (str base18 "00"))) 97))]
    (format "%02d" (- 98 m))))

(defn- validate-lei [lei]
  (if (and (string? lei) (= 20 (count lei)))
    (try (= 1 (mod (biginteger (to-digits lei)) 97))
         (catch Exception _ false))
    false))

(defn- assign-lei [lou entity12]
  (let [base (str/upper-case (str lou "00" entity12))]
    (str base (compute-lei-check-digits base))))

(defn- register-incorporation [entity-name officers capital articles address jurisdiction sequence]
  (when-not (seq entity-name) (throw (ex-info "incorporation: entity_name required" {})))
  (when-not (seq officers) (throw (ex-info "incorporation: at least one officer required" {})))
  (when (< capital 0) (throw (ex-info "incorporation: capital must be >= 0" {})))
  (let [registry-number (str (str/upper-case jurisdiction) "-" (format "%08d" sequence))
        eid (-> (format "%012d" sequence) (subs 0 12) str/upper-case)
        lei (assign-lei "EZHY" eid)]
    {"record" {"record_id" registry-number "kind" "incorporation" "entity_name" entity-name
               "officers" (vec officers) "capital" capital "jurisdiction" jurisdiction
               "lei" lei "immutable" true}
     "lei" lei "registry_number" registry-number
     "certificate" {"@context" ["https://www.w3.org/ns/credentials/v2"]
                    "type" ["VerifiableCredential" "IncorporationCertificate"]
                    "credentialSubject" {"id" registry-number "record" registry-number}
                    "proof" nil "server_held_authority" false "status" "issued-unsigned"}}))

;; ── credential-issue port (TD3 MRZ + issue_passport) ──
(def ^:private mrz-weights [7 3 1])

(defn- char-value [ch]
  (cond
    (= ch \<) 0
    (Character/isDigit ch) (- (int ch) (int \0))
    (and (>= (int ch) (int \A)) (<= (int ch) (int \Z))) (- (int ch) 55)
    :else (throw (ex-info (str "MRZ char must be [0-9A-Z<], got " ch) {}))))

(defn- mrz-check-digit [data]
  (str (mod (reduce + 0 (map-indexed (fn [i ch] (* (char-value ch) (nth mrz-weights (mod i 3))))
                                     data))
            10)))

(defn- mpad [s n]
  (let [s (str/replace (str/upper-case s) " " "<")]
    (subs (str s (apply str (repeat n "<"))) 0 n)))

(defn- build-td3-mrz [doc-number issuing-state nationality surname given-names
                      dob sex expiry personal-number]
  (let [name-field (mpad (str surname "<<" given-names) 39)
        line1 (str "P<" (str/upper-case issuing-state) name-field)
        doc (mpad doc-number 9)
        c-doc (mrz-check-digit doc)
        c-dob (mrz-check-digit dob)
        c-exp (mrz-check-digit expiry)
        pers (mpad personal-number 14)
        c-pers (mrz-check-digit pers)
        composite (str doc c-doc dob c-dob expiry c-exp pers c-pers)
        c-comp (mrz-check-digit composite)
        line2 (str doc c-doc (str/upper-case nationality) dob c-dob sex
                   expiry c-exp pers c-pers c-comp)]
    {"line1" line1 "line2" line2
     "check_digits" {"doc" c-doc "dob" c-dob "expiry" c-exp "personal" c-pers "composite" c-comp}}))

(defn- issue-passport [doc-number issuing-state nationality surname given-names
                       dob sex expiry subject-did]
  (when-not (seq doc-number) (throw (ex-info "passport: doc_number required" {})))
  (when-not (seq surname) (throw (ex-info "passport: surname required" {})))
  (let [mrz (build-td3-mrz doc-number issuing-state nationality surname given-names
                           dob sex expiry "")]
    {"mrz" mrz
     "document" {"type" ["VerifiableCredential" "Passport"]
                 "credentialSubject" {"id" subject-did}
                 "mrz" mrz "sod" nil "proof" nil
                 "server_held_authority" false "status" "issued-unsigned"}}))

;; ── shared tx kwargs (port of TX) ──
(def TX {:operated-by ":etzhayyim-council" :authority-mode ":sovereign-governance"
         :as-of "2026-06-06T00:00:00Z" :spec-basis "spec"})

(defn- val-of [datoms attr]
  (vec (for [[_ a v] datoms :when (= a attr)] v)))

(deftest test-tax-assessment-datoms-roundtrip
  (let [out (assess-from-return 6000000 1000000 "JPN.income")
        ds (D/assessment-datoms out "t1" (assoc TX :service "tax.income.file"))]
    (is (= [572500.0] (val-of ds ":egov.assessment/liability")))
    (is (= ["tax-assess"] (val-of ds ":egov.tx/module")))
    (is (= [false] (val-of ds ":egov.tx/server-held-authority"))))) ; G1

(deftest test-civil-record-is-immutable-g5
  (let [out (register-birth "b1" "child:a" ["p"] "tokyo" "2026-06-01T00:00:00Z" "2026-06-05T00:00:00Z")
        ds (D/civil-datoms out "t2" (assoc TX :service "civil.birth.register"))]
    (is (= [true] (val-of ds ":egov.record/immutable")))   ; G5
    (is (= ["birth"] (val-of ds ":egov.record/kind")))))

(deftest test-incorporation-datoms-carry-valid-lei
  (let [out (register-incorporation "Co" ["o"] 0 "art" "addr" "JPN" 1)
        ds (D/incorporation-datoms out "t3" (assoc TX :service "corp.incorporation.register"))
        lei (first (val-of ds ":egov.record/lei"))]
    (is (validate-lei lei))                                 ; the persisted LEI is valid
    (is (= [true] (val-of ds ":egov.record/immutable")))))

(deftest test-passport-datoms-certificate-unsigned-g1
  (let [out (issue-passport "L898902C3" "UTO" "UTO" "ERIKSSON" "ANNA" "740812" "F" "120415" "did:x")
        ds (D/passport-datoms out "t4" (assoc TX :service "passport.issue"))]
    (is (= [nil] (val-of ds ":egov.cert/proof")))           ; G1 — unsigned on the log
    (is (= ["issued-unsigned"] (val-of ds ":egov.cert/status")))))

(deftest test-g1-rejects-a-signed-artifact
  (let [out0 (assess-from-return 1000000 0 "FLAT20.income")
        out (assoc-in out0 ["receipt" "proof"] "forged-sig")] ; simulate a signed artifact
    (is (thrown? clojure.lang.ExceptionInfo
                 (D/assessment-datoms out "t5" (assoc TX :service "tax.income.file"))))))

(deftest test-g3-rejects-unknown-operator
  (let [out (assess-from-return 1000000 0 "FLAT20.income")
        bad (assoc TX :operated-by ":the-platform")]
    (is (thrown? clojure.lang.ExceptionInfo
                 (D/assessment-datoms out "t6" (assoc bad :service "tax.income.file"))))))

(deftest test-g3-both-principals-accepted
  (let [out (assess-from-return 1000000 0 "FLAT20.income")
        a (D/assessment-datoms out "ta" {:service "s" :operated-by ":etzhayyim-council"
                                         :authority-mode ":sovereign-governance"
                                         :as-of "2026-06-06T00:00:00Z" :spec-basis "x"})
        b (D/assessment-datoms out "tb" {:service "s" :operated-by ":adopting-government"
                                         :authority-mode ":supplied-to-state"
                                         :as-of "2026-06-06T00:00:00Z" :spec-basis "x"})]
    (is (= [":etzhayyim-council"] (val-of a ":egov.tx/operated-by")))
    (is (= [":adopting-government"] (val-of b ":egov.tx/operated-by")))))

(deftest test-g2-requires-spec-basis
  (let [out (assess-from-return 1000000 0 "FLAT20.income")
        bad (assoc TX :spec-basis "")]
    (is (thrown? clojure.lang.ExceptionInfo
                 (D/assessment-datoms out "t7" (assoc bad :service "s"))))))

(deftest test-ingest-batch-dry-run-body
  (let [out (assess-from-return 1000000 0 "FLAT20.income")
        ds (D/assessment-datoms out "t8" (assoc TX :service "s"))
        body (D/kg-ingest-batch ds)]
    (is (= "kg.ingest_batch" (get body "op")))
    (is (false? (get body "published")))
    (is (= (count ds) (get body "count")))))

(deftest test-g8-live-publish-is-gated
  (is (thrown? clojure.lang.ExceptionInfo
               (D/kg-ingest-batch [] {:published true}))))
