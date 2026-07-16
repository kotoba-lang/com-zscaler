(ns niyaku.methods.stow-plan
  "niyaku_stow_plan — container stowage slotting + discharge/load sequencing.

  1:1 Clojure port of `20-actors/niyaku/methods/stow_plan.py`.

  Pure Clojure (clojure.core only), no external deps. Portable .cljc."
  (:require [clojure.string :as str]))

(defn make-slot
  "A cell address. tier increases upward (0 = bottom)."
  [bay row tier]
  {:bay bay :row row :tier tier})

(defn slot-key
  "Return the (bay, row, tier) tuple for a slot."
  [slot]
  [(:bay slot) (:row slot) (:tier slot)])

(defn make-container
  ([box-id weight-t discharge-port]
   (make-container box-id weight-t discharge-port false nil))
  ([box-id weight-t discharge-port reefer]
   (make-container box-id weight-t discharge-port reefer nil))
  ([box-id weight-t discharge-port reefer hazmat]
   {:box-id box-id
    :weight-t weight-t
    :discharge-port discharge-port
    :reefer (boolean reefer)
    :hazmat hazmat}))

(defn- stow-error
  [msg]
  (ex-info msg {:error :stow}))

(defn make-stowage-plan
  ([] (make-stowage-plan {} []))
  ([assignments] (make-stowage-plan assignments []))
  ([assignments rotation]
   {:assignments assignments
    :rotation rotation}))

(defn slot-of
  "Return the slot assigned to `box-id`."
  [plan box-id]
  (get-in plan [:assignments box-id]))

(defn- stack-columns
  "All (bay, row) pairs in column-major order."
  [bays rows tiers]
  (for [b (range bays) r (range rows)] [b r]))

(defn build-stow-plan
  "Assign every container a slot under the core stowage constraints.

  Constraints enforced:
    * capacity — at most bays*rows*tiers boxes;
    * port rotation — earlier-discharge boxes are stacked ABOVE later ones;
    * weight-on-top — no heavier box rests on a lighter one in a column;
    * reefer — reefer boxes only in reefer_rows (if given);
    * hazmat — two different IMDG classes never share a column.

  Throws an ex-info with {:error :stow} if all boxes cannot be placed."
  [containers rotation bays rows tiers & {:keys [reefer-rows]}]
  (when (empty? rotation)
    (throw (stow-error "rotation must list at least one discharge port")))
  (let [reefer-rows (if (some? reefer-rows) (set reefer-rows) (set (range rows)))
        rot-index (zipmap rotation (range))
        _ (doseq [c containers]
            (when-not (contains? rot-index (:discharge-port c))
              (throw (stow-error
                       (str (:box-id c) ": discharge_port " (:discharge-port c)
                            " not in rotation")))))
        order (sort-by (fn [c] [(- (rot-index (:discharge-port c)))
                                (- (:weight-t c))])
                       containers)
        columns (stack-columns bays rows tiers)
        col-height (zipmap columns (repeat 0))
        col-hazmat (zipmap columns (repeat nil))
        col-top-weight (zipmap columns (repeat ##Inf))
        col-top-port (zipmap columns (repeat -1))]
    (loop [cs order
           plan (make-stowage-plan {} (vec rotation))
           col-height col-height
           col-hazmat col-hazmat
           col-top-weight col-top-weight
           col-top-port col-top-port]
      (if-let [c (first cs)]
        (let [result
              (some
                (fn [col]
                  (let [h (col-height col)
                        [b r] col]
                    (when (and (< h tiers)
                               (or (not (:reefer c)) (contains? reefer-rows r))
                               (or (nil? (:hazmat c))
                                   (= (:hazmat c) (col-hazmat col))
                                   (nil? (col-hazmat col)))
                               (<= (:weight-t c) (col-top-weight col))
                               (or (< (col-top-port col) 0)
                                   (<= (rot-index (:discharge-port c))
                                       (col-top-port col))))
                      {:slot (make-slot b r h) :col col})))
                columns)]
          (if-not result
            (throw (stow-error (str "no feasible slot for " (:box-id c))))
            (let [{:keys [slot col]} result
                  tier (:tier slot)]
              (recur (rest cs)
                     (assoc-in plan [:assignments (:box-id c)] slot)
                     (assoc col-height col (inc tier))
                     (if (some? (:hazmat c))
                       (assoc col-hazmat col (:hazmat c))
                       col-hazmat)
                     (assoc col-top-weight col (:weight-t c))
                     (assoc col-top-port col (rot-index (:discharge-port c)))))))
        plan))))

(defn discharge-sequence
  "Order to discharge all boxes for `port`: top tier first, per column.

  Guarantees no re-handle: within each column boxes are lifted top→bottom."
  [plan port]
  (let [by-col (reduce (fn [m [box-id slot]]
                         (update m [(:bay slot) (:row slot)]
                                 conj [(:tier slot) box-id]))
                       {}
                       (:assignments plan))
        cols (sort (keys by-col))]
    (vec
      (mapcat (fn [col]
                (->> (get by-col col)
                     (sort-by first #(compare %2 %1))
                     (map second)))
              cols))))

(defn validate-no-rehandle
  "True iff no column has a later-discharge box stacked above an earlier one."
  [plan rotation-index box-port]
  (let [by-col (reduce (fn [m [box-id slot]]
                         (update m [(:bay slot) (:row slot)]
                                 conj [(:tier slot) box-id]))
                       {}
                       (:assignments plan))]
    (every?
      (fn [col]
        (->> (get by-col col)
             (sort-by first)
             (reduce (fn [prev [_tier box-id]]
                       (let [p (rotation-index (box-port box-id))]
                         (if (and (some? prev) (> p prev))
                           (reduced false)
                           p)))
                     nil)
             (not= false)))
      (keys by-col))))
