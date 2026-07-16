;; madomori 窓守 — multi-face campaign routing over a building's faces/elevations.
;;
;; A tall building has several faces (N/E/S/W elevations, setback steps, atria).
;; A single cleaning campaign must cover every face, but the unit can only clean
;; one face at a time — between faces it must REPOSITION (re-rig the BMU / drive
;; the suction-climber around to the next access point). Reposition travel is dead
;; time, so we sequence the faces by nearest-neighbour over their access points,
;; starting from the ground dock origin [0 0], to minimise total reposition travel.
;;
;; campaign-coverage then sums the per-face boustrophedon coverage path length
;; (reusing facade-path/boustrophedon + facade-path/path-length-m) so a planner sees
;; the full campaign cost = Σ per-face coverage + Σ reposition travel.
;;
;; Pure geometric compute — faces are points + pane grids only. There is NO imagery,
;; person, interior, or biometric attribute anywhere in this model (G3 — the routing
;; layer is purely positional; cameras/recognition live nowhere in a face/route map).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure
;; planning compute; cleans no real glass, moves no real robot (G1 — R0 design+sim).
;; Per ADR-2606142020 (madomori R0). Clojure-first (the GAP-actor wave).
(ns madomori.methods.multi-face
  (:require [madomori.methods.facade-path :as fp]))

;; ── geometry ─────────────────────────────────────────────────────────────────
(defn- euclidean
  "Planar reposition distance between two access points [x y]."
  [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- (double x1) x2) 2)
                (Math/pow (- (double y1) y2) 2))))

;; ── face sequencing (nearest-neighbour over access points) ───────────────────
(defn face-sequence
  "Sequence the building `faces` (each {:face-id :access-point [x y] :rows :cols})
   by nearest-neighbour over their access points, starting from the dock `origin`
   (default [0 0]), to minimise reposition travel. Returns
   {:order [face-id …] :reposition-distance <total>}. The total reposition distance
   includes the dock→first-face leg and every face→next-face leg (no return-to-dock).

   Nearest-neighbour is a greedy heuristic (not provably optimal) but is the standard
   minimal-reposition campaign sequencer and is deterministic given equal-distance
   ties broken by input order."
  ([faces] (face-sequence faces [0 0]))
  ([faces origin]
   (when (empty? faces)
     (throw (ex-info "no faces to sequence" {:faces faces})))
   (loop [remaining (vec faces)
          here origin
          order []
          dist 0.0]
     (if (empty? remaining)
       {:order order :reposition-distance dist}
       (let [;; pick the face whose access point is closest to `here`
             nearest (apply min-key #(euclidean here (:access-point %)) remaining)
             step (euclidean here (:access-point nearest))]
         (recur (vec (remove #(= (:face-id %) (:face-id nearest)) remaining))
                (:access-point nearest)
                (conj order (:face-id nearest))
                (+ dist step)))))))

;; ── per-face coverage estimate (reuses facade-path) ──────────────────────────
(defn face-coverage-length-m
  "Per-face boustrophedon coverage path length in METRES, reusing
   facade-path/boustrophedon (rows×cols pane sweep) + facade-path/path-length-m
   (each horizontal step = pane-w, each vertical step = pane-h). This is the
   documented coverage estimate: an S-shape sweep ≈ rows×cols panes traversed with
   row-transition steps folded in by the path itself."
  [{:keys [rows cols]} pane-h-m pane-w-m]
  (fp/path-length-m (fp/boustrophedon rows cols) pane-h-m pane-w-m))

;; ── campaign coverage (Σ per-face coverage over the sequenced faces) ──────────
(defn campaign-coverage
  "Total campaign cost over `faces` sequenced from `origin`. `pane` is
   {:pane-h-m :pane-w-m} (the per-pane geometry shared across faces).
   Returns {:order :reposition-distance :coverage-length-m :total-length-m
            :per-face [{:face-id :coverage-length-m} …]}, where
   coverage-length-m = Σ per-face coverage path lengths (positive) and
   total-length-m = coverage-length-m + reposition-distance."
  ([faces pane] (campaign-coverage faces pane [0 0]))
  ([faces pane origin]
   (let [{:keys [pane-h-m pane-w-m]} pane
         {:keys [order reposition-distance]} (face-sequence faces origin)
         by-id (into {} (map (juxt :face-id identity) faces))
         per-face (mapv (fn [fid]
                          {:face-id fid
                           :coverage-length-m (face-coverage-length-m
                                               (by-id fid) pane-h-m pane-w-m)})
                        order)
         coverage-length (reduce + 0.0 (map :coverage-length-m per-face))]
     {:order order
      :reposition-distance reposition-distance
      :coverage-length-m coverage-length
      :total-length-m (+ coverage-length reposition-distance)
      :per-face per-face})))

;; ── demo ─────────────────────────────────────────────────────────────────────
(def ^:private demo-faces
  "A 3-face building: south elevation (big), then east + north setbacks.
   Access points are at the base of each elevation; the dock origin is [0 0]."
  [{:face-id :south :access-point [2 0]  :rows 12 :cols 8}
   {:face-id :north :access-point [2 30] :rows 12 :cols 8}
   {:face-id :east  :access-point [20 0] :rows 10 :cols 6}])

(def ^:private demo-pane {:pane-h-m 1.4 :pane-w-m 1.0})

(defn -main [& _]
  (let [seq-res (face-sequence demo-faces [0 0])
        camp (campaign-coverage demo-faces demo-pane [0 0])]
    (println "madomori 窓守 — multi-face campaign routing (R0 design+sim)")
    (println (format "  faces: %d  (south/north/east of a setback tower)" (count demo-faces)))
    (println (format "  nearest-neighbour order : %s"
                     (mapv name (:order seq-res))))
    (println (format "  reposition travel       : %.2f m" (:reposition-distance camp)))
    (println (format "  Σ per-face coverage     : %.2f m" (:coverage-length-m camp)))
    (println (format "  total campaign length   : %.2f m" (:total-length-m camp)))
    (println "  per-face coverage:")
    (doseq [{:keys [face-id coverage-length-m]} (:per-face camp)]
      (println (format "    - %-6s : %.2f m" (name face-id) coverage-length-m)))
    (println "  (G3: routing is purely positional — no imagery/person/interior data)")
    (flush)))
