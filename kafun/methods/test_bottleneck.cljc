#!/usr/bin/env bb
;; kafun 花粉 — tests for the remediation pipeline bottleneck lens.
;; Run:  bb --classpath 20-actors 20-actors/kafun/methods/test_bottleneck.cljc
(ns kafun.methods.test-bottleneck
  "Tests for remediation-bottlenecks — the pipeline-constraint view: which blocking stage
  (:await-sapling-supply the 無花粉苗木 L1-1 隘路 / :await-consent) jams the most stands, plus the
  counterfactual value of resolving it (how many stalled stands would advance to :reforest-priority).
  G5 ASSESSMENT-only — the counterfactual never mutates a stand; aggregate verdict counts (G2)."
  (:require [kafun.methods.remediate :as r]
            [clojure.test :refer [deftest is run-tests]]))

(defn- stand [id ov]
  (merge {:id id :replant true :carbon :net-negative :consent true :protected false
          :sapling-supply :ok :reforest-viability 0.2 :area-ha 10 :emission-density 0.1 :exposed-pop-weight 0.1}
         ov))

(def ^:private high {:area-ha 10000 :emission-density 0.5 :exposed-pop-weight 1.0 :reforest-viability 0.6})

(def ^:private stands
  [(stand "hi1" (merge high {:sapling-supply :none}))   ; awaits supply; high burden+viability → would reforest
   (stand "hi2" (merge high {:sapling-supply :none}))   ; awaits supply; high → would reforest
   (stand "lo1" {:sapling-supply :none})                ; awaits supply; low → would only monitor
   (stand "c1" {:consent false})                        ; awaits consent
   (stand "x1" {:replant false})])                      ; refused

(deftest binding-constraint-is-the-biggest-blocker
  (let [{:keys [tally binding-constraint blockers]} (r/remediation-bottlenecks stands)]
    (is (= 3 (:await-sapling-supply tally)) "3 stands await sapling supply")
    (is (= 1 (:await-consent tally)) "1 awaits consent")
    (is (= :await-sapling-supply binding-constraint) "sapling supply jams the most stands — the L1-1 隘路")
    (is (= [[:await-sapling-supply 3] [:await-consent 1]] blockers) "blockers ranked by count, biggest first")))

(deftest unblock-potential-counts-stands-that-would-reforest
  (let [{:keys [unblock-potential]} (r/remediation-bottlenecks stands)]
    (is (= 2 unblock-potential)
        "resolving the sapling 隘路 advances the 2 high-burden/viability stands to :reforest-priority")))

(deftest assessment-only-the-stands-are-never-mutated-g5
  (let [snapshot (mapv #(select-keys % [:id :sapling-supply :consent]) stands)]
    (r/remediation-bottlenecks stands)
    (is (= snapshot (mapv #(select-keys % [:id :sapling-supply :consent]) stands))
        "kafun supplies no sapling and grants no consent — the input stands are unchanged (G5)")))

(deftest no-blockers-yields-nil-binding
  (let [{:keys [binding-constraint unblock-potential]}
        (r/remediation-bottlenecks [(stand "ok" (merge high {:sapling-supply :ok}))])]
    (is (nil? binding-constraint) "no awaiting stands → no binding constraint")
    (is (nil? unblock-potential) "and nothing to unblock")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'kafun.methods.test-bottleneck)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
