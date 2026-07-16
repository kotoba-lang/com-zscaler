(ns hakoniwa.tests.test-simulate
  "hakoniwa 箱庭 — world-load + simulation-kernel tests (ADR-2606111500).
  1:1 Clojure port of tests/test_simulate.py. Pure stdlib, network-free, deterministic.

  Verifies the constitutional invariants empirically:
    - the scenario loads (synthetic personas + 縁), is non-trivial, no dangling 縁
    - G1: every persona is :persona/synthetic true, no PII-class field; a non-synthetic or
      PII-bearing persona is REFUSED at load (assert-synthetic raises)
    - the Friedkin-Johnsen kernel converges and stays in [0,1]
    - row-normalised incoming :influences weights
    - determinism: identical (seed, steps, replicas) → byte-identical ensemble
    - a stronger official-relay signal raises the town-wide mean (mechanism sanity)
    - the ensemble actually has spread (a distribution, not a degenerate point)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.java.io :as io]
            [hakoniwa.methods.world :as world]
            [hakoniwa.methods.simulate :as simulate]))

(def scenario
  (-> (io/file *file*) .getParentFile .getParentFile
      (io/file "data" "seed-scenario.kotoba.edn") str))

(deftest test-load-nontrivial-and-synthetic
  (let [{:keys [nodes edges]} (world/load-file* scenario)
        P (world/personas nodes)]
    (is (>= (count P) 12) (str "expected a real persona ensemble, got " (count P)))
    (is (>= (count edges) 30) (str "expected a real 縁 web, got " (count edges)))
    (doseq [nid (world/ordered-keys P)]
      (is (true? (get (get P nid) ":persona/synthetic")) (str "persona " nid " not marked synthetic (G1)")))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(defn- read-nodes
  "Parse the test EDN form into a {:sim/id node} map (mirrors the Python test harness)."
  [text]
  (reduce (fn [m f] (if (map? f) (assoc m (get f ":sim/id") f) m))
          {} (filter map? (world/read-edn text))))

(deftest test-g1-refuses-real-person
  ;; G1: a persona missing :persona/synthetic, or carrying PII, MUST be refused at load.
  (let [base (fn [extra] (str "[{:sim/id \"persona.x\" :sim/kind :persona :sim/label \"x\" " extra "}]"))]
    ;; missing synthetic marker
    (let [nodes (read-nodes (base ""))
          raised (try (world/assert-synthetic nodes) false (catch clojure.lang.ExceptionInfo _ true))]
      (is raised "load accepted a persona with no :persona/synthetic marker (G1 breach)"))
    ;; PII-bearing persona, even if marked synthetic
    (let [nodes2 (read-nodes (base ":persona/synthetic true :email \"a@b.c\""))
          raised (try (world/assert-synthetic nodes2) false (catch clojure.lang.ExceptionInfo _ true))]
      (is raised "load accepted a PII-bearing persona (G1 breach)"))))

(deftest test-kernel-converges-in-unit-interval
  (let [{:keys [nodes edges]} (world/load-file* scenario)
        {:keys [pids sus base-anchor incoming exposure]} (simulate/build-topology nodes edges)
        x (simulate/run-replica pids sus base-anchor incoming exposure 12 7 0 0.0)]
    (is (= (set (keys x)) (set pids)))
    (doseq [[i v] x]
      (is (and (<= 0.0 v) (<= v 1.0)) (str "stance for " i " left [0,1]: " v)))))

(deftest test-row-normalised-influence
  ;; Incoming :influences weights MUST row-normalise to 1 (or be empty → fully anchored).
  (let [{:keys [nodes edges]} (world/load-file* scenario)
        {:keys [incoming]} (simulate/build-topology nodes edges)]
    (doseq [[i lst] incoming]
      (when (seq lst)
        (is (< (Math/abs (- (reduce + 0.0 (map second lst)) 1.0)) 1e-9)
            (str i " incoming weights not normalised"))))))

(deftest test-determinism
  (let [{n1 :nodes e1 :edges} (world/load-file* scenario)
        [a ma] (simulate/ensemble n1 e1 {:steps 10 :replicas 32 :seed 3})
        {n2 :nodes e2 :edges} (world/load-file* scenario)
        [b mb] (simulate/ensemble n2 e2 {:steps 10 :replicas 32 :seed 3})]
    (is (= a b) "ensemble is not deterministic for fixed (seed, steps, replicas)")
    (is (= ma mb))))

(deftest test-stronger-relay-raises-mean
  ;; Mechanism sanity: a stronger official-relay push shifts the distribution upward.
  (let [{:keys [nodes edges]} (world/load-file* scenario)
        [base _] (simulate/ensemble nodes edges {:steps 12 :replicas 48 :seed 7})
        ;; strengthen signal.s1's push (the sonae-style authoritative relay).
        ;; assoc preserves the nodes map's ::node-order / ::order metadata.
        nodes* (assoc nodes "signal.s1" (assoc (get nodes "signal.s1") ":signal/push" 0.40))
        [boosted _] (simulate/ensemble nodes* edges {:steps 12 :replicas 48 :seed 7})]
    (is (> (/ (reduce + 0.0 boosted) (count boosted))
           (+ (/ (reduce + 0.0 base) (count base)) 1e-3))
        "a stronger preparedness relay did not raise town-wide adoption stance")))

(deftest test-ensemble-has-spread
  ;; The ensemble must actually be a distribution (replicas differ), not a degenerate point.
  (let [{:keys [nodes edges]} (world/load-file* scenario)
        [results _] (simulate/ensemble nodes edges {:steps 12 :replicas 64 :seed 7})]
    (is (> (- (apply max results) (apply min results)) 1e-4)
        "ensemble collapsed to a point (G2 needs spread)")))

#?(:clj (defn -main [& _] (run-tests 'hakoniwa.tests.test-simulate)))
