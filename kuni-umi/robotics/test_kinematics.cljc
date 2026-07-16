(ns kuni-umi.robotics.test-kinematics
  "kuni-umi 国産み planar-arm kinematics — cljc port conformance + the FK∘IK
  round-trip invariant. Oracle values are committed fixtures preserved from the
  port wave."
  (:require [clojure.test :refer [deftest is testing]]
            [kuni-umi.robotics.kinematics :as k]))

(def ^:private arm (k/planar-arm [1.0 0.8]))

(deftest reach-bounds
  (is (= 1.8 (k/max-reach arm)))
  ;; longest 1.0 − rest 0.8 = 0.19999999999999996 (float64; python identical)
  (is (< (Math/abs (- 0.2 (k/min-reach arm))) 1e-9))
  (is (= 0.0 (k/min-reach (k/planar-arm [1.0 1.0]))))  ;; equal links fold to 0
  (is (true? (k/reachable arm 1.2 0.4)))
  (is (false? (k/reachable arm 2.0 0.0)))      ;; beyond max reach
  (is (false? (k/reachable arm 0.05 0.0))))    ;; inside min reach

(deftest fk-matches-python-oracle
  (let [p (k/fk arm [0.5 0.3])]
    (is (= 1.434947929 (:x p)))
    (is (= 1.053310411 (:y p)))
    (is (= 0.8 (:theta p))))                    ;; theta = Σ joint angles
  ;; joint-count mismatch raises
  (is (thrown? clojure.lang.ExceptionInfo (k/fk arm [0.5]))))

(deftest ik2-matches-python-oracle
  (let [[q0 q1] (k/ik2 arm 1.2 0.4 true)]
    (is (= 1.006214589 q0))
    (is (= -1.595798932 q1)))
  (let [[q0 q1] (k/ik2 arm 1.2 0.4 false)]
    (is (= -0.362713480 q0))
    (is (= 1.595798932 q1)))                    ;; mirror solution
  (is (nil? (k/ik2 arm 5.0 5.0)))               ;; unreachable
  (is (thrown? clojure.lang.ExceptionInfo (k/ik2 (k/planar-arm [1.0 0.8 0.5]) 1.0 0.0))))

(deftest fk-of-ik-returns-the-target
  ;; the closed-form IK must invert FK: fk(ik2(x,y)) ≈ (x,y) for reachable targets
  (doseq [[x y] [[1.2 0.4] [0.5 0.9] [-0.6 0.7] [1.5 -0.2]]]
    (testing (str "target " x "," y)
      (when (k/reachable arm x y)
        (doseq [elbow [true false]]
          (let [sol (k/ik2 arm x y elbow)
                p   (k/fk arm sol)]
            (is (< (Math/abs (- (:x p) (double x))) 1e-7))
            (is (< (Math/abs (- (:y p) (double y))) 1e-7))))))))

(deftest joint-trajectory-interpolates
  (is (= [[0.0 0.0] [0.25 0.125] [0.5 0.25] [0.75 0.375] [1.0 0.5]]
         (k/joint-trajectory [0.0 0.0] [1.0 0.5] 4)))
  (is (= 6 (count (k/joint-trajectory [0.0] [1.0] 5))))   ;; steps+1 configs
  (is (thrown? clojure.lang.ExceptionInfo (k/joint-trajectory [0.0] [1.0 2.0] 4)))  ;; unequal
  (is (thrown? clojure.lang.ExceptionInfo (k/joint-trajectory [0.0] [1.0] 0))))      ;; steps<1
