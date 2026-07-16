(ns tasuke.methods.test-charter-invariants
  "Structural charter-invariant tests for 助 (tasuke) — ADR-2606060900.
  1:1 port of `methods/test_charter_invariants.py` (pytest → clojure.test).

  Assert the invariants STRUCTURALLY over the parsed ontology / lexicons / code constants — not by
  grepping prose. The load-bearing trio: G1 全て無料, G2 本人作成・本人提出, G3 警察authored不可.
  Plus G5 (no paid referral), G6 (PII by ref), G7 (no-server-key), G9 (draft-only)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [tasuke.methods.edn :as tedn]
            [tasuke.methods.triage :as triage]))

;; ROOT/20-actors/tasuke via *file* (…/tasuke/methods/test_charter_invariants.cljc → up 2 = tasuke)
#?(:clj (def ^:private actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def ^:private repo-dir (-> actor-dir .getParentFile .getParentFile)))
#?(:clj (def ^:private lex-dir (io/file actor-dir "lex")))
#?(:clj (def ^:private ontology-file (io/file repo-dir "00-contracts" "schemas" "cybercrime-victim-support-ontology.kotoba.edn")))

(defn- onto [] (tedn/load-edn ontology-file))

(defn- props [lex-name]
  (get-in (tedn/load-edn (io/file lex-dir (str lex-name ".edn")))
          [":defs" ":main" ":record" ":properties"]))

(defn- allowed [ident]
  (some (fn [m] (when (and (map? m) (= (get m ":db/ident") ident)) (get m ":db/allowed")))
        (get (onto) ":schema")))

;; ── G1 全て無料 — cost is structurally 0 in ontology + lexicons + code ────────
(deftest test-ontology-support-cost-allowed-zero-only
  (is (= [0] (allowed ":support/cost-jpy"))))

(deftest test-intake-lexicon-cost-const-zero
  (is (= 0 (get (get (props "victimIntake") ":supportCostJpy") ":const"))))

(deftest test-supportcase-lexicon-cost-const-zero
  (is (= 0 (get (get (props "supportCase") ":supportCostJpy") ":const"))))

(deftest test-code-support-cost-is-zero
  (is (and (= 0 triage/SUPPORT-COST-JPY) (= 0 (triage/support-cost-jpy)))))

;; ── G3 警察authored不可 — doc author is member-only ──────────────────────────
(deftest test-ontology-doc-authors-member-only
  (is (= [":member"] (get (onto) ":ontology/doc-authors")))
  (is (= [":member"] (allowed ":doc/authored-by"))))

(deftest test-police-report-lexicon-author-const-member
  (is (= "member" (get (get (props "policeReportDraft") ":authoredBy") ":const"))))

(deftest test-doc-authors-exclude-police-official-server
  (let [authors (get (onto) ":ontology/doc-authors")]
    (doseq [forbidden [":police" ":official" ":server"]]
      (is (not (some #{forbidden} authors))))))

;; ── G2 本人作成・本人提出 — support-role has no 代理 ─────────────────────────
(deftest test-ontology-support-roles-exclude-representation
  (let [roles (get (onto) ":ontology/support-roles")]
    (is (= [":guide" ":draft-assist" ":self-submit"] roles))
    (doseq [forbidden [":represent" ":proxy-submit" ":agent-file"]]
      (is (not (some #{forbidden} roles))))))

(deftest test-recovery-lexicon-role-enum-excludes-representation
  (let [enum (set (get (get (props "recoveryPlan") ":supportRole") ":enum" []))]
    (is (= #{"guide" "draft-assist" "self-submit"} enum))
    (is (empty? (clojure.set/intersection enum #{"represent" "proxy-submit" "agent-file"})))))

(deftest test-docs-need-member-signature-const-true
  (doseq [lex ["policeReportDraft" "platformRequest"]]
    (is (= true (get (get (props lex) ":needsMemberSignature") ":const")))))

;; ── G5 no paid counsel ───────────────────────────────────────────────────────
(deftest test-ontology-referral-paid-allowed-false-only
  (is (= [false] (allowed ":referral/paid")))
  (is (= [false] (allowed ":support/paid-referral"))))

(deftest test-supportcase-paid-referral-const-false
  (is (= false (get (get (props "supportCase") ":paidReferral") ":const"))))

;; ── G6 PII-by-reference — evidence has no plaintext field ────────────────────
(deftest test-evidence-lexicon-has-no-plaintext-field
  (let [p (props "evidenceItem")]
    (is (not (some (fn [k] (let [s (str/lower-case (str k))]
                             (or (str/includes? s "plaintext") (str/includes? s "raw"))))
                   (keys p))))
    (is (contains? p ":envelopeRef"))))

;; ── G7 no-server-key ─────────────────────────────────────────────────────────
(deftest test-intake-server-held-key-const-false
  (is (= false (get (get (props "victimIntake") ":serverHeldKey") ":const"))))

(deftest test-ontology-server-held-key-allowed-false
  (is (= [false] (allowed ":case/server-held-key"))))

;; ── G9 draft-only at R0 ──────────────────────────────────────────────────────
(deftest test-doc-published-allowed-false
  (is (= [false] (allowed ":doc/published")))
  (doseq [lex ["policeReportDraft" "platformRequest" "recoveryPlan"]]
    (is (= false (get (get (props lex) ":published") ":const")))))

;; ── code ≡ ontology vocab ────────────────────────────────────────────────────
(deftest test-code-scam-kinds-match-ontology
  (let [o (set (map #(str/replace % #"^:+" "") (get (onto) ":ontology/scam-kinds")))]
    (is (= (set triage/SCAM-KINDS) o))))

#?(:clj (defn -main [& _] (run-tests 'tasuke.methods.test-charter-invariants)))
