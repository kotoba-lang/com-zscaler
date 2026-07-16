(ns tasuke.methods.test-triage
  "Tests for 助 (tasuke) triage — scam-kind classification, severity, free-windows, gates.
  1:1 port of `methods/test_triage.py` (clojure.test)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [tasuke.methods.edn :as tedn]
            [tasuke.methods.triage :as triage]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def SEED (delay (tedn/load-edn (io/file actor-dir "data" "seed-cybercrime-cases.kotoba.edn")))))

(defn- intake [& {:as over}]
  (merge {":case/id" "t1" ":case/consent" true ":case/support-cost-jpy" 0
          ":case/server-held-key" false ":case/narrative" ""}
         over))

(defn- lstrip-colon [s] (str/replace (str s) #"^:+" ""))

;; ── G1 全て無料 ──────────────────────────────────────────────────────────────
(deftest test-support-is-always-free
  (is (= 0 (triage/support-cost-jpy)))
  (is (= 0 (triage/support-cost-jpy {":case/loss-jpy" 999}))))

(deftest test-nonzero-cost-intake-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1"
        (triage/triage (intake ":case/support-cost-jpy" 100)))))

(deftest test-every-triage-output-is-free
  (doseq [c (get @SEED ":case/batch")]
    (is (= 0 (get (triage/triage c) ":triage/support-cost-jpy")))))

;; ── G7 consent + no-server-key ───────────────────────────────────────────────
(deftest test-no-consent-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G7"
        (triage/triage (intake ":case/consent" false)))))

(deftest test-server-held-key-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no-server-key"
        (triage/triage (intake ":case/server-held-key" true)))))

;; ── G4 classification is a KIND, in vocab, never a verdict ────────────────────
(deftest test-classify-keywords
  (is (= "unauthorized-transfer" (triage/classify (intake ":case/narrative" "口座から不正送金された"))))
  (is (= "account-takeover" (triage/classify (intake ":case/narrative" "アカウントが乗っ取りされてログインできない"))))
  (is (= "phishing" (triage/classify (intake ":case/narrative" "偽サイトのフィッシングにあった"))))
  (is (= "support-scam" (triage/classify (intake ":case/narrative" "サポート詐欺の警告画面")))))

(deftest test-explicit-scam-kind-honored
  (is (= "ransomware" (triage/classify (intake ":case/scam-kind" ":ransomware")))))

(deftest test-every-classification-is-in-vocab
  (doseq [c (get @SEED ":case/batch")]
    (is (some #{(lstrip-colon (get (triage/triage c) ":triage/scam-kind"))} triage/SCAM-KINDS))))

(deftest test-no-verdict-field-in-output
  (let [t (triage/triage (intake ":case/scam-kind" ":investment-scam"))]
    (is (not (some #(or (str/includes? % "verdict") (str/includes? % "guilty") (str/includes? % "crime"))
                   (keys t))))))

;; ── severity + actions + windows + G5 (no paid referral) ─────────────────────
(deftest test-unauthorized-transfer-with-loss-is-critical
  (let [t (triage/triage (intake ":case/scam-kind" ":unauthorized-transfer" ":case/loss-jpy" 5000))]
    (is (= ":critical" (get t ":triage/severity")))))

(deftest test-actions-nonempty-and-evidence-first
  (let [t (triage/triage (intake ":case/scam-kind" ":phishing"))]
    (is (seq (get t ":triage/actions")))
    (is (str/includes? (first (get t ":triage/actions")) "証拠"))))

(deftest test-windows-present-and-free
  (let [t (triage/triage (intake ":case/scam-kind" ":unauthorized-transfer"))]
    (is (seq (get t ":triage/windows")))
    (is (= false (get t ":triage/paid-referral")))))
