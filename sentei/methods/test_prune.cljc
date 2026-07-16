(ns sentei.methods.test-prune
  "sentei 剪定 structural-invariant tests. 1:1 port of `test_prune.py`."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [sentei.methods.prune :as prune]))

(def AT "2026-06-07T20:00:00.000Z")
(def COUNCIL "did:web:etzhayyim.com:council#5of7")
(def MANIFESTED {"id" "kanae.fundFlowEdge:jp-mext-2024-outlay"
                 "manifested" true})
(def BUD {"id" "kanae.fundFlowEdge:jp-mext-2025-draft"
          "manifested" false})

;; ── G1 no-prior-restraint ──────────────────────────────────────────────────

(deftest test-cannot-prune-unmanifested-branch
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/prune BUD "quarantine" "anything" COUNCIL
                            {:at AT :council-level 7}))))

(deftest test-can-prune-after-it-manifests
  (let [p (prune/prune MANIFESTED "quarantine" "G10 review" COUNCIL
                       {:at AT :council-level 6})]
    (is (= "quarantine" (get p "action")))
    (is (= {(get MANIFESTED "id") "quarantine"}
           (prune/apply-log [p])))))

;; ── G2 append-only ──────────────────────────────────────────────────────────

(deftest test-prune-appends-never-deletes
  (let [p (prune/prune MANIFESTED "retract" "figure shown false" COUNCIL
                       {:at AT :council-level 6})]
    (is (= "prune" (get p "kind")))
    (is (contains? (prune/apply-log [p]) (get MANIFESTED "id")))))

(deftest test-as-of-time-travel-over-prune-history
  (let [p (prune/prune MANIFESTED "quarantine" "g10" COUNCIL
                       {:at "2026-06-07T20:00:00.000Z" :council-level 6})
        r (prune/regraft p COUNCIL {:at "2026-06-08T09:00:00.000Z"})]
    (is (= "live" (get (prune/apply-log [p r]) (get MANIFESTED "id"))))
    (is (= "quarantine"
           (get (prune/apply-log [p r] "2026-06-07")
                (get MANIFESTED "id"))))
    (is (= {} (prune/apply-log [p r] "2026-06-05")))))

;; ── G3 growth-unstoppable ───────────────────────────────────────────────────

(deftest test-no-halt-or-delete-action
  (doseq [bad ["delete" "halt-organism" "prior-restraint" "verdict"]]
    (is (not (contains? prune/PRUNE-ACTIONS bad)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (prune/prune MANIFESTED bad "x" COUNCIL
                              {:at AT :council-level 7})))))

;; ── G4/G7 transparent care ──────────────────────────────────────────────────

(deftest test-verdict-token-rejected
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/assert-no-verdict "this branch is 違法 and the awardee is guilty")))
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/prune MANIFESTED "revoke" "punish the 犯罪" COUNCIL
                            {:at AT :council-level 7}))))

(deftest test-basis-required
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/prune MANIFESTED "quarantine" "   " COUNCIL
                            {:at AT :council-level 6}))))

(deftest test-non-adjudicating-flag
  (let [p (prune/prune MANIFESTED "quarantine" "g10 review" COUNCIL
                       {:at AT :council-level 6})]
    (is (true? (get p "nonAdjudicating")))))

;; ── G5 no-server-key ────────────────────────────────────────────────────────

(deftest test-server-cannot-sign-prune
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/prune MANIFESTED "quarantine" "g10" "server"
                            {:at AT :council-level 7}))))

(deftest test-prune-serverheldkey-false
  (let [p (prune/prune MANIFESTED "quarantine" "g10" COUNCIL
                       {:at AT :council-level 6})]
    (is (false? (get p "serverHeldKey")))))

;; ── Council floor and vote ──────────────────────────────────────────────────

(deftest test-invariant-adjacent-needs-lv7
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/prune MANIFESTED "rollback" "§2(a) hit" COUNCIL
                             {:at AT :council-level 6 :invariant-adjacent true})))
  (let [ok (prune/prune MANIFESTED "rollback" "§2(a) hit" COUNCIL
                        {:at AT :council-level 7 :invariant-adjacent true})]
    (is (= 7 (get ok "councilLevel")))))

(deftest test-contested-prune-needs-majority
  (is (thrown? clojure.lang.ExceptionInfo
               (prune/prune MANIFESTED "quarantine" "contested" COUNCIL
                            {:at AT :council-level 6
                             :contested-vote {"yes" 2 "no" 5}})))
  (let [ok (prune/prune MANIFESTED "quarantine" "contested" COUNCIL
                        {:at AT :council-level 6
                         :contested-vote {"yes" 6 "no" 1}})]
    (is (= "quarantine" (get ok "action")))))

;; ── G6 reversible ───────────────────────────────────────────────────────────

(deftest test-regraft-heals
  (let [p (prune/prune MANIFESTED "quarantine" "mistaken cut" COUNCIL
                       {:at AT :council-level 6})
        r (prune/regraft p COUNCIL {:at "2026-06-09T00:00:00.000Z"})]
    (is (= "regraft" (get r "kind")))
    (is (= "live" (get (prune/apply-log [p r]) (get MANIFESTED "id"))))))

(deftest test-server-cannot-regraft
  (let [p (prune/prune MANIFESTED "quarantine" "x" COUNCIL
                       {:at AT :council-level 6})]
    (is (thrown? clojure.lang.ExceptionInfo
                 (prune/regraft p "server" {:at AT})))))

;; ── Datom serialization ─────────────────────────────────────────────────────

(deftest test-to-datoms-namespace
  (let [p (prune/prune MANIFESTED "quarantine" "g10" COUNCIL
                       {:at AT :council-level 6})
        datoms (prune/to-datoms p)]
    (is (every? #(str/starts-with? (get % "a") ":sentei.prune/") datoms))
    (is (some #(= ":sentei.prune/action" (get % "a")) datoms))))
