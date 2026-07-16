;; madomori 窓守 — façade coverage path planning over a grid of glazing panes.
;;
;; A building face is a grid of panes (rows × cols). The cleaning unit must visit
;; every pane exactly once. We plan a boustrophedon (S-shape / ox-plough) coverage
;; path — the classic minimal-turn coverage sweep — then sum the traversed length
;; and the per-pane water + cleaning-agent budget (G2 — track and minimize).
;;
;; A coverage plan is complete iff every pane is visited exactly once; `coverage`
;; reports completeness so a planner can never silently skip glass.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure
;; planning compute; cleans no real glass (G1 no-server-key / R0 design+sim).
;; Per ADR-2606142020 (madomori R0). Clojure-first (the GAP-actor wave).
(ns madomori.methods.facade-path)

;; ── boustrophedon coverage path ──────────────────────────────────────────────
(defn boustrophedon
  "S-shape coverage order over a rows×cols pane grid. Returns a vector of [row col]
   cells: row 0 left→right, row 1 right→left, … (alternating) so consecutive panes
   are always adjacent — the minimal-turn coverage sweep. (row 0 = bottom course.)"
  [rows cols]
  (when (or (neg? rows) (neg? cols))
    (throw (ex-info "rows/cols must be non-negative" {:rows rows :cols cols})))
  (vec
   (for [r (range rows)
         c (if (even? r) (range cols) (reverse (range cols)))]
     [r c])))

;; ── path length (in pane-units, then metres) ─────────────────────────────────
(defn- step-dist
  "Manhattan adjacency step between two grid cells, in pane-units."
  [[r1 c1] [r2 c2]]
  (+ (Math/abs (- r1 r2)) (Math/abs (- c1 c2))))

(defn path-length-m
  "Total traversed length of a coverage `path` (seq of [row col]) in METRES,
   using per-pane height/width. Each horizontal step costs pane-w, each vertical
   step costs pane-h."
  [path pane-h-m pane-w-m]
  (->> (partition 2 1 path)
       (reduce (fn [acc [[r1 c1] [r2 c2]]]
                 (+ acc
                    (* (Math/abs (- r1 r2)) pane-h-m)
                    (* (Math/abs (- c1 c2)) pane-w-m)))
               0.0)))

;; ── per-pane consumable budget (G2 — minimize water + agent) ─────────────────
(defn consumable-budget
  "Total water (L) and cleaning-agent (mL) for a coverage of `n-panes`.
   The agent is reported so a planner can MINIMISE it (G2 — eco). No solvent choice
   is made here; the agent identity stays in the seed (deionized-water + surfactant)."
  [n-panes water-l-per-pane agent-ml-per-pane]
  {:panes n-panes
   :water-l (* n-panes water-l-per-pane)
   :agent-ml (* n-panes agent-ml-per-pane)})

;; ── coverage completeness (every pane visited exactly once) ──────────────────
(defn coverage
  "Completeness of a coverage `path` over a rows×cols grid. Returns
   {:total :visited :complete? :duplicate? :missing}. `:complete?` iff every pane
   is visited exactly once (no skip, no double-pass)."
  [path rows cols]
  (let [total (* rows cols)
        all (set (for [r (range rows) c (range cols)] [r c]))
        seen (frequencies path)
        visited (count (keys seen))
        duplicate? (boolean (some #(> % 1) (vals seen)))
        missing (vec (sort (remove (set (keys seen)) all)))]
    {:total total
     :visited visited
     :duplicate? duplicate?
     :missing missing
     :complete? (and (= visited total) (not duplicate?) (empty? missing))}))

(defn plan
  "Full façade coverage plan over a `face` map ({:rows :cols :pane-h-m :pane-w-m})
   and a `robot` map ({:water-l-per-pane :agent-ml-per-pane}). Returns
   {:path :length-m :budget :coverage}."
  [face robot]
  (let [{:keys [rows cols pane-h-m pane-w-m]} face
        path (boustrophedon rows cols)]
    {:path path
     :length-m (path-length-m path pane-h-m pane-w-m)
     :budget (consumable-budget (count path)
                                (:water-l-per-pane robot 0)
                                (:agent-ml-per-pane robot 0))
     :coverage (coverage path rows cols)}))
