(ns tsumugi.methods.test-resolve
  "Cross-language oracle tests for tsumugi.methods.resolve — the Clojure port of
  methods/resolve.py (the pure noisy-OR latent-entity resolver).

  No test_resolve.py existed, so the expected values were produced by running the REAL
  Python resolve_latent_entities / to_edn on a synthetic orgs+edges set and embedded
  verbatim — a genuine cross-language oracle. Pins: only latent (unclaimed / :latent)
  orgs resolved, existence = noisy-OR over :evidence edges (round-4), the frontier
  classification, and aggregate-first method-versioning (G2/N1)."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.resolve :as r]))

(def orgs
  {":org.a" {":organism/claimed?" false}
   ":org.b" {":organism/standing" ":latent"}
   ":org.c" {":organism/claimed?" true ":organism/standing" ":member"}})
(def edges
  [{":en/kind" ":evidence" ":en/to" ":org.a" ":en/evidence-kind" ":labor" ":en/evidence-weight" 0.8}
   {":en/kind" ":evidence" ":en/to" ":org.a" ":en/evidence-kind" ":ecology" ":en/evidence-weight" 0.6}
   {":en/kind" ":evidence" ":en/to" ":org.b" ":en/evidence-kind" ":labor" ":en/evidence-weight" 0.3}
   {":en/kind" ":custodies" ":en/to" ":org.a"}])

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-9))

(deftest only-latent-orgs-resolved
  (let [ds (r/resolve-latent-entities orgs edges)]
    (is (= 2 (count ds)))                                  ; org.c (claimed member) excluded
    (is (= [":org.a" ":org.b"] (mapv #(get % ":latent/organism") ds)))))  ; sorted

(deftest noisy-or-existence-and-fission-ready
  (let [a (first (r/resolve-latent-entities orgs edges))]
    ;; 1 - (1-0.8)(1-0.6) = 1 - 0.08 = 0.92
    (is (close? 0.92 (get a ":latent/existence")))
    (is (= 2 (get a ":latent/evidence-count")))
    (is (= 2 (get a ":latent/viewpoint-consensus")))       ; labor + ecology
    (is (= ":fission-ready" (get a ":latent/frontier")))   ; ≥0.7 ∧ consensus≥2 ∧ k≥2
    (is (= "latent-resolve/v1-noisy-or" (get a ":latent/method-version")))))

(deftest weak-single-evidence-is-observed
  (let [b (second (r/resolve-latent-entities orgs edges))]
    (is (close? 0.3 (get b ":latent/existence")))
    (is (= 1 (get b ":latent/evidence-count")))
    (is (= ":observed" (get b ":latent/frontier")))))      ; 0.3 < 0.4 → not even :candidate

(deftest no-evidence-is-zero-observed
  (let [ds (r/resolve-latent-entities {":org.x" {":organism/claimed?" false}} [])
        x (first ds)]
    (is (= 0.0 (get x ":latent/existence")))
    (is (= 0 (get x ":latent/evidence-count")))
    (is (= 0 (get x ":latent/viewpoint-consensus")))
    (is (= ":observed" (get x ":latent/frontier")))))

(deftest candidate-frontier-mid-existence
  ;; single 0.5 evidence → existence 0.5, k 1 → :candidate (≥0.4 ∧ k≥1, but not fission-ready)
  (let [ds (r/resolve-latent-entities {":org.m" {":organism/standing" ":latent"}}
                                      [{":en/kind" ":evidence" ":en/to" ":org.m"
                                        ":en/evidence-kind" ":labor" ":en/evidence-weight" 0.5}])
        m (first ds)]
    (is (close? 0.5 (get m ":latent/existence")))
    (is (= ":candidate" (get m ":latent/frontier")))))

(deftest to-edn-shape
  (let [edn (r/to-edn (r/resolve-latent-entities orgs edges))]
    (is (clojure.string/includes? edn "latent-entity frontier"))
    (is (clojure.string/includes? edn ":latent/organism :org.a"))
    (is (clojure.string/includes? edn ":latent/frontier :fission-ready"))
    (is (clojure.string/includes? edn ":latent/method-version \"latent-resolve/v1-noisy-or\""))))
