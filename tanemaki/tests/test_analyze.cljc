(ns tanemaki.tests.test-analyze
  "tanemaki 種蒔き — analyzer + steward-boundary tests (ADR-2606122001).
  1:1 Clojure port of tests/test_analyze.py.

  Verifies the constitutional invariants empirically:
    - graph loads (nodes + 縁), no dangling 縁
    - G1 (the defining boundary): NO screen-conflicting org ever routes to :propose; there is
      no :fund route at all — funding is the members' vote, not a tanemaki output
    - G4: the disclosed rubric weights sum to 1.0 (a skewed rubric throws)
    - G5: thin evidence / undetermined screens route to :insufficient-evidence, never :propose
    - N1 edge-primary: dd-fit is the integral of incident :meets edges; no stored org score
    - G6: every seed org is synthetic (fictional)
    - all three routes are exercised by the seed
    - screens carry a disclosed charter basis + code

  NOTE on scope: the Python test_analyze additionally exercises the `datom_emit` sibling
  (test_datom_emit_ground_and_transient + test_determinism). Those two assertions depend on
  the unported `datom_emit` module, so they are intentionally omitted here (the datom_emit
  port is a separate unit, mirroring the inochi/rasen precedent). All PURE analyze assertions
  are ported 1:1."
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [clojure.java.io :as io]
            [tanemaki.methods.analyze :as analyze]))

(def actor-dir (-> *file* io/file .getParentFile .getParentFile))
(def seed (io/file actor-dir "data" "seed-stewardship-graph.kotoba.edn"))

(defn load-seed [] (analyze/load-file* seed))

(deftest test-load-nontrivial
  (let [{:keys [nodes edges]} (load-seed)]
    (is (and (>= (count nodes) 30) (>= (count edges) 60))
        (str (count nodes) " nodes / " (count edges) " 縁"))
    (let [kinds (set (map #(get % ":fs/kind") (vals nodes)))]
      (is (clojure.set/subset?
           #{":org" ":screen" ":criterion" ":source" ":instrument" ":milestone"} kinds)))
    (doseq [e edges]
      (is (and (contains? nodes (get e ":en/from")) (contains? nodes (get e ":en/to")))
          (str "dangling 縁: " e)))))

(deftest test-g1-no-conflicted-org-is-proposable
  (testing "THE defining boundary: a screen :conflicts ⇒ :excluded, never :propose."
    (let [{:keys [nodes edges]} (load-seed)]
      (doseq [[nid n] nodes
              :when (= ":org" (get n ":fs/kind"))]
        (let [rec (analyze/recommend-route nid nodes edges)]
          (when (seq (get rec "conflicts"))
            (is (= ":excluded" (get rec "route"))
                (str "G1 VIOLATION: " nid " has conflicts " (get rec "conflicts")
                     " but routed " (get rec "route")))))))))

(deftest test-g1-no-fund-route-exists
  (testing "tanemaki cannot emit a funding decision — :fund is not a route."
    (is (and (not (some #{":fund"} analyze/ROUTES))
             (= (set analyze/ROUTES) #{":excluded" ":insufficient-evidence" ":propose"})))
    (let [{:keys [nodes edges]} (load-seed)]
      (doseq [[nid n] nodes
              :when (= ":org" (get n ":fs/kind"))]
        (is (some #{(get (analyze/recommend-route nid nodes edges) "route")} analyze/ROUTES))))))

(deftest test-g4-rubric-weights-sum-to-one
  (let [{:keys [nodes]} (load-seed)
        crit (analyze/criteria nodes)] ;; throws on a skewed rubric
    (is (< (Math/abs (- (reduce + 0.0 (map #(double (get % ":criterion/weight"))
                                           (vals crit))) 1.0)) 1e-9))
    ;; and a tampered rubric throws (rubric integrity is enforced, not assumed)
    (let [cid (first (or (:tanemaki.methods.analyze/order (meta crit)) (keys crit)))
          tampered (assoc nodes cid (assoc (get nodes cid) ":criterion/weight" 0.99))]
      (is (thrown? clojure.lang.ExceptionInfo (analyze/criteria tampered))))))

(deftest test-g5-thin-evidence-never-proposes
  (let [{:keys [nodes edges]} (load-seed)]
    (doseq [[nid n] nodes
            :when (= ":org" (get n ":fs/kind"))]
      (let [rec (analyze/recommend-route nid nodes edges)]
        (when (= ":propose" (get rec "route"))
          (is (>= (get rec "evidence_coverage") analyze/COVERAGE-FLOOR)
              (str nid " proposed below the evidence floor"))
          (is (and (empty? (get rec "undetermined")) (empty? (get rec "conflicts"))))
          (is (every? #(= ":conforms" %) (vals (get rec "screen_findings")))
              (str nid " proposed without clearing every screen")))))))

(deftest test-seed-exercises-all-three-routes
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        routes (set (map #(get % "route") (vals (get res "orgs"))))]
    (is (= routes #{":excluded" ":insufficient-evidence" ":propose"}) (str routes))))

(deftest test-edge-primary-no-stored-score
  (let [{:keys [nodes edges]} (load-seed)
        res (analyze/analyze nodes edges)
        crit (analyze/criteria nodes)]
    ;; dd-fit equals the hand-computed integral of incident :meets edges
    (doseq [[oid r] (get res "orgs")]
      (let [per (reduce
                 (fn [m e]
                   (if (and (= ":meets" (get e ":en/kind"))
                            (= oid (get e ":en/from"))
                            (contains? crit (get e ":en/to")))
                     (update m (get e ":en/to") (fnil + 0.0) (double (get e ":en/weight")))
                     m))
                 {} edges)
            expect (reduce + 0.0
                           (for [[c w] per]
                             (* (double (get-in crit [c ":criterion/weight"])) (min 1.0 w))))
            round6 (fn [v] (/ (Math/rint (* (double v) 1000000.0)) 1000000.0))]
        (is (< (Math/abs (- (get r "dd_fit") (round6 expect))) 1e-9) (str oid))))
    ;; no stored per-org score on any node (edge-primary only)
    (doseq [n (vals nodes)]
      (is (not (some #(str/starts-with? % ":bond/") (keys n)))))))

(deftest test-g6-every-seed-org-is-synthetic
  (let [{:keys [nodes]} (load-seed)]
    (doseq [[nid n] nodes
            :when (= ":org" (get n ":fs/kind"))]
      (is (true? (get n ":org/synthetic"))
          (str "G6 VIOLATION: " nid " is not marked synthetic — a real org in the seed is "
               "reputational adjudication (real-org DD is G7-gated)")))))

(deftest test-screens-carry-disclosed-basis
  (let [{:keys [nodes]} (load-seed)]
    (doseq [[nid n] nodes
            :when (= ":screen" (get n ":fs/kind"))]
      (is (get n ":screen/basis") (str nid " missing its disclosed charter anchor"))
      (is (get n ":screen/code") (str nid " missing code")))))
