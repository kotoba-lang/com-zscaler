#!/usr/bin/env bb
;; iriai 入会 — social self-publication membrane tests (dry-run, no-server-key, charter gates).
;; Run:  bb --classpath 20-actors 20-actors/iriai/methods/test_social.cljc
(ns iriai.methods.test-social
  (:require [iriai.methods.iriai-edn :as ie]
            [iriai.methods.infra :as infra]
            [iriai.methods.fund :as fund]
            [iriai.methods.maintain :as maintain]
            [iriai.methods.social :as social]
            [iriai.cells.social-post.state-machine :as sm]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/iriai/kotoba/seed.edn")
(defn- cells [] (ie/cells seed-path))
(defn- assets [] (vec (filter #(= (:type %) :asset) (ie/parse-edn (slurp seed-path)))))
(def srcs ["ADR-2606272200" "did:web:etzhayyim.com:actor:iriai"])

;; ── every post is dry-run + keyless + commons-map + cash-zero + sim-only ───────
(deftest posts-pinned-invariants
  (doseq [p (social/drafts-from-seed (cells) (assets))]
    (is (= ":dry-run" (get p ":post/status")) "published is unrepresentable")
    (is (true? (get p ":post/is-commons-map")) "G1 — coverage map, never a shut-off list")
    (is (true? (get p ":post/cash-zero")) "G2 — §1.16 in-kind, never billed")
    (is (true? (get p ":post/sim-only")) "G5 — narrates the map, never actuates")
    (is (false? (get p ":post/server-held-key")) "no-server-key")
    (is (>= (count (get p ":post/sources")) 2) "≥2 provenance citations")))

;; ── the three post kinds carry their content ───────────────────────────────────
(deftest post-content
  (let [cov (social/draft-coverage-post (infra/assess (cells)) srcs)
        fnd (social/draft-funding-post (fund/plan (cells)) srcs)
        upk (social/draft-maintenance-post (maintain/plan (assets)) srcs)]
    (is (str/includes? (get cov ":post/body") "被覆"))
    (is (str/includes? (get fnd ":post/body") "現金 0") "funding post states cash≡0")
    (is (str/includes? (get upk ":post/body") "安全床") "upkeep post states safety-floor")))

;; ── G1 structural: a body with shut-off / per-person vocab is refused ──────────
(deftest g1-refuses-withhold-vocab
  ;; the public `post` builder runs assert-no-withhold; feed a poisoned subject via draft
  (is (thrown? clojure.lang.ExceptionInfo
               (#'iriai.methods.social/post "x" "電気を遮断する停止通知" srcs "")))
  (is (thrown? clojure.lang.ExceptionInfo
               (#'iriai.methods.social/post "x" "disconnect the household" srcs ""))))

;; ── ≥2 sources required; <2 throws ─────────────────────────────────────────────
(deftest sources-required
  (is (thrown? clojure.lang.ExceptionInfo (social/draft-coverage-post (infra/assess (cells)) ["only-one"])))
  (is (thrown? clojure.lang.ExceptionInfo (social/draft-funding-post (fund/plan (cells)) []))))

;; ── live posting refuses by construction (Council Lv6+ + operator + member-sig) ─
(deftest build-live-refuses
  (is (thrown? clojure.lang.ExceptionInfo (social/build-live {:any "args"}))))

;; ── cell state-machine: valid → drafted; bad inputs → refused ──────────────────
(deftest cell-membrane
  (let [ok (sm/transition-to-drafted {"subject" "coverage:lifeline-commons"
                                      "sources" srcs "requested_status" "dry-run"})
        cs (get ok "cell_state")]
    (is (= sm/phase-drafted (get cs "phase")))
    (is (= ":dry-run" (get-in cs ["payload" ":post/status"])))
    (is (true? (get-in cs ["payload" ":post/is-commons-map"]))))
  ;; <2 sources → refused
  (is (= sm/phase-refused (get-in (sm/transition-to-drafted {"subject" "x" "sources" ["one"]}) ["cell_state" "phase"])))
  ;; server-held-key true → refused
  (is (= sm/phase-refused (get-in (sm/transition-to-drafted {"subject" "x" "sources" srcs "server_held_key" true}) ["cell_state" "phase"])))
  ;; published request → refused (R0-gate)
  (is (= sm/phase-refused (get-in (sm/transition-to-drafted {"subject" "x" "sources" srcs "requested_status" "published"}) ["cell_state" "phase"])))
  ;; shut-off vocab in subject → refused (G1)
  (is (= sm/phase-refused (get-in (sm/transition-to-drafted {"subject" "遮断 list" "sources" srcs}) ["cell_state" "phase"]))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'iriai.methods.test-social)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
