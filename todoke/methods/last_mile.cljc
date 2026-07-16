(ns todoke.methods.last-mile
  "todoke 届け — pure-Clojure mirror of the todoke-route Rust core + courier liberation sizing.

  1:1 port of `20-actors/todoke/methods/last_mile.py` (ADR-2606042300).

  Purposes:

    1. `plan-last-mile` / safety-envelope helpers — a faithful Clojure mirror of the Rust
       `todoke-route` crate. The cell state-machine calls this; the deployed run calls the
       Rust crate. The parity test pins the two implementations to the same visiting order.

    2. `courier-freed-hours` / `displacement-cohort-size` — sizing for the labour-liberation
       mission (ISIC H53 / ISCO 9621 parcel-courier toil) and the G2 Displacement-Dividend
       coupling (ADR-2606032130). Order-of-magnitude and `:representative` (G10).

  A Stop is a map {:id :x :y :zone}. distance = Math/hypot of dx,dy.

  Constitutional:
    G7 (safety envelope) — an out-of-ceiling SAE level or out-of-ODD zone is modelled as
    a thrown ex-info with `:envelope-violation` true, mirroring the Python `EnvelopeViolation`.

  Pure Clojure (clojure.core + Math only), no external deps. Portable .cljc."
  (:require [clojure.string :as str]))

(def ^:const sae-level-ceiling 4) ; N2: Level 5 is a non-goal

;; Per-zone speed caps in m/s — MUST match Zone::speed_cap_mps in route/src/lib.rs.
(def zone-speed-cap-mps
  {"sidewalk"  1.8
   "crosswalk" 1.4
   "doorpath"  1.0
   "bikelane"  4.2
   "road"      nil}) ; not in the todoke ODD (N2)

(defn envelope-violation
  "Return an ex-info map for a constitutional refusal (G7)."
  [msg]
  (ex-info msg {:envelope-violation true}))

(defn stop
  "Convenience constructor for a Stop map {:id :x :y :zone} — a plain map is
  equally valid; this just names the shape (mirrors Python's Stop(id, x, y, zone))."
  [id x y zone]
  {:id id :x x :y y :zone zone})

(defn- stop-dist
  "Euclidean distance between two stops."
  [a b]
  (Math/hypot (- (:x a) (:x b)) (- (:y a) (:y b))))

(defn- check-envelope
  "G7 gate — throw BEFORE any route is produced, mirroring the Rust `Err` returns."
  [stops sae-level commanded-mps]
  (when (> sae-level sae-level-ceiling)
    (throw (envelope-violation (str "G7: SAE level " sae-level " exceeds ceiling " sae-level-ceiling " (N2)"))))
  (doseq [s stops]
    (let [cap (get zone-speed-cap-mps (:zone s))]
      (when (nil? cap)
        (throw (envelope-violation (str "G7: stop " (:id s) " zone '" (:zone s) "' outside todoke ODD (N2)"))))
      (when (> commanded-mps cap)
        (throw (envelope-violation (str "G7: commanded " commanded-mps " m/s exceeds " (:zone s) " cap " cap " m/s at stop " (:id s))))))))

(defn- nearest-neighbour
  "Greedy nearest-neighbour TSP from stops[0]; returns indices. The tie-breaker matches Python exactly: prefer smaller j at <= best_d + 1e-12."
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

(defn- two-opt-swap-value
  "Return [new-tour improved?] after one complete 2-opt pass; depot (index 0) pinned."
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
  "Repeatedly apply 2-opt passes until no improvement. Mirrors Python semantics exactly."
  [seed stops]
  (loop [tour (vec seed)]
    (let [new-tour (two-opt-swap-value tour stops)]
      (if (= new-tour tour)
        tour
        (recur new-tour)))))

(defn plan-last-mile
  "Return [order-of-ids length-m] for a safety-validated last-mile path.

   `stops[0]` is the depot/drop curb; the path is open (no return). Throws an
   ex-info with `:envelope-violation` true if the run would break the envelope (G7).

   Accepts keyword-style optional args `:sae-level` (default 4) and `:commanded-mps`
   (default 1.5), matching the Python signatures."
  [stops & {:keys [sae-level commanded-mps]
            :or {sae-level 4 commanded-mps 1.5}}]
  (when (empty? stops)
    (throw (envelope-violation "G7: no stops to route")))
  (check-envelope stops sae-level commanded-mps)
  (let [seq-idx (two-opt (nearest-neighbour stops) stops)
        length (reduce + (map #(stop-dist (nth stops (nth seq-idx %))
                                          (nth stops (nth seq-idx (inc %))))
                              (range (dec (count seq-idx)))))]
    [(mapv #(-> (nth stops %) :id) seq-idx) length]))

;; --- Labour-liberation sizing (mission + G2 coupling) ---------------------------------

(defn courier-freed-hours
  "Human courier labour-hours/year removed by automating `automation-fraction` of stops."
  [headcount hours-per-worker-yr automation-fraction]
  (* headcount hours-per-worker-yr automation-fraction))

(defn displacement-cohort-size
  "Approx number of courier roles displaced (owed the tenure-weighted dividend, ADR-2606032130)."
  [headcount automation-fraction]
  (long (Math/round (* headcount automation-fraction))))

(defn -main
  "CLI entry: `:representative` order-of-magnitude (G10): global parcel-courier pool ISCO 9621."
  [& _argv]
  (let [head 1.9e7
        fh (courier-freed-hours head 2200 0.30)
        cohort (displacement-cohort-size head 0.30)]
    (println (str "todoke illustrative: automating 30% of last-mile stops frees "
                  (format "%.1f" (/ fh 1.0e9)) "B courier-hours/yr; cohort ≈ "
                  (format "%.1f" (/ cohort 1.0e6)) "M roles."))))
