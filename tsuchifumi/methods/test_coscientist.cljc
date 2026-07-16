#!/usr/bin/env bb
;; tsuchifumi 土踏み — co-scientist (identify+analyze) tests, incl. the charter gates.
;; Run: bb --classpath 20-actors 20-actors/tsuchifumi/methods/test_coscientist.cljc
(ns tsuchifumi.methods.test-coscientist
  (:require [tsuchifumi.methods.tsuchifumi-edn :as te]
            [tsuchifumi.methods.analyze :as an]
            [tsuchifumi.methods.coscientist :as cs]
            [clojure.set :as set]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (te/load-seed "20-actors/tsuchifumi/kotoba/seed.edn"))
(defn- assessment [] (an/assess (:regions seed) (:evidence seed)))
(defn- ident [] (cs/identify (assessment)))

;; ── identify returns both tracks (特定) ──────────────────────────────────────
(deftest identifies-action-and-research
  (let [i (ident)]
    (is (some? (get-in i ["identified" "action"])) "identifies a no-regret action")
    (is (some? (get-in i ["identified" "research"])) "identifies a research hypothesis")
    (is (= :action (:track (get-in i ["identified" "action"]))))
    (is (= :research (:track (get-in i ["identified" "research"]))))))

;; ── G2 — the identified ACTION never rests on contested evidence ─────────────
(deftest action-is-no-regret
  (let [a (get-in (ident) ["identified" "action"])]
    (is (#{:established :emerging} (:tier a))
        "an actionable hypothesis must rest on ≥ emerging evidence (G2)")))

(deftest research-is-contested-and-only-studied
  (let [r (get-in (ident) ["identified" "research"])]
    (is (#{:contested :anecdotal} (:tier r))
        "the research track holds contested hypotheses — to study, never to act on")))

;; ── G2 review — a contested candidate on the action track is vetoed ──────────
(deftest contested-action-vetoed
  (let [bad {:id "x" :mechanism "outdoor-time-nudge" :track :action :tier :contested
             :prediction "p"}]
    (is (= :contested-cannot-be-action (:reason (cs/review bad)))
        "you may STUDY a contested claim, never ACT on it (G2)")))

;; ── G-mechanism — a forbidden mechanism cannot enter ─────────────────────────
(deftest forbidden-mechanism-rejected
  (let [bad {:id "y" :mechanism "fear-marketing" :track :action :tier :established
             :prediction "p"}]
    (is (= :forbidden-mechanism (:reason (cs/review bad))))
    (is (empty? (set/intersection cs/aligned-mechanisms cs/forbidden-mechanisms))
        "aligned and forbidden mechanism sets are disjoint")
    (is (every? cs/aligned-mechanisms (map :mechanism cs/catalog))
        "every catalog mechanism is aligned (no forbidden mechanism is representable)")))

;; ── G-falsifiable — a candidate with no prediction is rejected ───────────────
(deftest not-falsifiable-rejected
  (is (= :not-falsifiable (:reason (cs/review {:id "z" :mechanism "open-publication"
                                              :track :action :tier :established :prediction ""})))))

;; ── Rank/Evolve sanity ───────────────────────────────────────────────────────
(deftest tournament-deterministic
  (is (= (cs/identify (assessment)) (cs/identify (assessment)))
      "the tournament is reproducible (no randomness, no wall clock)"))

(deftest evolve-produces-charter-clean-hybrid
  (let [i (ident) e (get i "evolved")]
    (when e
      (is (cs/aligned-mechanisms (:mechanism e)) "evolved hybrid stays charter-clean")
      (is (:ok (cs/review e)) "evolved hybrid passes review"))))

;; ── datoms — paired tier, no forbidden attribute ─────────────────────────────
(deftest datoms-clean
  (let [ds (cs/datoms (ident))
        attrs (set (map (fn [[_ _ a _]] a) ds))]
    (is (contains? attrs ":tsuchifumi.hyp/evidence-tier") "every hypothesis carries its tier (G2)")
    (is (contains? attrs ":tsuchifumi.hyp/prediction") "falsifiable prediction recorded")
    (doseq [bad [":tsuchifumi/diagnose" ":tsuchifumi/product" ":tsuchifumi.person/health"]]
      (is (not (contains? attrs bad))))))

(let [{:keys [fail error]} (run-tests 'tsuchifumi.methods.test-coscientist)]
  (when (pos? (+ fail error)) (System/exit 1)))
