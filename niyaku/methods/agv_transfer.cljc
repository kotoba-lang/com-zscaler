(ns niyaku.methods.agv-transfer
  "agv_transfer — automated guided vehicle (AGV) horizontal-transport core.

  1:1 Clojure port of `20-actors/niyaku/methods/agv_transfer.py`.

  After the STS crane lands a box on the quay apron, a battery AGV carries it to
  the yard stack. This module is the planning core behind the yard_transfer cell:

    * a trapezoidal velocity profile (accel → cruise → decel, or triangular when
      the leg is too short to reach cruise) giving time-optimal travel time;
    * a lane-segment conflict check (two AGVs sharing a one-way segment must not
      have overlapping occupancy time windows);
    * a greedy dispatch that assigns moves to a fleet to minimise makespan.

  Pure Clojure (clojure.core only), no external deps. Portable .cljc.")

(defn make-agv
  "Battery AGV kinematic envelope (electric, regenerative braking — G8)."
  [& {:keys [v-max a-max length-m]
      :or   {v-max 6.0       ;; m/s
             a-max 0.8       ;; m/s² (accel = decel)
             length-m 16.0}}] ;; AGV + 40ft box footprint, for segment occupancy
  {:v-max v-max :a-max a-max :length-m length-m})

(defn travel-time
  "Time-optimal travel time over a straight leg under a trapezoidal profile.

  If the leg is long enough to reach v_max the profile is accel/cruise/decel;
  otherwise a symmetric triangular accel/decel that peaks below v_max."
  ^double [^double distance-m agv]
  (when (< distance-m 0)
    (throw (ex-info "distance must be non-negative" {:error :value})))
  (if (== distance-m 0)
    0.0
    (let [a (double (:a-max agv))
          v (double (:v-max agv))
          d-to-vmax (/ (* v v) a)]       ;; distance to accel to v_max then decel to 0
      (if (>= distance-m d-to-vmax)
        (let [t-ramp (/ v a)             ;; accel + symmetric decel
              d-cruise (- distance-m d-to-vmax)]
          (+ (* 2.0 t-ramp) (/ d-cruise v)))
        ;; triangular: peak velocity vp = sqrt(a * d); total t = 2 vp / a
        (let [vp (Math/sqrt (* a distance-m))]
          (/ (* 2.0 vp) a))))))

(defn make-segment-reservation
  "One AGV's occupancy of a named one-way lane segment over [t_in, t_out]."
  [segment agv-id t-in t-out]
  {:segment segment :agv-id agv-id :t-in t-in :t-out t-out})

(defn reservations-conflict
  "True iff two reservations share a segment and overlap in time.

  Touching at an endpoint (t_out == t_in) is NOT a conflict. Different segments
  never conflict."
  [r1 r2]
  (if (or (not= (:segment r1) (:segment r2)) (= (:agv-id r1) (:agv-id r2)))
    false
    (and (< (double (:t-in r1)) (double (:t-out r2)))
         (< (double (:t-in r2)) (double (:t-out r1))))))

(defn find-conflicts
  "All conflicting index pairs [i j] (i<j) in a reservation list."
  [reservations]
  (let [n (count reservations)
        rv (vec reservations)]
    (vec
      (for [i (range n)
            j (range (inc i) n)
            :when (reservations-conflict (nth rv i) (nth rv j))]
        [i j]))))

(defn make-move
  [move-id distance-m]
  {:move-id move-id :distance-m distance-m})

(defn make-dispatch-result
  ([] (make-dispatch-result {} {}))
  ([assignment finish-time]
   {:assignment assignment :finish-time finish-time}))

(defn makespan
  ^double [dispatch-result]
  (let [ft (vals (:finish-time dispatch-result))]
    (if (seq ft) (double (apply max ft)) 0.0)))

(defn dispatch
  "Greedy makespan-minimising assignment: each move (longest-first) goes to the
  AGV that is currently free earliest (LPT — longest-processing-time rule)."
  [moves agv-ids agv]
  (when (empty? agv-ids)
    (throw (ex-info "need at least one AGV" {:error :value})))
  (let [ordered (sort-by #(- (double (:distance-m %))) moves)
        ;; Python min(agv_ids, key=…) keeps the FIRST on ties (preserves list order);
        ;; Clojure min-key keeps the last — so pick first-min explicitly.
        first-min-by (fn [ks f]
                       (reduce (fn [best x] (if (< (double (f x)) (double (f best))) x best))
                               (first ks) (rest ks)))]
    (reduce
      (fn [res mv]
        ;; pick the AGV that frees up soonest (min by finish-time; first wins on ties)
        (let [a (first-min-by agv-ids #(get (:finish-time res) %))]
          (-> res
              (update-in [:assignment a] conj (:move-id mv))
              (update-in [:finish-time a] + (travel-time (double (:distance-m mv)) agv)))))
      (make-dispatch-result
        (zipmap agv-ids (repeat []))
        (zipmap agv-ids (repeat 0.0)))
      ordered)))
