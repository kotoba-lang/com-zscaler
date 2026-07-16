(ns niyaku.methods.terminal-cycle
  "terminal_cycle — end-to-end vessel-discharge orchestration.

  1:1 Clojure port of `20-actors/niyaku/methods/terminal_cycle.py`.

  Ties the four method cores into one deterministic discharge simulation:

    stow-plan       → where each box sits + the no-rehandle discharge order
    crane-dynamics  → per-box hoist + anti-sway traverse time & residual sway
    agv-transfer    → quay-apron → yard legs dispatched across the AGV fleet
    isaac-sway-sim  → (optional) routes the crane traverse through the clean-room
                      isaacsim.core.api Cartpole instead of the analytic model

  Returns a discharge report with overall discharge time (max of crane- and
  AGV-bound timelines, since they pipeline), productivity (moves/hour), worst
  per-box residual sway, and a per-box ledger.

  Pure Clojure, no external deps. Portable .cljc.

  NOTE: on this host the Isaac surface is unavailable (no Clojure kotodama), so
  `:use-isaac true` falls back to the analytic model — exactly the Python
  fall-back path when kotodama is absent."
  (:require [niyaku.methods.agv-transfer :as agv]
            [niyaku.methods.crane-dynamics :as cd]
            [niyaku.methods.isaac-sway-sim :as sim]
            [niyaku.methods.stow-plan :as sp]))

(defn- round-half-even
  "Python round(x, n): banker's rounding (HALF_EVEN) to n decimal places."
  [x n]
  #?(:clj (-> (java.math.BigDecimal/valueOf (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :default (let [p (Math/pow 10 n)
                    scaled (* (double x) p)
                    fl (Math/floor scaled)
                    diff (- scaled fl)
                    r (cond
                        (< diff 0.5) fl
                        (> diff 0.5) (inc fl)
                        :else (if (even? (long fl)) fl (inc fl)))]
                (/ r p))))

(defn make-yard-layout
  "Where the yard sits relative to each crane (drives AGV leg distance)."
  [& {:keys [apron-to-yard-m per-row-offset-m]
      :or   {apron-to-yard-m 120.0   ;; m
             per-row-offset-m 6.0}}]  ;; extra AGV distance per yard row index
  {:apron-to-yard-m apron-to-yard-m :per-row-offset-m per-row-offset-m})

(defn- make-box-move-record
  [box-id crane-time-s residual-sway-m agv-id agv-time-s]
  {:box-id box-id
   :crane-time-s crane-time-s
   :residual-sway-m residual-sway-m
   :agv-id agv-id
   :agv-time-s agv-time-s})

(defn- make-discharge-report
  [records crane-timeline-s agv-makespan-s discharge-time-s max-residual-sway-m moves]
  {:records records
   :crane-timeline-s crane-timeline-s
   :agv-makespan-s agv-makespan-s
   :discharge-time-s discharge-time-s
   :max-residual-sway-m max-residual-sway-m
   :moves moves})

(defn moves-per-hour
  ^double [report]
  (if (<= (double (:discharge-time-s report)) 0)
    0.0
    (/ (* 3600.0 (:moves report)) (double (:discharge-time-s report)))))

(defn- traverse-distance
  "Ship→shore traverse distance for a box: outreach scaled by how far out the row
  sits, bounded by the rail."
  ^double [crane ^long slot-row]
  (let [base (min (* (double (:rail-length crane)) 0.5) 25.0)]
    (min (double (:rail-length crane)) (+ base (* slot-row 2.0)))))

(defn simulate-discharge
  "Simulate discharging every box bound for `discharge-port`.

  If `:plan` is omitted a stow plan is built first. The crane works boxes serially
  in the no-rehandle discharge order; AGVs run the yard legs in parallel
  (dispatched LPT). `:use-isaac true` routes each crane traverse through the
  clean-room Isaac Cartpole (falls back to the analytic model if unavailable)."
  [containers rotation discharge-port bays rows tiers
   & {:keys [crane agv agv-ids yard plan use-isaac]
      :or   {use-isaac false}}]
  (let [crane (or crane (cd/make-gantry-crane))
        agv (or agv (agv/make-agv))
        agv-ids (or agv-ids ["AGV1" "AGV2" "AGV3"])
        yard (or yard (make-yard-layout))
        plan (or plan (sp/build-stow-plan containers rotation bays rows tiers))
        by-id (zipmap (map :box-id containers) containers)
        ;; only boxes assigned a slot AND bound for this port, in no-rehandle order
        seq* (filter (fn [b]
                       (let [c (get by-id b)]
                         (and c (= (:discharge-port c) discharge-port))))
                     (sp/discharge-sequence plan discharge-port))
        ;; on this host isaac is never available → analytic fall-back (Python parity)
        isaac-run (when use-isaac
                    (when (sim/isaac-available?) sim/run-sts-transfer))
        step (fn [[crane-timeline max-sway moves records] box-id]
               (let [slot (sp/slot-of plan box-id)
                     dist (traverse-distance crane (:row slot))
                     [crane-time sway]
                     (if (some? isaac-run)
                       (let [rep (isaac-run :x-target (min (/ dist 15.0) 2.0)
                                            :anti-sway true :steps 4000)
                             res (cd/simulate-traverse crane dist :max-time-s 300.0)]
                         [(double (:settle-time-s res))
                          (* (Math/abs (double (:residual-sway-rad rep)))
                             (double (:cable-length crane)))])
                       (let [res (cd/simulate-traverse crane dist :max-time-s 300.0)]
                         [(double (:settle-time-s res)) (double (:residual-sway-m res))]))
                     ;; hoist: up clear of guides (tier-dependent) + down onto AGV
                     hoist (/ (+ (* (double (:cable-length crane)) 0.4)
                                 (* (:tier slot) 2.6) 12.0) 1.5)
                     crane-time (+ crane-time hoist)
                     agv-dist (+ (double (:apron-to-yard-m yard))
                                 (* (:row slot) (double (:per-row-offset-m yard))))]
                 [(+ crane-timeline crane-time)
                  (max max-sway sway)
                  (conj moves (agv/make-move box-id agv-dist))
                  (conj records (make-box-move-record
                                  box-id (round-half-even crane-time 2)
                                  (round-half-even sway 4)
                                  "" (round-half-even (agv/travel-time agv-dist agv) 2)))]))
        [crane-timeline max-sway moves records]
        (reduce step [0.0 0.0 [] []] seq*)
        disp (agv/dispatch moves agv-ids agv)
        ;; back-fill which AGV took each box
        box-to-agv (into {} (for [[a bids] (:assignment disp), bid bids] [bid a]))
        records (mapv (fn [r] (assoc r :agv-id (get box-to-agv (:box-id r) ""))) records)
        agv-makespan (agv/makespan disp)]
    (make-discharge-report
      records
      (round-half-even crane-timeline 2)
      (round-half-even agv-makespan 2)
      (round-half-even (max crane-timeline agv-makespan) 2)
      (round-half-even max-sway 4)
      (count records))))
