(ns infra-utility-connect.cells.activation-test.state-machine
  "1:1 cljc port of cells/activation_test/cell.py.
  Simple two-step state machine: init → process (complete).
  String keys mirror the Python dict structure.")

(defn- s* [state] (get state "utility_state" {}))

(defn init-state [state]
  {"utility_state" {"phase" "init"
                    "projectId" (get state "projectId" "unknown")
                    "completionPct" 0}
   "next_node" "process"})

(defn process-state [state]
  {"utility_state" (assoc (s* state)
                           "phase" "complete"
                           "completionPct" 100)
   "next_node" "end"})

(defn run-chain [input-state]
  (reduce (fn [s f] (merge s (f s)))
          input-state
          [init-state process-state]))
