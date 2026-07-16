(ns manabi.cells.cert-prep.test-cert-prep
  "manabi.cert_prep — cljc test suite for the 4 R0 scaffold cells.

  Each cell must:
    1. Load without error (require succeeds).
    2. Throw ExceptionInfo with the expected R0 scaffold message when solve is called.
    3. Carry :status :r0-scaffold in the ex-data.

  Parity evidence (verbatim Python vs cljc):
    Python  → RuntimeError:  manabi.cert_prep R0 scaffold: domain_review cell not activated. ...
    Clojure → ExceptionInfo: manabi.cert_prep R0 scaffold: domain_review cell not activated. ...
  (message prefix identical; exception type differs: RuntimeError vs ExceptionInfo — expected,
   since Clojure/bb does not have Python RuntimeError)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [manabi.cells.cert-prep.domain-review   :as domain-review]
            [manabi.cells.cert-prep.personal-material :as personal-material]
            [manabi.cells.cert-prep.practice-question :as practice-question]
            [manabi.cells.cert-prep.self-assessment   :as self-assessment]))

;; ── helpers ──────────────────────────────────────────────────────────────────

(defn- captures-r0-throw?
  "Returns true if calling (f {}) throws ExceptionInfo containing the R0 scaffold marker."
  [f]
  (try
    (f {})
    false
    (catch clojure.lang.ExceptionInfo e
      (and (clojure.string/includes? (ex-message e) "R0 scaffold")
           (= :r0-scaffold (-> e ex-data :status))))))

;; ── domain_review ─────────────────────────────────────────────────────────────

(deftest test-domain-review-loads
  (testing "domain_review cljc ns loads without error"
    (is (some? (find-ns 'manabi.cells.cert-prep.domain-review)))))

(deftest test-domain-review-throws-r0
  (testing "domain_review/solve throws R0 scaffold ExceptionInfo"
    (is (captures-r0-throw? domain-review/solve))))

(deftest test-domain-review-message-content
  (testing "domain_review R0 message contains ADR reference"
    (try
      (domain-review/solve {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (clojure.string/includes? (ex-message e) "ADR-2605264400"))
        (is (= :domain-review (-> e ex-data :cell)))))))

;; ── personal_material ─────────────────────────────────────────────────────────

(deftest test-personal-material-loads
  (testing "personal_material cljc ns loads without error"
    (is (some? (find-ns 'manabi.cells.cert-prep.personal-material)))))

(deftest test-personal-material-throws-r0
  (testing "personal_material/solve throws R0 scaffold ExceptionInfo"
    (is (captures-r0-throw? personal-material/solve))))

(deftest test-personal-material-message-content
  (testing "personal_material R0 message contains R2 activation gate"
    (try
      (personal-material/solve {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (clojure.string/includes? (ex-message e) "R2"))
        (is (= :personal-material (-> e ex-data :cell)))))))

;; ── practice_question ─────────────────────────────────────────────────────────

(deftest test-practice-question-loads
  (testing "practice_question cljc ns loads without error"
    (is (some? (find-ns 'manabi.cells.cert-prep.practice-question)))))

(deftest test-practice-question-throws-r0
  (testing "practice_question/solve throws R0 scaffold ExceptionInfo"
    (is (captures-r0-throw? practice-question/solve))))

(deftest test-practice-question-message-content
  (testing "practice_question R0 message contains G16 compliance reference"
    (try
      (practice-question/solve {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (clojure.string/includes? (ex-message e) "G15/G16/N11"))
        (is (= :practice-question (-> e ex-data :cell)))))))

;; ── self_assessment ───────────────────────────────────────────────────────────

(deftest test-self-assessment-loads
  (testing "self_assessment cljc ns loads without error"
    (is (some? (find-ns 'manabi.cells.cert-prep.self-assessment)))))

(deftest test-self-assessment-throws-r0
  (testing "self_assessment/solve throws R0 scaffold ExceptionInfo"
    (is (captures-r0-throw? self-assessment/solve))))

(deftest test-self-assessment-message-content
  (testing "self_assessment R0 message references domainMasteryAttestation"
    (try
      (self-assessment/solve {})
      (is false "should have thrown")
      (catch clojure.lang.ExceptionInfo e
        (is (clojure.string/includes? (ex-message e) "domainMasteryAttestation"))
        (is (= :self-assessment (-> e ex-data :cell)))))))

;; ── parity smoke ─────────────────────────────────────────────────────────────

(deftest test-all-cells-throw-r0
  (testing "all four cells throw R0 scaffold (aggregate parity guard)"
    (is (captures-r0-throw? domain-review/solve))
    (is (captures-r0-throw? personal-material/solve))
    (is (captures-r0-throw? practice-question/solve))
    (is (captures-r0-throw? self-assessment/solve))))
