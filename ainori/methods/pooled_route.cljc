(ns ainori.methods.pooled-route
  "pooled_route — ainori multi-stop pooled sequencing, REUSING the todoke route core.

  1:1 port of `20-actors/ainori/methods/pooled_route.py` (ADR-2606071500).

  Per the manifest `:actor/reuses` claim, ainori does NOT ship a second routing engine:
  it reuses todoke's `methods/last_mile` sequencing primitives — the SAME `_nearest_neighbour`
  + `_two_opt` core that the Rust `todoke-route` crate mirrors. The Python `sequence_stops`
  calls those two primitives DIRECTLY (it does NOT call `plan_last_mile`), deliberately
  bypassing todoke's PEDESTRIAN safety envelope (sequencing is charter-neutral; ainori's
  vehicular envelope lives in agent.py, G3). The matching todoke `.cljc` makes those primitives
  private (`defn-`), so this port inlines byte-faithful copies of todoke's nearest-neighbour +
  2-opt (identical tie-breakers / epsilons / depot-pinning) rather than reimplementing them — and
  the parity test pins ainori's `sequence-stops` to `todoke/plan-last-mile`'s order on a shared
  sidewalk fixture, so the two cannot drift.

  The ONE thing ainori does NOT inherit from todoke is the *safety envelope*: todoke's ODD is
  pedestrian (sidewalk/crosswalk/doorpath/bikelane), while ainori is VEHICULAR. ainori keeps
  its own SAE-L4 vehicular envelope in agent.py (G3) and reuses only the geometric stop
  sequencing. Sequencing has no charter content; the envelope does. ainori's no-surge
  `cost-share` (agent.py) is inlined here verbatim (compose, don't duplicate; integer
  floor-division so the carrier absorbs any remainder — the platform never profits, G1/G2).

  Data maps are STRING-keyed (mirrors the Python dicts). Pure; portable .cljc."
  (:require [todoke.methods.last-mile]))   ;; loaded so the reuse claim is structural (parity test)

(defn- stop-dist
  "Euclidean distance between two {:x :y} stop maps (mirrors todoke Stop.dist / Math.hypot)."
  [a b]
  (Math/hypot (- (:x a) (:x b)) (- (:y a) (:y b))))

