#!/usr/bin/env bb
;; abaki 暴 — validation of the ossekai survival-tree route-around planner.
;; Run:  bb --classpath 20-actors 20-actors/abaki/methods/test_survival_tree.cljc
(ns abaki.methods.test-survival-tree
  "Validation of simulate-ossekai-survival-tree — abaki's route-around planner. Given a routing
  policy listing blocked (monopolized) domains, it narrates which decentralized fallback each
  blocked domain activates (the whole point of an anti-monopoly route-around: never punish, route
  around). It was ISOLATED. Pins each domain→fallback branch, that an unblocked domain contributes
  no branch, and that the branches compose — so a regression dropping or mis-wiring a fallback is
  caught."
  (:require [abaki.methods.react-router :as r]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]))

(defn- policy [& domains] {"blocked_entities" (mapv (fn [d] {"domain" d}) domains)})

(deftest each-blocked-domain-activates-its-decentralized-fallback
  (let [bio (r/simulate-ossekai-survival-tree (policy "biology"))]
    (is (str/includes? bio "Biology/Agri"))
    (is (str/includes? bio "suki") "biology blocked → suki (local heirloom seed bank) fallback"))
  (let [log (r/simulate-ossekai-survival-tree (policy "logistics"))]
    (is (str/includes? log "Logistics"))
    (is (str/includes? log "wadachi") "logistics blocked → wadachi (autonomous mesh delivery) fallback"))
  (let [cmp (r/simulate-ossekai-survival-tree (policy "compute"))]
    (is (str/includes? cmp "Compute"))
    (is (str/includes? cmp "ameno") "compute blocked → ameno (WebGPU local inference) fallback")))

(deftest an-unblocked-domain-contributes-no-branch
  (let [out (r/simulate-ossekai-survival-tree (policy "compute"))]   ;; only compute blocked
    (is (not (str/includes? out "Biology/Agri")) "biology not blocked → no biology branch")
    (is (not (str/includes? out "wadachi")) "logistics not blocked → no logistics branch"))
  (let [none (r/simulate-ossekai-survival-tree (policy))]
    (is (not (str/includes? none "suki")))
    (is (not (str/includes? none "wadachi")))
    (is (not (str/includes? none "ameno")))))

(deftest the-header-is-always-present-and-blocks-compose
  (is (str/includes? (r/simulate-ossekai-survival-tree (policy)) "Ossekai Survival Simulator")
      "the simulator header is emitted even with nothing blocked")
  (let [all (r/simulate-ossekai-survival-tree (policy "biology" "logistics" "compute"))]
    (is (str/includes? all "suki"))
    (is (str/includes? all "wadachi"))
    (is (str/includes? all "ameno") "all blocked domains compose their fallbacks into one tree")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'abaki.methods.test-survival-tree)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
