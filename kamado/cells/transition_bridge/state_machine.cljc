(ns kamado.cells.transition-bridge.state-machine
  "cljc port of cells/transition_bridge/cell.py (ADR-2606051500).
  R0 scaffold — routes converted/dismantled assets to sibling actors.
  Routes, never adjudicates (G4). .solve() raises until Council activation.
  The ROUTES table is preserved as declarative data.")

;; Declarative routing table (R0: not yet executed)
;; kamado decommissions and synthesizes; it does not lobby.
(def routes
  {"converted-site"        ["hikari"]
   "dismantled-unit"       ["hodoki" "kanayama" "haraedo"]
   "remediated-land"       ["hikari" "mitsuho"]
   "fossil-policy-question" ["danjo" "moushibumi"]
   "displaced-worker"      ["displacement-dividend"]})  ;; ADR-2606032130, G9

(defn route-lookup
  "Return the route actors for a given asset-type key (declarative, R0 only)."
  [asset-type]
  (get routes asset-type []))

(defn solve [_state]
  (throw (ex-info "kamado R0 scaffold: activate transition_bridge via Council ADR (post-2606051500 ratification)"
                  {:cell :transition-bridge :actor :kamado :status :r0-scaffold
                   :routes routes})))
