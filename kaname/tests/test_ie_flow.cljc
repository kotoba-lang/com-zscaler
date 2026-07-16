;; kaname 要 — ie-flow embedding tests (the SoS scoring leg). ADR-2606212200.
;; Run (needs the shared ie-flow lib + kotoba.datom on the classpath):
;;   bb -cp "20-actors:70-tools/src:20-actors/kotodama/src" \
;;      -e '(require (quote clojure.test) (quote kaname.tests.test-ie-flow)) \
;;          (clojure.test/run-tests (quote kaname.tests.test-ie-flow))'
(ns kaname.tests.test-ie-flow
  (:require [clojure.test :refer [deftest is run-tests]]
            [kaname.methods.ie-flow :as ief]
            [etzhayyim.ie-flow.score :as score]))

(deftest events-well-formed
  (let [evs (ief/flow-events)]
    (is (pos? (count evs)) "one event per leverage point")
    (is (every? #(and (:source %) (:target %) (:type %)) evs))
    (is (every? :agent? evs) "kaname is the agent doing the rectification")
    (is (every? #(>= (:value %) 0.0) evs))
    (is (every? #(= "kaname" (:actor %)) evs))
    (is (every? #(zero? (:risk %)) evs) "synthesis-only — proposes, never captures")))

(deftest rectifies-concentration-into-leverage
  (let [st (ief/flow-state)]
    (is (pos? (:order-index st))
        "kaname RECTIFIES diffuse cross-domain concentration C into the few 要 (L) → positive order-index")
    (is (pos? (:net-gain st)) "the information-energy flow pays for itself (Φ>0)")
    (is (not (:parasitic? st)) "non-parasitic — returns more order than it consumes (共生)")))

(deftest routes-are-opening-only
  ;; G2: every route DISSOLVES concentration; capture/seize/control are unrepresentable
  (let [routes (set (map :type (ief/flow-events)))]
    (is (every? #{"open" "decentralize" "route-around" "add-redundancy"} routes)
        "the route enum cannot express acquiring the 要 (opening-only, G2)")))

(deftest scoreboard-entry
  (let [s (score/info-control-score (ief/flow-state) {:descendant 0.75})]
    (is (not (:vetoed? s)) "kaname is charter-clean (opening-only) — not vetoed")
    (is (pos? (:score s)) "kaname earns a positive information-control score")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kaname.tests.test-ie-flow)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
