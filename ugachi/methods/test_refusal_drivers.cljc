#!/usr/bin/env bb
;; ugachi 穿ち — tests for the refusal-driver profile (count vs severity).
;; Run:  bb --classpath 20-actors 20-actors/ugachi/methods/test_refusal_drivers.cljc
(ns ugachi.methods.test-refusal-drivers
  "Tests for refusal-drivers — among the §2(l) gate's REFUSED projects, which constitutional concern
  drives the refusals by COUNT vs by SEVERITY (summed multigen-risk). Surfaces that the most frequent
  refusal reason need not be the most severe. ASSESSMENT only — a stewardship profile of the
  constitutional reasons, never an industry name / target-list (G1/G2)."
  (:require [ugachi.methods.gate :as g]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private projects
  ;; two frequent-but-low-risk no-consent refusals + one rare-but-high-risk irreversible refusal
  [{:id "n1" :consent false :carbon :net-neutral :monopoly-effect :neutral :irreversibility 0.0}
   {:id "n2" :consent false :carbon :net-neutral :monopoly-effect :neutral :irreversibility 0.0}
   {:id "h1" :consent true :carbon :net-negative :monopoly-effect :diversify :irreversibility 1.0 :remediation 0.0}
   ;; a NON-refused project (route-to-recovery) — must be excluded from the refusal profile
   {:id "ok" :consent true :carbon :net-negative :monopoly-effect :diversify :irreversibility 0.0
    :recovery-alternative :viable}])

(deftest counts-only-the-refused-projects
  (let [{:keys [refused]} (g/refusal-drivers projects)]
    (is (= 3 refused) "3 of the 4 projects are refused; the route-to-recovery one is excluded")))

(deftest by-count-ranks-the-most-frequent-refusal-reason
  (let [{:keys [by-count dominant-by-count]} (g/refusal-drivers projects)]
    (is (= [[:no-consent 2] [:irreversible-multigen-harm 1]] by-count) "no-consent refuses the most projects")
    (is (= :no-consent dominant-by-count))))

(deftest by-severity-ranks-the-highest-risk-refusal-reason
  (let [{:keys [by-severity dominant-by-severity]} (g/refusal-drivers projects)]
    (is (= :irreversible-multigen-harm dominant-by-severity)
        "the single irreversible-harm refusal carries more multigen-risk than the two no-consent refusals combined")
    ;; the contrast: most FREQUENT reason ≠ most SEVERE reason
    (is (not= dominant-by-severity (:dominant-by-count (g/refusal-drivers projects))))))

(deftest no-refusals-yields-empty-profile
  (let [{:keys [refused dominant-by-count dominant-by-severity]}
        (g/refusal-drivers [{:id "ok" :consent true :carbon :net-negative :monopoly-effect :diversify
                             :irreversibility 0.0 :transparent true :descendant-benefit 0.9}])]
    (is (= 0 refused) "a permitted project contributes no refusal")
    (is (nil? dominant-by-count))
    (is (nil? dominant-by-severity))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'ugachi.methods.test-refusal-drivers)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
