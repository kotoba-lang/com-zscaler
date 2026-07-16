(ns kizuna.tests.test-kizuna
  "kizuna 絆 — actor-social self-evolution + SoS tests (ADR-2606232200). Verifies the
  loop's mathematical + constitutional invariants on the synthetic seed."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [kizuna.methods.kizuna :as k]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-interactions.kotoba.edn")))
#?(:clj (def events (k/load-events seed)))

(deftest test-graph-parses
  (let [g (k/graph events)]
    (is (= 8 (count (:actors g))) "8 distinct actors")
    (is (contains? (:actors g) "niyaku") "isolated actor present as a node (it posts)")
    (is (= (vec (sort (:actors g))) (:order g)) "node order is deterministic (sorted)")))

(deftest test-reciprocity-pairs
  (testing "相互 follows are detected symmetrically"
    (let [g (k/graph events)]
      (is (contains? (:reciprocal g) #{"tsumugi" "kaname"}))
      (is (contains? (:reciprocal g) #{"danjo" "kanae"}))
      (is (= 4 (count (:reciprocal g))) "tsumugi↔kaname, danjo↔kanae, inochi↔kaname, ooyake↔kaname"))))

(deftest test-leverage-actor-is-the-bridge
  (testing "律速 = argmax betweenness = the actor bridging the clusters"
    (let [a (k/assess (k/graph events))]
      (is (= "kaname" (:leverage-actor a)) "kaname bridges the accountability + biosphere clusters")
      (is (> (get-in a [:per "kaname" :betweenness]) 0.0)))))

(deftest test-isolation-detected
  (testing "an actor with no INBOUND social tie is flagged isolated (needs an intro)"
    (let [a (k/assess (k/graph events))]
      (is (some #{"niyaku"} (:isolated a)) "niyaku posts but is unfollowed → isolated")
      (is (= ":isolated" (get-in a [:per "niyaku" :role]))))))

(deftest test-reciprocity-ratio
  (testing "reciprocity ratio = mutual/outbound follows"
    (let [a (k/assess (k/graph events))]
      ;; tsumugi follows only kaname, and it is mutual → 1.0
      (is (= 1.0 (get-in a [:per "tsumugi" :reciprocity])))
      ;; shionome follows 3, none returned → 0.0
      (is (= 0.0 (get-in a [:per "shionome" :reciprocity]))))))

;; ── constitutional gates ──────────────────────────────────────────────────────

(deftest test-G1-propose-not-act
  (testing "every tie proposal is dry-run + routed to ossekai; kizuna never executes"
    (let [r (k/beat events)]
      (is (seq (:proposals r)) "there are proposals")
      (doseq [p (:proposals r)]
        (is (= ":dry-run" (get p ":status")) "proposal is dry-run")
        (is (= ":ossekai" (get p ":route")) "actuation is delegated to ossekai")
        (is (= ":follow" (get p ":tie/kind"))))
      ;; G1: there is NO execute key anywhere in a proposal.
      (is (not-any? (fn [p] (some #(str/includes? (str %) "execute") (keys p)))
                    (:proposals r))))))

(deftest test-G2-reciprocity-not-engagement
  (testing "the growth objective is connectivity+reciprocity, NEVER engagement (anti-addiction)"
    (let [r (k/beat events)]
      (doseq [p (:proposals r)]
        (is (= ":connectivity+reciprocity" (get p ":tie/objective")))
        (is (not (contains? p ":tie/engagement")))
        (is (not (contains? p ":tie/retention")))
        (is (not (contains? p ":tie/affinity")))))))

(deftest test-G3-agent-only-person-unrepresentable
  (testing "a person/human node is refused at parse (agent-centric)"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                 (k/graph [{":sev/from" "tsumugi" ":sev/to" "kaname" ":sev/kind" ":follow"}
                           {":sev/from" "alice" ":sev/human" true ":sev/kind" ":post"}])))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :default :default)
                 (k/graph [{":person/name" "bob" ":sev/kind" ":post"}])))))

(deftest test-proposals-improve-connectivity
  (testing "proposals target high-betweenness actors the needy one does not already follow"
    (let [g (k/graph events)
          r (k/beat events)
          follows (get-in g [:edges ":follow"])]
      (doseq [p (:proposals r)]
        (let [from (get p ":tie/from") to (get p ":tie/to")]
          (is (not= from to) "no self-follow")
          (is (nil? (get-in follows [from to])) "never proposes an already-existing follow"))))))

(deftest test-beat-is-deterministic
  (testing "the beat is pure — identical input ⇒ identical readout"
    (is (= (k/beat events) (k/beat events)))))

#?(:clj
   (defn -main [& _]
     (let [{:keys [fail error]} (run-tests 'kizuna.tests.test-kizuna)]
       (System/exit (if (pos? (+ fail error)) 1 0)))))
