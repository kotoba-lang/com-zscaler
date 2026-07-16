#!/usr/bin/env bb
;; uzu 渦 — generative-model tests (incl. the subject-dependence-of-meaning invariant).
;; Run: bb --classpath 20-actors 20-actors/uzu/methods/test_model.cljc
(ns uzu.methods.test-model
  (:require [uzu.methods.model :as m]
            [clojure.test :refer [deftest is run-tests]]))

(def abundant-sig (get m/regime-signature :abundant))
(def hostile-sig  (get m/regime-signature :hostile))

(deftest belief-normalizes
  (is (< (Math/abs (- 1.0 (reduce + (vals (m/update-belief m/uniform abundant-sig 0.15))))) 1e-9)))

(deftest perception-tracks-the-world
  ;; a clear abundant signal ⇒ belief concentrates on :abundant; a hostile one ⇒ :hostile
  (is (= :abundant (m/most-likely (m/update-belief m/uniform abundant-sig 0.15))))
  (is (= :hostile  (m/most-likely (m/update-belief m/uniform hostile-sig 0.15)))))

(deftest volatility-lets-belief-switch
  ;; after settling on abundant, a hostile observation must still be recognized
  ;; (a static filter would stay stuck — leak is what lets it switch)
  (let [stuck (reduce (fn [q _] (m/update-belief q abundant-sig 0.15)) m/uniform (range 5))]
    (is (= :hostile (m/most-likely (m/update-belief stuck hostile-sig 0.15))))))

(deftest free-energy-is-surprise
  ;; an observation the belief expects is LESS surprising than one it does not
  (let [q (m/update-belief m/uniform abundant-sig 0.15)]
    (is (< (m/free-energy q abundant-sig 0.15)
           (m/free-energy q hostile-sig 0.15)))))

(deftest entropy-bounds
  (is (< (Math/abs (- (Math/log 4) (m/entropy m/uniform))) 1e-9) "uniform = max entropy ln4")
  (is (< (m/entropy {:abundant 1.0 :scarce 0.0 :benign 0.0 :hostile 0.0}) 1e-6) "certain = ~0"))

;; ── THE invariant: meaning is subject-dependent ──────────────────────────────
(deftest meaning-is-subject-dependent
  ;; SAME belief, SAME affordable actions — different preference C ⇒ different action.
  (let [q (m/update-belief m/uniform abundant-sig 0.15)
        afford [:forage :flee :rest :explore]
        forager  (m/choose q {:nutrient 1.0  :threat 0.0} afford)   ; values nutrient
        ascetic  (m/choose q {:nutrient 0.05 :threat 0.0} afford)]  ; indifferent to nutrient
    (is (= :forage forager) "a nutrient-valuing subject forages abundance")
    (is (= :flee   ascetic) "an indifferent subject retreats from the SAME perception")
    (is (not= forager ascetic) "identical signal ⇒ different action ⇒ meaning is subject-dependent")))

(deftest threat-aversion-flees-hostility
  ;; a threat-averse subject reliably flees a believed-hostile regime
  (let [q (m/update-belief m/uniform hostile-sig 0.15)]
    (is (= :flee (m/choose q {:nutrient 1.0 :threat 0.0} [:forage :flee :rest :explore])))))

(deftest threat-seeking-forages-hostility
  ;; a threat-SEEKING pathology forages into the same hostile regime (⇒ it will die)
  (let [q (m/update-belief m/uniform hostile-sig 0.15)]
    (is (= :forage (m/choose q {:nutrient 1.0 :threat 1.0} [:forage :flee :rest :explore])))))

(deftest affordability-vetoes
  ;; choose only ever returns an affordable action
  (is (= :rest (m/choose (m/update-belief m/uniform abundant-sig 0.15)
                         {:nutrient 1.0 :threat 0.0} [:rest]))))

(let [{:keys [fail error]} (run-tests 'uzu.methods.test-model)]
  (when (pos? (+ fail error)) (System/exit 1)))
