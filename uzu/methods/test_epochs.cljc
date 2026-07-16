#!/usr/bin/env bb
;; uzu 渦 — multi-epoch (seasons) life tests: a net-negative world starves even the fittest.
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_epochs.cljc
(ns uzu.methods.test-epochs
  (:require [uzu.methods.uzu-edn :as ue]
            [uzu.methods.metabolism :as metab]
            [clojure.test :refer [deftest is run-tests]]))

(def seed (ue/classify (ue/load-edn "20-actors/uzu/kotoba/seed.edn")))
(def tape (:tape seed))
(defn org [id] (first (filter #(= id (:id %)) (:organisms seed))))

(deftest one-epoch-equals-live
  (let [a (metab/live (org "kurage") tape)
        b (metab/live-epochs (org "kurage") tape 1)]
    (is (= (:energy a) (:energy b)))
    (is (= (:alive? a) (:alive? b)))
    (is (= (count (:history a)) (count (:history b))))))

(deftest net-negative-world-starves-the-fittest
  ;; kurage self-maintains for one season but the shipped world is net-negative,
  ;; so it dies in season 2 — fitness of meaning is necessary, not sufficient
  (is (true? (:alive? (metab/live-epochs (org "kurage") tape 1))) "survives season 1")
  (is (false? (:alive? (metab/live-epochs (org "kurage") tape 2))) "starves by season 2"))

(deftest death-is-monotonic
  ;; once dead, never alive again: the alive? flags are all-true then all-false
  (let [hist (:history (metab/live-epochs (org "kurage") tape 3))
        flags (map :alive? hist)]
    (is (not-any? true? (drop-while true? flags)) "no resurrection after death")))

(deftest history-bounded-by-epochs
  (let [n (count tape)
        s (metab/live-epochs (org "kurage") tape 3)]
    (is (<= (count (:history s)) (* 3 n)) "history never exceeds epochs × tape")
    (is (>= (count (:history s)) n) "lived at least one full season")))

(deftest epochs-are-deterministic
  (is (= (:energy (metab/live-epochs (org "kurage") tape 3))
         (:energy (metab/live-epochs (org "kurage") tape 3)))
      "no randomness / no wall clock ⇒ identical outcome"))

(deftest already-dead-stays-dead-across-the-seam
  ;; gyoja dies in season 1; more seasons cannot revive it
  (is (false? (:alive? (metab/live-epochs (org "gyoja") tape 4)))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-epochs)]
  (when (pos? (+ fail error)) (System/exit 1)))
