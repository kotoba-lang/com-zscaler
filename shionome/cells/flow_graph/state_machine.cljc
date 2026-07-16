(ns shionome.cells.flow-graph.state-machine
  "Phase state machine for the 潮目 shionome flow_graph cell.
  1:1 port of cells/flow_graph/state_machine.py (ADR-2606072200). Indexes screened flows into
  per-bucket net inflow/outflow totals (capital-movement kinds only). Self-contained."
  (:require [clojure.string :as str]))

(def capital-movement-kinds #{"rotation" "fund-inflow" "fund-outflow" "fx-flow"})

(def state-defaults {"phase" "init" "flows" [] "net" {}})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- pyround4 [x] (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn transition-to-indexed [state]
  (let [cs (cell-state state)
        flows (get state "flows" (get cs "flows"))
        net (reduce (fn [net f]
                      (if-not (contains? capital-movement-kinds (kw (get f "kind")))
                        net
                        (let [mag (double (or (get f "magnitude") 0.0))
                              tgt (get f "target") src (get f "source")
                              net (if (and tgt (not= tgt "external")) (update net tgt (fnil + 0.0) mag) net)
                              net (if (and src (not= src "external")) (update net src (fnil - 0.0) mag) net)]
                          net)))
                    {} flows)]
    {"cell_state" (assoc cs "flows" flows
                         "net" (into {} (map (fn [[k v]] [k (pyround4 v)]) net))
                         "phase" "indexed")}))

(defn solve [_input-state]
  (throw (ex-info "shionome R0 scaffold: activate flow_graph via Council ADR (post-2606072200 ratification)" {:scaffold true})))
