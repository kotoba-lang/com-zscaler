(ns kuni-umi.robotics.kinematics
  "kinematics — install-robot motion model (planar serial arm FK/IK + trajectory).
  1:1 cljc port of `robotics/kinematics.py`.

  The :representative motion layer for the install/service fleet (Otete arm and
  siblings). A planar serial arm answers the questions an R0 operational loop must
  answer honestly: is the target reachable? what joint config reaches it (analytic
  2-link IK, closed form)? what joint-space trajectory gets there (pair with
  safety/check-trajectory to bound the per-step rate)?

  Pure float64 math (java.lang.Math cos/sin/hypot/atan2/sqrt); `round9` mirrors
  Python `round(x, 9)` (round-half-to-even) so FK/IK outputs match the python
  bit-for-bit. When 40-engine/kami-engine is checked out, the same task scripts
  target the Featherstone 6-DOF solver; this FK/IK contract is the subset the
  cells depend on, so the swap is mechanical.")

(defn- round9
  "Mirror Python `round(x, 9)`: round-half-to-even at 9 decimal places."
  [x]
  (let [scale 1.0e9]
    (/ (Math/rint (* (double x) scale)) scale)))

;; ── Pose ──────────────────────────────────────────────────────────

(defn pose
  "A planar end-effector pose. Port of the `Pose` dataclass (theta defaults 0.0)."
  ([x y] (pose x y 0.0))
  ([x y theta] {:x (double x) :y (double y) :theta (double theta)}))

;; ── PlanarArm ─────────────────────────────────────────────────────

(defn planar-arm
  "A planar serial arm defined by its link lengths (metres). Port of `PlanarArm`."
  [link-lengths]
  {:link-lengths (mapv double link-lengths)})

(defn max-reach
  "Σ link lengths. Port of `PlanarArm.max_reach`."
  [arm]
  (reduce + 0.0 (:link-lengths arm)))

(defn min-reach
  "Inner workspace radius (0 if any single link can fold inside the rest).
  Port of `PlanarArm.min_reach`."
  [arm]
  (let [longest (apply max (:link-lengths arm))
        rest    (- (max-reach arm) longest)]
    (max 0.0 (- longest rest))))

(defn fk
  "Forward kinematics: joint angles (rad, relative) → end-effector Pose.
  Port of `PlanarArm.fk`."
  [arm joints]
  (let [links (:link-lengths arm)]
    (when (not= (count joints) (count links))
      (throw (ex-info (str "expected " (count links) " joints, got " (count joints))
                      {:kinematics/error true})))
    (let [[x y theta]
          (reduce (fn [[x y theta] [length q]]
                    (let [theta' (+ theta q)]
                      [(+ x (* length (Math/cos theta')))
                       (+ y (* length (Math/sin theta')))
                       theta']))
                  [0.0 0.0 0.0]
                  (map vector links joints))]
      (pose (round9 x) (round9 y) (round9 theta)))))

(defn reachable
  "Is (x, y) within [min_reach, max_reach]? Port of `PlanarArm.reachable`."
  [arm x y]
  (let [r (Math/hypot (double x) (double y))]
    (and (<= (- (min-reach arm) 1e-9) r)
         (<= r (+ (max-reach arm) 1e-9)))))

(defn ik2
  "Analytic 2-link inverse kinematics (requires exactly 2 links). Returns
  [q0 q1] in radians, or nil if (x, y) is unreachable. `elbow-up` selects between
  the two mirror solutions. Port of `PlanarArm.ik2`."
  ([arm x y] (ik2 arm x y true))
  ([arm x y elbow-up]
   (let [links (:link-lengths arm)]
     (when (not= 2 (count links))
       (throw (ex-info "ik2 requires a 2-link arm" {:kinematics/error true})))
     (let [[l1 l2] links
           x (double x) y (double y)
           r2 (+ (* x x) (* y y))
           cos-q1 (/ (- r2 (* l1 l1) (* l2 l2)) (* 2.0 l1 l2))]
       (if (or (< cos-q1 (- -1.0 1e-9)) (> cos-q1 (+ 1.0 1e-9)))
         nil  ;; unreachable
         (let [cos-q1 (min 1.0 (max -1.0 cos-q1))
               sin-q1 (Math/sqrt (max 0.0 (- 1.0 (* cos-q1 cos-q1))))
               sin-q1 (if elbow-up (- sin-q1) sin-q1)
               q1 (Math/atan2 sin-q1 cos-q1)
               q0 (- (Math/atan2 y x)
                     (Math/atan2 (* l2 (Math/sin q1)) (+ l1 (* l2 (Math/cos q1)))))]
           [(round9 q0) (round9 q1)]))))))

;; ── Trajectory ────────────────────────────────────────────────────

(defn joint-trajectory
  "Linear joint-space interpolation from q-start to q-goal over `steps` steps.
  Returns `steps + 1` configurations (inclusive of both endpoints). Port of
  `joint_trajectory`. Pair with safety/check-trajectory to bound the per-step rate."
  [q-start q-goal steps]
  (when (not= (count q-start) (count q-goal))
    (throw (ex-info "start and goal must have equal joint count" {:kinematics/error true})))
  (when (< steps 1)
    (throw (ex-info "steps must be >= 1" {:kinematics/error true})))
  (mapv (fn [k]
          (let [a (/ (double k) steps)]
            (mapv (fn [s g] (+ s (* a (- g s)))) q-start q-goal)))
        (range (inc steps))))
