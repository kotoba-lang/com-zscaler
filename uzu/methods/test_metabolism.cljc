#!/usr/bin/env bb
;; uzu 渦 — metabolism (the dissipative loop) tests: survival = fit of meaning to world.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_metabolism.cljc
(ns uzu.methods.test-metabolism
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.metabolism :as metab]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/uzu/kotoba/seed.edn")
(def seed (ue/classify (ue/load-edn seed-path)))
(def tape (:tape seed))
(defn org [id] (first (filter #(= id (:id %)) (:organisms seed))))
(defn lived [id] (metab/live (org id) tape))

;; ── self-maintenance is EARNED, not assumed ──────────────────────────────────
(deftest well-fitted-survives
  (let [s (lived "kurage")]
    (is (true? (:alive? s)) "kurage's meaning fits the world ⇒ it self-maintains")
    (is (> (:energy s) 0.0))
    (is (= 12 (count (:history s))) "lived the whole tape")))

(deftest threat-seeking-pathology-dies
  (let [s (lived "meial")]
    (is (false? (:alive? s)) "meial forages into hostility ⇒ death")
    (is (< (:lifespan (metab/summary s)) 12) "died before the tape ended")))

(deftest ascetic-under-draws-and-dies
  (let [s (lived "gyoja")]
    (is (false? (:alive? s)) "gyoja retreats from everything ⇒ under-draws ⇒ death")))

(deftest survivor-flees-danger-and-forages-safety
  (let [acts (:actions (metab/summary (lived "kurage")))]
    (is (pos? (get acts :flee 0)) "kurage flees the hostile steps")
    (is (pos? (get acts :forage 0)) "and forages the safe ones")))

;; ── meaning subject-dependence on the SAME tape ──────────────────────────────
(deftest same-tape-different-lives
  (let [k (metab/init-organism (org "kurage"))
        g (metab/init-organism (org "gyoja"))
        step0 (first tape)
        k1 (metab/beat k step0)
        g1 (metab/beat g step0)]
    (is (not= (:action (:last k1)) (:action (:last g1)))
        "on the identical first perception, kurage and gyoja act differently")))

;; ── two ledgers stay distinct (energy vs information free energy) ─────────────
(deftest datoms-keep-energy-and-free-energy-separate
  (let [ds (metab/datoms (lived "kurage"))
        attrs (set (map #(nth % 2) ds))]
    (is (contains? attrs ":uzu.beat/energy") "conserved energy attribute present")
    (is (contains? attrs ":uzu.beat/free-energy") "informational free-energy attribute present")
    (is (every? #(= 4 (count %)) ds) "all datoms are [op e a v]")
    (is (every? #(= ":db/add" (first %)) ds) "append-only :db/add")))

(deftest dead-organism-stops-beating
  (let [dead (assoc (metab/init-organism (org "kurage")) :alive? false :energy -1.0)]
    (is (= dead (metab/beat dead (first tape))) "a dead organism's loop does not advance")))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-metabolism)]
  (when (pos? (+ fail error)) (System/exit 1)))
