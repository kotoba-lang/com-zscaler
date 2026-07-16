(ns keizu.cells.relation-weave.state-machine
  "Phase state machine for the 系図 (keizu) relation_weave cell.
  1:1 port of cells/relation_weave/state_machine.py (ADR-2606066000).

  Derives a committee's cross-organ count from its member seats' organs — aggregate, edge-primary
  (G4). A finding describes ties/diversity, never a per-person score. Self-contained.")

(def phase-init "init")
(def phase-woven "woven")
(def phase-refused "refused")

(def state-defaults
  {"phase"      phase-init
   "nodes"      {}
   "committees" []
   "findings"   []
   "refusal"    ""})

(defn- cell-state [state]
  (merge state-defaults (get state "cell_state" {})))

(defn transition-to-woven [state]
  (let [cs0 (cell-state state)
        cs  (assoc cs0
                   "nodes"      (get state "nodes"      (get cs0 "nodes"))
                   "committees" (get state "committees" (get cs0 "committees")))
        committees (get cs "committees")]
    (if (empty? committees)
      {"cell_state" (assoc cs "refusal" "no committees to weave" "phase" phase-refused)}
      (let [nodes (get cs "nodes")
            findings
            (reduce
             (fn [acc c]
               (let [members (get c "members" [])
                     organs (vec (sort (set (map (fn [m] (get-in nodes [m "organ"] "(unknown)"))
                                                 members))))]
                 (conj acc {"committee"      (get c "id")
                            "member_count"   (count members)
                            "distinct_organs" (count organs)
                            "organs"         organs})))
             (vec (get cs "findings"))
             committees)]
        {"cell_state" (assoc cs "findings" findings "phase" phase-woven)}))))
