(ns musubi.methods.test-ceremony-recognition-resolver
  "musubi -- unit tests for methods.ceremony-recognition-resolver (musubi's first
  real computed-logic slice, iteration #8). Core edge-case coverage (unknown
  jurisdiction, sort order, ceremony-type filter, blank-input rejection, coded G14
  invariant) uses an entirely SYNTHETIC fixture registry (fake jurisdiction codes
  'zz1'/'zz2'/'zz3', fake titles/authorities -- no real jurisdiction, no real
  person). A separate small composition-proof section exercises the REAL shipped
  registry/ceremony-recognition.seed.json (public jurisdiction civil-procedure
  facts, not personal data of any real person -- same category of fixture the
  actor already ships and iter #1-2 verified) to prove the resolver genuinely
  queries the actual seed rather than only ever running against a mock."
  (:require [clojure.test :refer [deftest is]]
            [musubi.methods.ceremony-recognition-resolver :as r]))

;; ── entirely synthetic fixture (no real jurisdiction/ceremony data) ──
(def synthetic-registry
  [{"recognitionId" "zz1-marriage-high" "jurisdiction" "zz1" "ceremonyType" "marriage"
    "confidence" "high" "title" "Zeta Civil Marriage Notice"}
   {"recognitionId" "zz1-marriage-low" "jurisdiction" "zz1" "ceremonyType" "marriage"
    "confidence" "low" "title" "Aardvark Alt Registration Path"}
   {"recognitionId" "zz1-marriage-medium-b" "jurisdiction" "zz1" "ceremonyType" "marriage"
    "confidence" "medium" "title" "Zulu Municipal Partnership"}
   {"recognitionId" "zz1-marriage-medium-a" "jurisdiction" "zz1" "ceremonyType" "marriage"
    "confidence" "medium" "title" "Beta Common-Law Status"}
   {"recognitionId" "zz1-naming-high" "jurisdiction" "zz1" "ceremonyType" "naming"
    "confidence" "high" "title" "Zeta Birth Notification"}
   {"recognitionId" "zz2-funeral-unknown-confidence" "jurisdiction" "zz2" "ceremonyType" "funeral"
    "confidence" "experimental-unrated" "title" "Zz2 Death Registration"}
   {"recognitionId" "zz2-funeral-high" "jurisdiction" "zz2" "ceremonyType" "funeral"
    "confidence" "high" "title" "Zz2 Cremation Permit"}])

;; ── unknown jurisdiction -> [] (never an error) ──
(deftest test-unknown-jurisdiction-returns-empty
  (is (= [] (r/resolve-recognition synthetic-registry "zz9-not-a-real-jurisdiction"))))

;; ── ceremony-type filter narrows to just that type for the jurisdiction ──
(deftest test-ceremony-type-filter
  (let [naming (r/resolve-recognition synthetic-registry "zz1" "naming")]
    (is (= 1 (count naming)))
    (is (= "zz1-naming-high" (get (first naming) "recognitionId")))))

;; ── nil / omitted ceremony-type -> all types for that jurisdiction ──
(deftest test-nil-ceremony-type-returns-all-types-for-jurisdiction
  (is (= 5 (count (r/resolve-recognition synthetic-registry "zz1"))))
  (is (= 5 (count (r/resolve-recognition synthetic-registry "zz1" nil)))))

;; ── confidence-desc (high -> medium -> low) then title-asc within a confidence tier ──
(deftest test-sort-order-confidence-then-title
  (let [ms (r/resolve-recognition synthetic-registry "zz1" "marriage")]
    (is (= ["zz1-marriage-high" "zz1-marriage-medium-a" "zz1-marriage-medium-b" "zz1-marriage-low"]
           (mapv #(get % "recognitionId") ms)))))

;; ── an unrecognized confidence string sorts LAST (rank 3), never errors ──
(deftest test-unknown-confidence-sorts-last-not-erroring
  (let [fs (r/resolve-recognition synthetic-registry "zz2" "funeral")]
    (is (= ["zz2-funeral-high" "zz2-funeral-unknown-confidence"]
           (mapv #(get % "recognitionId") fs)))))

;; ── G14 STRUCTURAL: every returned entry is coded isLegalOpinion=false + confersCivilStatus=false,
;;    even though the synthetic fixture itself never set those keys -- the resolver ASSERTS them ──
(deftest test-g14-coded-no-advice-no-civil-status
  (doseq [entry (r/resolve-recognition synthetic-registry "zz1")]
    (is (false? (get entry "isLegalOpinion")))
    (is (false? (get entry "confersCivilStatus")))))

;; ── blank / nil jurisdiction rejected structurally ──
(deftest test-rejects-blank-jurisdiction
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"jurisdiction"
        (r/resolve-recognition synthetic-registry nil)))
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"jurisdiction"
        (r/resolve-recognition synthetic-registry ""))))

;; ── has-recognition-mapping? convenience predicate ──
(deftest test-has-recognition-mapping
  (is (true? (r/has-recognition-mapping? synthetic-registry "zz1" "marriage")))
  (is (false? (r/has-recognition-mapping? synthetic-registry "zz1" "funeral")))
  (is (false? (r/has-recognition-mapping? synthetic-registry "zz9-not-a-real-jurisdiction"))))

;; ── genuine composition proof: resolve-recognition against the REAL shipped seed
;;    (public jurisdiction civil-procedure facts, not personal data) -- proves this
;;    isn't only ever exercised against a mock ──
#?(:clj
   (deftest test-real-seed-jpn-marriage-composition
     (let [reg (r/load-registry)
           ms (r/resolve-recognition reg "jpn" "marriage")]
       (is (= 3 (count ms)))
       (is (= ["jp-marriage-notification-konin-todoke"
               "jp-common-law-marriage-naien"
               "jp-municipal-partnership-oath"]
              (mapv #(get % "recognitionId") ms)))
       (is (every? #(false? (get % "isLegalOpinion")) ms))
       (is (every? #(false? (get % "confersCivilStatus")) ms))
       ;; unknown jurisdiction against the real seed too
       (is (= [] (r/resolve-recognition reg "zz9-not-a-real-jurisdiction"))))))
