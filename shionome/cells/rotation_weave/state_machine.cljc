(ns shionome.cells.rotation-weave.state-machine
  "Phase state machine for the 潮目 shionome rotation_weave cell.
  1:1 port of cells/rotation_weave/state_machine.py (ADR-2606072200). Ranks bucket→bucket rotation
  pairs (capital-movement kinds) by magnitude. Aggregate, edge-primary; no per-bucket score. Self-contained."
  (:require [clojure.string :as str]))

(def capital-movement-kinds #{"rotation" "fund-inflow" "fund-outflow" "fx-flow"})

(def state-defaults {"phase" "init" "flows" [] "pairs" []})
(defn- cell-state [state] (merge state-defaults (get state "cell_state" {})))
(defn- kw [v] (-> (str (or v "")) (str/replace #"^:+" "") (str/split #"/") last str/lower-case))
(defn- pyround4 [x] (/ (Math/round (* (double x) 10000.0)) 10000.0))

(defn transition-to-woven [state]
  (let [cs (cell-state state)
        flows (get state "flows" (get cs "flows"))
        pairs (reduce (fn [m f]
                        (let [src (get f "source") tgt (get f "target")]
                          (if (and (contains? capital-movement-kinds (kw (get f "kind")))
                                   src tgt (not= src "external") (not= tgt "external") (not= src tgt))
                            (update m [src tgt] (fnil + 0.0) (double (or (get f "magnitude") 0.0)))
                            m)))
                      {} flows)
        woven (->> pairs
                   (map (fn [[[s t] mag]] [s t (pyround4 mag)]))
                   (sort-by (fn [[s t m]] [(- m) s t]))
                   vec)]
    {"cell_state" (assoc cs "flows" flows "pairs" woven "phase" "woven")}))

(defn solve [_input-state]
  (throw (ex-info "shionome R0 scaffold: activate rotation_weave via Council ADR (post-2606072200 ratification)" {:scaffold true})))
