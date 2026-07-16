(ns tsumugi.methods.test-analyze-scale
  "Cross-language oracle tests for tsumugi.methods.analyze-scale — the Clojure port of
  methods/analyze_scale.py.

  No test_analyze_scale.py existed, so the expected aggregate values were produced by
  running the REAL Python analyze(load(seed)) over the committed
  seed-scale-power.kotoba.edn and embedded verbatim (628 nodes / 637 ties; top locality
  jp.aichi concentration 13.2; :national scale load 356.88; :keiretsu collective load
  636.6; vertical root org.corp.jp.7203 scale-span 3) — a genuine cross-language oracle.
  The S1/S2/S5/range validator refusals mirror the Python ValueErrors."
  (:require [clojure.test :refer [deftest is testing]]
            [tsumugi.methods.analyze-scale :as a]))

(def seed-path "20-actors/tsumugi/data/seed-scale-power.kotoba.edn")

(defn- result []
  (let [[nodes ties] (a/load-graph seed-path)] (a/analyze nodes ties)))

(defn- close? [x y] (< (Math/abs (- (double x) (double y))) 1e-6))

(deftest counts-match-the-real-seed
  (let [r (result)]
    (is (= 628 (get r "node_count")))
    (is (= 637 (get r "tie_count")))))

(deftest top-locality-is-aichi
  (let [locs (get (result) "localities")]
    (is (= 31 (count locs)))
    (is (= "jp.aichi" (get (first locs) "locality")))
    (is (close? 13.2 (get (first locs) "concentration")))
    (is (= 4 (get (first locs) "sector_diversity")))
    (is (= ["jp.aichi" "jp.nagasaki" "jp.aichi.toyota-shi"]
           (mapv #(get % "locality") (take 3 locs))))
    (is (close? 12.08 (get (nth locs 1) "concentration")))))

(deftest national-scale-dominates
  (let [scales (get (result) "scales")
        top (first scales)]
    (is (= ":national" (get top "scale")))
    (is (close? 356.88 (get top "load")))
    (is (= 594 (get top "ties")))))

(deftest top-broker-spans-three-sectors
  (let [b (first (get (result) "brokers"))]
    (is (= "org.plant.jp.toyota-aichi" (get b "id")))
    (is (= 3 (get b "span")))
    (is (close? 2.38 (get b "cross_load")))))

(deftest keiretsu-is-top-collective
  (let [ck (first (get (result) "collective_kinds"))]
    (is (= ":keiretsu" (get ck "kind")))
    (is (= 559 (get ck "node_count")))
    (is (close? 636.6 (get ck "load")))))

(deftest vertical-integration-toyota
  (let [v (get (result) "vertical")]
    (is (= 3 (count v)))
    (is (= "org.corp.jp.7203" (get (first v) "root")))
    (is (= 3 (get (first v) "scale_span")))
    (is (close? 6.68 (get (first v) "load")))))

(deftest concentration-is-rounded-to-4
  (testing "every locality concentration is a round-to-4 readout (edge-primary, on read)"
    (doseq [x (get (result) "localities")]
      (is (close? (get x "concentration")
                  (/ (Math/rint (* (get x "concentration") 10000.0)) 10000.0))))))

;; ── validator refusals (mirror the Python ValueErrors) ────────────────────────────
(def ^:private good-node
  {":pwr/id" "n1" ":pwr/standing" ":institutional" ":pwr/scale" ":national"
   ":pwr/sector" ":san" ":pwr/locality" "jp.x"})

(deftest s1-forbids-per-node-score
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"S1 breach"
                        (a/validate-node (assoc good-node ":pwr/power-score" 0.9)))))

(deftest s2-excludes-private-person
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"S2 breach"
                        (a/validate-node (assoc good-node ":pwr/standing" ":private-person")))))

(deftest s5-forbids-verdict-tie-kind
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"S5 breach"
                        (a/validate-tie {":tie/id" "t1" ":tie/kind" ":collusion"
                                         ":tie/sources" ["a" "b"] ":tie/grasping-load" 0.5}))))

(deftest tie-grasping-load-must-be-in-range
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"range breach"
                        (a/validate-tie {":tie/id" "t2" ":tie/kind" ":funds"
                                         ":tie/sources" ["a" "b"] ":tie/grasping-load" 1.5}))))

(deftest tie-needs-two-sources
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"S4 breach"
                        (a/validate-tie {":tie/id" "t3" ":tie/kind" ":funds"
                                         ":tie/sources" ["only-one"] ":tie/grasping-load" 0.5}))))
