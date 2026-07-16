(ns silicon.methods.test-wafer-handler
  "Tests for silicon.methods.wafer-handler."
  (:require [clojure.test :refer [deftest is]]
            [silicon.methods.wafer-handler :as wh]))

(deftest test-move-time-monotonic-in-distance
  (let [a (wh/move-time {:dist 0.5})
        b (wh/move-time {:dist 1.5})
        c (wh/move-time {:dist 6.0})]
    (is (< a b))
    (is (< b c))
    (is (pos? a))))

(deftest test-short-move-is-triangular
  ;; a very short move never reaches vmax; still positive, includes settle
  (let [t (wh/move-time {:dist 0.01 :vmax 3.14 :acc 12.0 :settle 0.15})]
    (is (> t 0.15))
    (is (< t 0.5))))

(deftest test-transfer-time-positive
  (is (pos? (wh/transfer-time {})))
  ;; bigger rotation costs more
  (is (< (wh/transfer-time {:swap-dist 0.5})
         (wh/transfer-time {:swap-dist 3.0}))))

(deftest test-loadlock-cycle
  ;; lower base pressure (deeper vacuum) takes longer to pump
  (is (< (wh/loadlock-cycle {:base-pa 10.0})
         (wh/loadlock-cycle {:base-pa 0.1}))))

(deftest test-route-cycle-time
  (let [t (wh/route-cycle-time [60.0 120.0 90.0])]
    ;; ≥ sum of process times (240) plus transfers
    (is (> t 240.0))))

(deftest test-throughput-bottleneck-bound
  (let [proc [60.0 120.0 90.0]
        out (wh/throughput-wph proc :slots 25)]
    (is (pos? (:wph out)))
    (is (pos? (:foup-time-min out)))
    ;; bottleneck = max process + one transfer
    (is (> (:bottleneck-s out) 120.0))
    ;; more wafers in the FOUP → higher steady-state throughput (amortized loadlock)
    (let [few (wh/throughput-wph proc :slots 5)
          many (wh/throughput-wph proc :slots 50)]
      (is (> (:wph many) (:wph few))))))

(deftest test-schedule-feasible
  (is (wh/schedule-feasible? [60.0 120.0] (wh/transfer-time {}))))

(deftest test-scara-fk-known-pose
  ;; θ1=0, θ2=0 → arm fully extended along +x at l1+l2
  (let [arm {:l1 0.4 :l2 0.35 :theta1 0.0 :theta2 0.0}
        p (wh/scara-fk arm)]
    (is (= 0.75 (:x p)))
    (is (= 0.0 (:y p)))))

(deftest test-scara-reachability
  (let [arm {:l1 0.4 :l2 0.35}]
    (is (wh/scara-reachable? arm 0.5 0.2))    ; inside annulus
    (is (not (wh/scara-reachable? arm 2.0 0.0)))  ; beyond l1+l2
    (is (not (wh/scara-reachable? arm 0.0 0.0))))) ; inside inner hole (|l1-l2|=0.05)

(deftest test-scara-fk-ik-roundtrip
  ;; FK(IK(target)) ≈ target for a reachable point
  (let [arm {:l1 0.4 :l2 0.35}
        tx 0.5 ty 0.2
        {:keys [theta1 theta2]} (wh/scara-ik arm tx ty)
        p (wh/scara-fk (assoc arm :theta1 theta1 :theta2 theta2))]
    (is (< (Math/abs (- (:x p) tx)) 0.01))
    (is (< (Math/abs (- (:y p) ty)) 0.01))))

(deftest test-scara-ik-unreachable-nil
  (is (nil? (wh/scara-ik {:l1 0.4 :l2 0.35} 5.0 0.0))))

(deftest test-station-reachable
  (let [arm {:l1 0.4 :l2 0.35}]
    (is (wh/station-reachable? arm [{:x 0.5 :y 0.0} {:x 0.4 :y 0.3} {:x 0.0 :y 0.6}]))
    (is (not (wh/station-reachable? arm [{:x 0.5 :y 0.0} {:x 9.0 :y 0.0}])))))
