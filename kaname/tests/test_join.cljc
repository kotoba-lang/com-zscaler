(ns kaname.tests.test-join
  "kaname 要 — live mirror JOIN tests (ADR-2606172100 R1). Parse a mirror's [e a v tx op] Datom
  log, lift it into a domain layer, and reconcile across layers by label — the system-of-systems
  join. Uses a synthetic fixture (deterministic; no dependency on a sibling actor's output)."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            #?(:clj [clojure.java.io :as io])
            [kaname.methods.sos :as sos]
            [kaname.methods.join :as join]))

#?(:clj (def actor-dir (-> *file* io/file .getParentFile .getParentFile)))
#?(:clj (def fixture (io/file actor-dir "data" "fixture-mirror-datoms.kotoba.edn")))

(deftest test-datoms->graph
  (testing "ground :add datoms reconstruct nodes + edges; :derived ignored"
    (let [g (join/read-datom-log fixture)]
      (is (= 3 (count (:nodes g))))                 ; openai + nvidia + chair
      (is (contains? (:nodes g) "m.openai"))
      (is (= "OpenAI" (get-in g [:nodes "m.openai" ":organism/label"])))
      ;; both 縁 entities reconstruct (compute-deal + partners), no :derived leaks as a node
      (is (= 2 (count (:edges g))))
      (is (not (contains? (:nodes g) "en.m.nvidia.compute-deal.m.openai"))))))

(deftest test-lift-into-layer
  (testing "lift maps kinds, assigns the domain, namespaces ids, drops unmapped 縁"
    (let [g (join/read-datom-log fixture)
          forms (join/lift g ":ai" ":chie")
          nodes (filter #(contains? % ":organism/id") forms)
          edges (filter #(contains? % ":en/from") forms)]
      ;; nodes namespaced + classified
      (is (every? #(clojure.string/starts-with? (get % ":organism/id") "chie/") nodes))
      (is (some #(= ":sos/role" (get % ":organism/kind")) nodes))   ; the public chair role
      (is (every? #(= [":chie"] (get % ":sos/source-actors")) nodes))
      ;; compute-deal → :concentrates in :ai ; partners (unmapped) DROPPED
      (is (= 1 (count edges)))
      (is (= ":concentrates" (get (first edges) ":en/kind")))
      (is (= ":ai" (get (first edges) ":en/domain"))))))

(deftest test-reconcile-spans-domains
  (testing "an entity present in TWO layers reconciles to one node spanning BOTH domains (V grows)"
    (let [;; layer 1 (:ai): NVIDIA ← OpenAI
          ai   [{":organism/id" "a/nvidia" ":organism/label" "NVIDIA"  ":sos/source-actors" [":chie"]}
                {":organism/id" "a/openai" ":organism/label" "OpenAI"  ":sos/source-actors" [":chie"]}
                {":en/from" "a/openai" ":en/to" "a/nvidia" ":en/kind" ":concentrates" ":en/domain" ":ai" ":en/grasping-load" 0.5}]
          ;; layer 2 (:economy): NVIDIA ← Grid (same NVIDIA by label)
          econ [{":organism/id" "e/nvidia" ":organism/label" "NVIDIA"  ":sos/source-actors" [":kabuto"]}
                {":organism/id" "e/grid"   ":organism/label" "Grid"    ":sos/source-actors" [":kabuto"]}
                {":en/from" "e/grid" ":en/to" "e/nvidia" ":en/kind" ":concentrates" ":en/domain" ":economy" ":en/grasping-load" 0.6}]
          recon (join/reconcile-by-label (concat ai econ))
          {:keys [nodes edges]} (join/forms->graph recon)
          res (sos/leverage nodes edges)
          ;; canonical NVIDIA id = lowest of {a/nvidia, e/nvidia} = "a/nvidia"
          cid "a/nvidia"]
      (is (contains? nodes cid))
      (testing "source-actors unioned across mirrors"
        (is (= [":chie" ":kabuto"] (get-in nodes [cid ":sos/source-actors"]))))
      (testing "the reconciled entity now bears load in BOTH domains → versatility 2"
        (is (= #{":ai" ":economy"} (get-in res [:domains cid])))
        (is (= 2 (get-in res [:V cid])))))))

(deftest test-no-fabricated-axis
  (testing "edges with an unmapped 縁-kind are dropped, never coerced into a fake axis"
    (let [g (join/read-datom-log fixture)
          forms (join/lift g ":ai" ":chie")
          edges (filter #(contains? % ":en/from") forms)]
      (is (not-any? #(= ":partners" (get % ":en/kind")) edges)))))

(deftest test-auto-detect-format
  (testing "parse-graph dispatches: forms-graph (maps) vs Datom log (5-vectors)"
    (let [forms-g (join/parse-graph [{":organism/id" "x" ":organism/kind" ":sos/entity"}
                                     {":en/from" "x" ":en/to" "y" ":en/kind" ":concentrates"}])
          datom-g (join/parse-graph [["x" ":organism/kind" ":sos/entity" 1 ":add"]])]
      (is (contains? (:nodes forms-g) "x"))
      (is (= 1 (count (:edges forms-g))))
      (is (contains? (:nodes datom-g) "x")))))

;; Integration — REAL multi-mirror join. Guarded: verifies only when the sibling mirror outputs are
;; present on disk (they are committed in this repo); skips gracefully elsewhere (never breaks CI).
#?(:clj
   (deftest test-real-multi-mirror-join
     (let [base (io/file actor-dir ".." )
           chie (io/file base "chie" "out" "ai-ecosystem-datoms.kotoba.edn")
           tsum (io/file base "tsumugi" "out" "woven-graph.kotoba.edn")]
       (if (and (.exists chie) (.exists tsum))
         (let [{:keys [graph loaded]} (join/join-mirrors base)
               {:keys [nodes edges]} graph
               res (sos/leverage nodes edges)
               cross (->> (:V res) (filter (fn [[_ v]] (>= v 2))) (map first))]
           (testing "≥2 mirrors load + a multilayer graph is produced"
             (is (>= (count loaded) 2))
             (is (pos? (count nodes)))
             (is (>= (count (distinct (keep #(get % ":en/domain") edges))) 2)))
           (testing "reconcile surfaces a cross-domain entity sourced from ≥2 mirrors (the SoS payoff)"
             (is (seq cross))
             (is (some (fn [nid]
                         (>= (count (get-in nodes [nid ":sos/source-actors"])) 2))
                       cross))))
         (testing "(skipped — sibling mirror outputs not present in this checkout)"
           (is true))))))
