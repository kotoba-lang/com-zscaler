(ns keizu.cells.committee-graph.state-machine
  "Phase state machine for the 系図 (keizu) committee_graph cell.
  1:1 port of cells/committee_graph/state_machine.py (ADR-2606066000).

  Given committee composition snapshots, derives cross-committee co-membership EDGES (a seat on
  >1 committee) — edge-primary (G4), never a per-seat score. Self-contained.")

(def phase-init "init")
(def phase-composed "composed")
(def phase-refused "refused")

(def state-defaults
  {"phase"         phase-init
   "committees"    []
   "co_membership" []
   "refusal"       ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn transition-to-composed [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0 "committees" (get state "committees" (get cs0 "committees")))
        committees (get cs "committees")]
    (if (empty? committees)
      {"cell_state" (assoc cs "refusal" "no committees to compose" "phase" phase-refused)}
      (let [;; by_seat: dict[str, list] preserving insertion+append order
            by-seat
            (reduce
             (fn [acc c]
               (reduce
                (fn [a seat]
                  (update a seat (fnil conj []) (get c "id")))
                acc
                (get c "members" [])))
             {}
             committees)
            co-membership
            (->> (sort-by key by-seat)
                 (keep (fn [[s cl]]
                         (when (> (count (set cl)) 1)
                           {"seat" s "committees" (vec (sort (set cl)))})))
                 vec)]
        {"cell_state" (assoc cs "co_membership" co-membership "phase" phase-composed)}))))