;; --------------------------------------------------------------------------- #
;; todoke route core, inlined verbatim (todoke makes these `defn-`). The Python
;; `sequence_stops` calls these primitives directly to bypass the pedestrian envelope.
;; --------------------------------------------------------------------------- #
(defn- nearest-neighbour
  "Greedy nearest-neighbour TSP from stops[0]; returns indices. Tie-breaker matches Python
  exactly: prefer smaller j at <= best_d + 1e-12 (verbatim copy of todoke's private fn)."
  [stops]
  (let [n (count stops)
        visited (boolean-array n false)]
    (aset visited 0 true)
    (loop [tour [0]
           cur 0]
      (if (= (count tour) n)
        tour
        (let [[best _best-d]
              (loop [j 0, best nil, best-d Double/POSITIVE_INFINITY]
                (if (>= j n)
                  [best best-d]
                  (if (aget visited j)
                    (recur (inc j) best best-d)
                    (let [d (stop-dist (nth stops cur) (nth stops j))]
                      (if (or (and best (< d (- best-d 1e-12)))
                              (and best (<= d (+ best-d 1e-12)) (< j best))
                              (nil? best))
                        (recur (inc j) j d)
                        (recur (inc j) best best-d))))))]
          (assert (some? best) "nearest-neighbour could not find next stop")
          (aset visited best true)
          (recur (conj tour best) best))))))

(defn- reverse-segment
  "Reverse tour[i..k] inclusive, returning a new tour vector."
  [tour i k]
  (let [n (count tour)]
    (vec (concat (subvec tour 0 i)
                 (reverse (subvec tour i (inc k)))
                 (subvec tour (inc k) n)))))

(defn- two-opt-pass
  "One complete 2-opt pass; depot (index 0) pinned. Verbatim from todoke's private fn."
  [tour stops]
  (let [n (count tour)]
    (loop [t (vec tour)
           i 1]
      (if (>= i (- n 1))
        t
        (recur
         (loop [t t
                k (inc i)]
           (if (>= k n)
             t
             (let [ti-1 (nth t (dec i))
                   ti   (nth t i)
                   tk   (nth t k)
                   tk+1 (when (< (inc k) n) (nth t (inc k)))
                   a (nth stops ti-1)
                   b (nth stops ti)
                   c (nth stops tk)
                   d-next (when tk+1 (nth stops tk+1))
                   before (+ (stop-dist a b)
                             (if d-next (stop-dist c d-next) 0.0))
                   after  (+ (stop-dist a c)
                             (if d-next (stop-dist b d-next) 0.0))]
               (if (< (+ after 1e-9) before)
                 (recur (reverse-segment t i k) (inc k))
                 (recur t (inc k))))))
         (inc i))))))

(defn- two-opt
  "Repeatedly apply 2-opt passes until no improvement (Python `_two_opt` returns early for n<4)."
  [seed stops]
  (if (< (count seed) 4)
    (vec seed)
    (loop [tour (vec seed)]
      (let [new-tour (two-opt-pass tour stops)]
        (if (= new-tour tour)
          tour
          (recur new-tour))))))

(defn- ->stop
  "Build a todoke-style stop map {:id :x :y :zone} (the same shape last-mile consumes)."
  [id x y zone]
  {:id (int id) :x (double x) :y (double y) :zone zone})

;; --------------------------------------------------------------------------- #
;; cost-share (G2 no-surge) — inlined from ainori py/agent.py, verbatim behavior.
;; There is NO demand / surge multiplier: share depends only on real cost + occupancy.
;; --------------------------------------------------------------------------- #
(defn cost-share
  "Each rider's flat share of the trip's REAL fuel/wear cost. Higher occupancy ⇒ lower share,
  the opposite of surge (G2). Integer floor-division (carrier absorbs the remainder, G1)."
  [fuel-wear-minor occupancy]
  (let [occ (max 1 (int occupancy))]
    (long (quot (long fuel-wear-minor) occ))))

(defn sequence-stops
  "Order a list of stop maps (`stops[0]` pinned as origin) and return [order-of-ids length-m].

  This IS todoke's sequencing core: `_two_opt(_nearest_neighbour(stops), stops)` — the same
  primitives the Rust crate mirrors. No safety envelope is applied here (sequencing is
  charter-neutral; ainori's vehicular envelope lives in agent.py, G3), so vehicular zones like
  \"arterial\"/\"expressway\" sequence freely. The parity test pins this to todoke's
  `plan-last-mile` order on a shared pedestrian fixture."
  [stops]
  (if (empty? stops)
    [[] 0.0]
    (let [seq-idx (two-opt (nearest-neighbour stops) stops)
          length (reduce + 0.0
                         (map (fn [i]
                                (stop-dist (nth stops (nth seq-idx i))
                                           (nth stops (nth seq-idx (inc i)))))
                              (range (dec (count seq-idx)))))]
      [(mapv #(-> (nth stops %) :id) seq-idx) length])))

(defn pooled-route
  "Build a pooled vehicular route: the carrier's origin (id 0) plus each rider's pickup/dropoff
  point, sequenced by the reused todoke core to minimise added distance (G11). `carrier-origin`
  is [x y]; `rider-points` is a list of string-keyed maps {\"id\" \"x\" \"y\" \"zone\"}. Returns a
  string-keyed map {\"order\" \"lengthM\" \"occupancy\"}."
  [carrier-origin rider-points]
  (let [stops (into [(->stop 0 (nth carrier-origin 0) (nth carrier-origin 1) "arterial")]
                    (map (fn [p]
                           (->stop (get p "id") (get p "x") (get p "y")
                                   (get p "zone" "arterial")))
                         rider-points))
        [order length] (sequence-stops stops)]
    {"order" order
     "lengthM" length
     "occupancy" (count rider-points)}))

(defn plan-pooled-trip
  "End-to-end pooled trip: sequence the stops with the reused todoke core, then split the REAL
  fuel/wear cost flat across the pooled riders with ainori's no-surge `cost-share`. Returns the
  route + per-rider share + total collected.

  Honest cost-share property (G1/G2): totalCollected = share × occupancy NEVER exceeds the real
  fuel/wear (floor-division rounds the per-rider share DOWN, carrier absorbs the remainder)."
  [carrier-origin rider-points fuel-wear-minor]
  (let [route (pooled-route carrier-origin rider-points)
        occ (get route "occupancy")
        share (cost-share fuel-wear-minor occ)]
    (assoc route
           "fuelWearMinor" (long fuel-wear-minor)
           "costSharePerRiderMinor" share
           "totalCollectedMinor" (* share occ))))

;; NOTE: the Python module's import-time todoke path wiring + the __main__ demo are omitted;
;; reuse is expressed directly via the `:require` of todoke.methods.last-mile above.
