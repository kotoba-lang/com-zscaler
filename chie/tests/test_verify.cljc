(ns chie.tests.test-verify
  "chie 智慧 — integrity / charter-gate tests (ADR-2606171200). The committed seed passes
  the full self-audit clean; each adversarial mutation is caught:
    - G4 forbidden token on a node/edge → error
    - bad / missing sourcing → error
    - edge to an unknown node → error
    - grasping-load outside [0,1] → error
    - a person node with a private-profile attr → error (G2)
    - undeclared attr vs the schema → DRIFT error"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [chie.methods.analyze :as analyze]
            [chie.methods.verify :as v]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def seed (io/file actor-dir "data" "seed-ai-ecosystem.kotoba.edn")))
#?(:clj (defn- repo-root []
          (loop [d actor-dir] (cond (nil? d) actor-dir
                                    (.exists (io/file d "00-contracts" "schemas")) d
                                    :else (recur (.getParentFile d))))))
#?(:clj (def schema (io/file (repo-root) "00-contracts" "schemas" "ai-ecosystem-ontology.kotoba.edn")))
#?(:clj (defn- graph [] (analyze/load-file* seed)))

#?(:clj
   (deftest test-committed-seed-passes-clean
     (let [r (v/verify-files seed schema)]
       (is (:ok r) (str "seed must pass clean; errors: " (:errors r)))
       (is (pos? (get-in r [:stats :nodes]))))))

#?(:clj
   (deftest test-g4-forbidden-token-caught
     (let [{:keys [nodes edges]} (graph)
           bad (assoc-in nodes ["ai.lab.openai" ":ai/score"] 9)
           r (v/verify bad edges)]
       (is (not (:ok r)))
       (is (some #(clojure.string/includes? % "G4") (:errors r))))))

#?(:clj
   (deftest test-bad-sourcing-caught
     (let [{:keys [nodes edges]} (graph)
           bad (assoc-in nodes ["ai.lab.openai" ":organism/sourcing"] ":made-up")
           r (v/verify bad edges)]
       (is (not (:ok r))))))

#?(:clj
   (deftest test-dangling-edge-caught
     (let [{:keys [nodes edges]} (graph)
           bad (conj edges {":en/from" "ghost" ":en/to" "ai.lab.openai" ":en/kind" ":invests-in"
                            ":en/grasping-load" 0.5 ":en/sourcing" ":representative"})
           r (v/verify nodes bad)]
       (is (not (:ok r)))
       (is (some #(clojure.string/includes? % "unknown node ghost") (:errors r))))))

#?(:clj
   (deftest test-load-out-of-range-caught
     (let [{:keys [nodes edges]} (graph)
           bad (conj edges {":en/from" "ai.co.nvidia" ":en/to" "ai.lab.openai" ":en/kind" ":compute-deal"
                            ":en/grasping-load" 1.5 ":en/sourcing" ":representative"})
           r (v/verify nodes bad)]
       (is (not (:ok r))))))

#?(:clj
   (deftest test-private-person-attr-caught
     (let [{:keys [nodes edges]} (graph)
           bad (assoc-in nodes ["ai.role.openai.ceo" ":person/name"] "REDACTED")
           r (v/verify nodes edges)]
       ;; reuse the same graph but inject into nodes
       (let [r2 (v/verify bad edges)]
         (is (not (:ok r2)))
         (is (some #(clojure.string/includes? % "G2") (:errors r2)))))))

#?(:clj
   (deftest test-drift-caught
     (let [{:keys [nodes edges]} (graph)
           bad (assoc-in nodes ["ai.lab.openai" ":ai/undeclared-attr"] "x")
           declared #{":organism/id" ":organism/kind" ":organism/sourcing" ":ai/open?"}
           r (v/verify bad edges declared)]
       (is (not (:ok r)))
       (is (some #(clojure.string/includes? % "DRIFT") (:errors r))))))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'chie.tests.test-verify)]
       (System/exit (if (pos? (+ (:fail r) (:error r))) 1 0)))))
