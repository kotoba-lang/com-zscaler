(ns masago.methods.test-analyze
  "masago 真砂 — analyzer tests (ADR-2606151027). Clojure / kotoba-datomic native.

  Verifies the constitutional invariants empirically:
    - the seed parses + classifies into nodes (5 kinds) + edges, no dangling 縁
    - edge-primary (N1): material discovery-priority equals the independent integral of incident
      :has-property + :candidate-for edges × disclosed confidence weight (recomputed here)
    - the top discovery node is a :material that carries application candidacy (research-routed)
    - application readiness is non-empty and every bearer is an :application
    - G1: screen RAISES on a synthesis-route field, and on a weaponizable :application/class —
      fabrication + force are STRUCTURALLY EXCLUDED (unrepresentable), never silently rendered
    - G4: screen RAISES on a non-open dataset-source license
    - N3: the computed value rides the edge (:en/value on :has-property), never a node score
    - the Datom log emits ground [e a v tx :add] and flags derived readouts transient (N1/G2)
    - determinism (two runs byte-identical)
    - the report + coverage render with the invariant notes + honest denominator"
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [masago.methods.analyze :as A]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-open-materials-graph.kotoba.edn"))

(defn- load- [] (A/classify (A/load-edn seed)))

(deftest test-seed-parses-and-classifies
  (let [[nodes edges] (load-)]
    (is (>= (count nodes) 40) (str "expected a real seed, got " (count nodes) " nodes"))
    (is (>= (count edges) 50) (str "expected a real 縁 web, got " (count edges) " edges"))
    (let [kinds (set (map #(get % ":mat/kind") (vals nodes)))]
      (is (every? kinds [":material" ":element" ":property" ":application" ":dataset-source"])
          (str "missing core kinds: " kinds)))
    (doseq [e edges]
      (is (contains? nodes (get e ":en/from")) (str "dangling from: " (get e ":en/from")))
      (is (contains? nodes (get e ":en/to")) (str "dangling to: " (get e ":en/to"))))))

(deftest test-edge-primary-integral
  (testing "N1: discovery-priority MUST equal the independent integral of incident evidence 縁."
    (let [[nodes edges] (load-)
          a (A/analyze nodes edges)
          expect (reduce (fn [m e]
                           (let [k (get e ":en/kind")]
                             (if (or (= k ":has-property") (= k ":candidate-for"))
                               (let [w (get A/confidence-weight (get e ":en/confidence") A/default-conf)
                                     load- (double (get e ":en/grasping-load"))]
                                 (update m (get e ":en/from") (fnil + 0.0) (* load- w)))
                               m)))
                         {} edges)]
      (doseq [[nid v] expect]
        (is (< (Math/abs (- (get-in a [:discovery nid]) v)) 1e-9)
            (str nid ": " (get-in a [:discovery nid]) " != " v)))
      ;; no stored per-node score key on any node (edge-primary only)
      (doseq [n (vals nodes)]
        (is (not-any? #(or (str/starts-with? % ":bond/") (= % ":material/score")) (keys n)))))))

(deftest test-discovery-top-is-material
  (let [[nodes edges] (load-)
        a (A/analyze nodes edges)
        top (first (sort-by (fn [[_ v]] (- (double v))) (:discovery a)))
        tid (first top)]
    (is (= ":material" (get-in nodes [tid ":mat/kind"])) (str "top discovery node " tid " is not a material"))
    (is (seq (filter #(and (= tid (get % ":en/from")) (= ":candidate-for" (get % ":en/kind"))) edges))
        (str "top material " tid " has no application candidacy"))))

(deftest test-readiness-nonempty-and-application
  (let [[nodes edges] (load-)
        a (A/analyze nodes edges)]
    (is (seq (:readiness a)) "no application readiness computed")
    (doseq [nid (keys (:readiness a))]
      (is (= ":application" (get-in nodes [nid ":mat/kind"])) (str "readiness-bearer " nid " is not an application")))))

(deftest test-g1-screen-rejects-synthesis-route
  (testing "G1: a synthesis-route field must raise, never render."
    (let [bad {"mat.x" {":mat/id" "mat.x" ":mat/kind" ":material" ":synthesis/route" "do not store"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation" (A/screen bad []))))))

(deftest test-g1-screen-rejects-weapon-application
  (testing "G1: a weaponizable application class must raise (force unrepresentable)."
    (let [bad {"app.x" {":mat/id" "app.x" ":mat/kind" ":application" ":application/class" ":explosive"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1 violation" (A/screen bad []))))))

(deftest test-g4-screen-rejects-non-open-license
  (testing "G4: a non-open dataset-source license must raise."
    (let [bad {"src.x" {":mat/id" "src.x" ":mat/kind" ":dataset-source" ":source/license" "proprietary"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G4 violation" (A/screen bad []))))))

(deftest test-seed-is-charter-clean
  (testing "the committed seed passes the G1/G4 screen and every source is open-licensed."
    (let [[nodes edges] (load-)]
      (is (map? (A/screen nodes edges)) "seed must pass screen (returns confidence breakdown)")
      (doseq [n (vals nodes)]
        (when (= ":dataset-source" (get n ":mat/kind"))
          (is (#'A/open-license? (get n ":source/license")) (get n ":mat/id"))))
      (doseq [n (vals nodes)]
        (when (= ":application" (get n ":mat/kind"))
          (is (not (A/forbidden-app-classes (get n ":application/class"))) (get n ":mat/id")))))))

(deftest test-disclosed-value-rides-edge-not-node
  (testing "N3: the computed VALUE is a disclosed edge fact (:en/value on :has-property), never a node score."
    (let [[nodes edges] (load-)
          has-prop (filter #(= ":has-property" (get % ":en/kind")) edges)]
      (is (seq has-prop) "no :has-property edges")
      (doseq [e has-prop] (is (contains? e ":en/value") (str "a :has-property edge missing :en/value")))
      (doseq [n (vals nodes)] (is (not (contains? n ":en/value")))))))

(deftest test-datoms-ground-and-transient
  (let [[nodes edges] (load-)
        a (A/analyze nodes edges)
        out (A/render-datoms nodes edges a 7)]
    (is (str/includes? out ":add]") "no ground :add datoms emitted")
    (is (str/includes? out ":material/formula") "node attribute datoms missing")
    (is (str/includes? out ":en/grasping-load") "edge attribute datoms missing")
    (is (str/includes? out ":en/confidence") "confidence edge datoms missing")
    (is (str/includes? out ":bond/is-transient true"))
    (is (str/includes? out ":bond/discovery-priority"))
    (is (str/includes? out " 7 :add]"))
    (doseq [line (str/split-lines out)]
      (when (and (str/starts-with? line "[") (str/includes? line ":bond/"))
        (is (str/includes? line ":derived]") (str "derived readout not flagged transient: " line))))))

(deftest test-determinism
  (let [[n1 e1] (load-)
        [n2 e2] (load-)]
    (is (= (A/render-datoms n1 e1 (A/analyze n1 e1) 1)
           (A/render-datoms n2 e2 (A/analyze n2 e2) 1))
        "Datom render is not deterministic")))

(deftest test-report-and-coverage-render
  (let [[nodes edges] (load-)
        a (A/analyze nodes edges)
        report (A/render-report nodes edges a)
        cov (A/render-coverage nodes edges)]
    (is (str/starts-with? report "# masago"))
    (is (str/includes? report "RESEARCH map"))             ; G1 framing surfaced
    (is (str/includes? report "discovery-priority"))
    (is (str/includes? cov "coverage of the full ~10^8 open-materials commons is ~0 by design"))  ; honest denom
    (is (str/includes? cov "no synthesis routes"))         ; G1 guard surfaced
    (is (str/includes? cov "Gap map"))))

(deftest test-sources-include-omat24-and-materials-project
  (let [[nodes _] (load-)
        ids (set (keep #(when (= ":dataset-source" (get % ":mat/kind")) (get % ":mat/id")) (vals nodes)))]
    (is (contains? ids "src.omat24") "OMat24 source missing (the motivating dataset)")
    (is (contains? ids "src.materials-project") "Materials Project source missing")))
