(ns saisei.tests.test-filing-plan
  "saisei 再生 — filing-plan builder tests (ADR-2607061800). clojure.test."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [saisei.methods.filing-plan :as p]))

(defn- by-id []
  (let [situations (p/load-situations)
        procs (p/load-procs)]
    (into {} (for [s situations] [(get s ":sit/id") (p/build-plan s procs)]))))

(defn- track [plan proc-id] (some #(when (= (get % "proc") proc-id) %) (get plan "tracks")))

(deftest test-jp-covered-both-tracks-disclosed
  (let [ps (by-id)
        pl (get ps "sit:jp-1")]
    (is (= (get pl "status") ":covered"))
    (is (= (get pl "jurisdiction") ":jp"))
    ;; G2: both jiko-hasan AND kojin-saisei are disclosed — saisei never picks one
    (is (track pl "proc:jp-jiko-hasan"))
    (is (track pl "proc:jp-kojin-saisei"))
    (is (seq (get pl "referrals")))))

(deftest test-g3-representation-unrepresentable
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (p/make-option {":opt/id" ":x" ":opt/kind" ":representation" ":opt/label" "x"}))))

(deftest test-g3-all-options-self-submit
  (let [ps (by-id)]
    (doseq [[_ pl] ps
            t (get pl "tracks")
            o (get t "options")]
      (is (not= (get o "kind") ":representation"))
      (is (= (get o "filed_by") "member")))))

(deftest test-g5-de-mandatory-precondition-blocks
  (let [ps (by-id)
        pl (get ps "sit:de-1")
        t (track pl "proc:de-verbraucherinsolvenz")]
    (is (= (get pl "status") ":covered"))
    (is (get t "blocked_on_precondition"))
    (is (seq (get t "mandatory_preconditions")))
    (is (str/includes? (get (first (get t "mandatory_preconditions")) "detail")
                        "außergerichtlich"))))

(deftest test-g5-jp-no-mandatory-precondition
  (let [ps (by-id)
        pl (get ps "sit:jp-1")
        t (track pl "proc:jp-jiko-hasan")]
    (is (not (get t "blocked_on_precondition")))
    (is (= (get t "mandatory_preconditions") []))))

(deftest test-uk-dro-structurally-referral-only
  (let [ps (by-id)
        pl (get ps "sit:uk-1")
        t (track pl "proc:uk-dro")]
    (is (get t "blocked_on_precondition"))
    (is (some #(str/includes? % "intermediary") (get t "refer_when")))))

(deftest test-g10-unknown-jurisdiction-degrades
  (let [ps (by-id)
        pl (get ps "sit:br-1")]
    (is (= (get pl "status") ":unknown-jurisdiction"))
    (is (= (get pl "tracks") []))
    (is (seq (get pl "referrals")))))

(deftest test-g4-timeline-never-a-computed-date
  (let [ps (by-id)
        pl (get ps "sit:us-1")
        t (track pl "proc:us-chapter7")
        tl (first (get t "timelines"))]
    (is (string? (get tl "rule")))
    (is (str/includes? (get tl "anchor") "11 U.S.C."))
    ;; the rule text itself must never contain a resolved calendar date pattern
    (is (not (re-find #"\d{4}-\d{2}-\d{2}" (get tl "rule"))))))

(deftest test-g7-referrals-always-present
  (let [ps (by-id)]
    (doseq [[_ pl] ps]
      (is (seq (get pl "referrals"))))))

(deftest test-report-renders
  (let [ps (vals (by-id))
        out (p/report ps)]
    (is (string? out))
    (is (str/includes? out "saisei"))))

(deftest test-legal-directory-present-per-covered-jurisdiction
  ;; Every covered jurisdiction (jp/us/uk/de) surfaces at least one real
  ;; bar-association/court/insolvency-practitioner directory entry — a LINK
  ;; only (saisei performs/supervises no legal work itself).
  (let [ps (by-id)]
    (doseq [j-id ["sit:jp-1" "sit:us-1" "sit:uk-1" "sit:de-1"]]
      (let [pl (get ps j-id)
            dir (get pl "legal_directory")]
        (is (seq dir))
        (doseq [d dir]
          (is (str/starts-with? (get d "url") "https://"))
          (is (some? (get d "kind"))))
        (is (some #(= (get % "kind") ":bar-association") dir))))))

(deftest test-legal-directory-empty-for-uncovered-jurisdiction
  (let [ps (by-id)
        pl (get ps "sit:br-1")]
    (is (= (get pl "legal_directory") []))))

(deftest test-official-forms-url-present-every-track-link-only
  ;; Every registered procedure carries a verified official-forms-url (a LINK
  ;; only — saisei never drafts/pre-fills the form itself, G3/N2).
  (let [procs (p/load-procs)]
    (doseq [proc procs]
      (let [url (get proc ":proc/official-forms-url")]
        (is (string? url))
        (is (str/starts-with? url "https://"))))
    (let [ps (by-id)
          pl (get ps "sit:jp-1")]
      (doseq [t (get pl "tracks")]
        (is (str/starts-with? (get t "official_forms_url") "https://"))))))
