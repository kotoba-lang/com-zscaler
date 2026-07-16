;; tsumugi 紡ぎ — ie-flow embedding tests (the SoS scoring leg). ADR-2606212200.
(ns tsumugi.methods.test-ie-flow
  (:require [clojure.test :refer [deftest is run-tests]]
            [tsumugi.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]))

(deftest events-well-formed
  (let [evs (ief/flow-events)]
    (is (pos? (count evs)) "one event per power-entity")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "tsumugi is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "tsumugi" (:actor %)) evs))
    (is (every? #(zero? (:risk %)) evs) "observation-only — mirrors, never holds")))

(deftest routes-toward-release-only
  ;; G2: a release MAP, never a target-list; every entity routes toward 解放
  (is (= #{"release"} (set (map :type (ief/flow-events))))
      "every power-holder routes toward RELEASE (not a target-list)"))

(deftest rectifies-holding-into-release
  (let [st (ief/flow-state)]
    (is (pos? (:order-index st))
        "tsumugi RECTIFIES scattered 取-holding into release-priority (release-target = max(0,held−1) concentrates on the biggest holders) → positive order-index")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest small-holders-export-no-release-energy
  ;; release-target = max(0, held−1) ⇒ an entity with held ≤ 1 exports 0 release-energy
  (let [evs (ief/flow-events)
        small (filter #(<= (:volume %) 1.0) evs)]
    (when (seq small)
      (is (every? #(zero? (:value %)) small)
          "held ≤ 1 ⇒ no release-target (the rectification concentrates on the big 取-holders)"))))

(deftest scoreboard-entry
  (let [s (score/info-control-score (ief/flow-state) {:descendant 0.75})]
    (is (not (:vetoed? s)) "tsumugi is charter-clean (release map) — not vetoed")
    (is (pos? (:score s)) "tsumugi earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'tsumugi.methods.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
