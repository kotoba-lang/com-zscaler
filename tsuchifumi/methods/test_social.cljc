#!/usr/bin/env bb
;; tsuchifumi 土踏み — atproto ossekai post-invariant tests (G1/G2/G4/G5 + no-server-key).
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_social.cljc
(ns tsuchifumi.methods.test-social
  (:require [tsuchifumi.methods.tsuchifumi-edn :as te]
            [tsuchifumi.methods.analyze :as an]
            [tsuchifumi.methods.risk :as risk]
            [tsuchifumi.methods.social :as s]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/tsuchifumi/kotoba/seed.edn")
(defn- seed [] (te/load-seed seed-path))
(def established [{:claim "緑地アクセスは wellbeing と関連" :tier :established :source "公衆衛生 review"}
                  {:claim "屋外活動の wellbeing 効果は確立" :tier :established :source "活動指針"}])
(def emerging [{:claim "夜間の画面光は概日リズムに影響しうる" :tier :emerging :source "睡眠研究"}])
(def contested [{:claim "アーシングが炎症を減らす(未確立)" :tier :contested :source "small-n"}
                {:claim "非熱的EMF影響(未確立)" :tier :contested :source "minority"}])
(defn- a-region [] (first (get (an/assess (:regions (seed)) (:evidence (seed))) "regions")))

;; ── every post is dry-run, no-server-key, routed to ossekai ──────────────────
(deftest post-invariants
  (let [p (s/draft-relief-post (a-region) established)]
    (is (= ":dry-run" (get p ":post/status")))
    (is (= false (get p ":post/server-held-key")) "no-server-key")
    (is (= ":ossekai" (get p ":post/route")) "routed to the carrier (御節介)")
    (is (= true (get p ":post/non-diagnostic")))
    (is (= true (get p ":post/no-fear-notice")))
    (is (= true (get p ":post/no-commerce")))))

;; ── G4 — a fear/alarm body is refused ────────────────────────────────────────
(deftest fear-refused
  (is (thrown? clojure.lang.ExceptionInfo (s/guard-no-fear "電磁波は危険です、がんになる"))
      "fear token → refused (G4)")
  (is (nil? (s/guard-no-fear (str s/DISCLAIMER "\n緑地で過ごしましょう")))
      "the disclaimer (which names risk to disclaim it) is exempt"))

;; ── G5 — a sales/product body is refused ─────────────────────────────────────
(deftest sales-refused
  (is (thrown? clojure.lang.ExceptionInfo (s/guard-no-sales "アーシングマットを今すぐ購入"))
      "sales token → refused (G5)"))

;; ── G1 — a diagnosis/treatment body is refused ───────────────────────────────
(deftest diagnosis-refused
  (is (thrown? clojure.lang.ExceptionInfo (s/guard-no-diagnosis "あなたの症状は私たちが治します"))
      "diagnosis/treatment token → refused (G1)"))

;; ── G2 — an unhedged EMF→health harm claim is refused ────────────────────────
(deftest emf-harm-claim-refused
  (is (thrown? clojure.lang.ExceptionInfo (s/guard-no-emf-harm-claim "電磁波が原因で病気になります"))
      "unhedged EMF harm assertion → refused (G2)"))

;; ── G2 — a practice nudge may not rest on a contested citation ───────────────
(deftest practice-post-rejects-contested-source
  (is (thrown? clojure.lang.ExceptionInfo (s/draft-relief-post (a-region) contested))
      "a no-regret practice post must rest on ≥ emerging evidence (G2)"))

(deftest honesty-post-may-name-contested
  (let [p (s/draft-honesty-post contested)]
    (is (= ":dry-run" (get p ":post/status")))
    (is (= true (get-in p [":post/proposal" ":proposal/anti-pseudoscience"]))
        "the honesty post is the only one allowed to NAME the contested claim — to disclaim it"))
  (is (thrown? clojure.lang.ExceptionInfo (s/draft-honesty-post []))
      "even the honesty post needs ≥1 citation"))

;; ── batch builds the full proposal set from real assessments ─────────────────
(deftest ossekai-batch-builds
  (let [assessment (an/assess (:regions (seed)) (:evidence (seed)))
        risk-a (risk/assess (:drivers (seed)))
        batch (s/ossekai-batch assessment (:evidence (seed)) risk-a)]
    (is (>= (:count batch) 3) "at least relief + honesty + leverage posts")
    (is (every? #(= ":dry-run" (get % ":post/status")) (:posts batch)))
    (is (every? #(= false (get % ":post/server-held-key")) (:posts batch)))))

;; ── live carry is structurally refused (no-server-key) ───────────────────────
(deftest build-live-refused
  (is (thrown? clojure.lang.ExceptionInfo (s/build-live))
      "tsuchifumi never publishes; ossekai carries — live build raises (no-server-key)"))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-social)]
  (when (pos? (+ fail error)) (System/exit 1)))
