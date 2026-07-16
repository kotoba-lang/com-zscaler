(ns maps.methods.bpmn-run
  "Generic BPMN process execution engine. Takes the process-def explicitly
   (no global ref), so any actor's BPMN can run Zeebe-free on the kotoba
   Datom log."
  (:require [maps.methods.bpmn-xml :as bx]))

(defn start-instance
  "Create a process instance with a token at the start node."
  [process-def instance-id]
  (let [start (bx/start-node process-def)]
    {:instance-id instance-id
     :token start
     :vars {}
     :history [start]
     :status :running}))

(defn step
  "Advance the instance one node.
   handlers = {task-type (fn [vars] => output-map)}
   cond-eval = (fn [cond-string vars] => boolean)
   Missing handler for a task -> no-op (fail-open).
   :else branch on a gateway always matches."
  [process-def inst handlers cond-eval]
  (let [node-id (:token inst)
        node (get process-def node-id)]
    (case (:type node)
      :end (assoc inst :status (or (:status node) :done))

      :start (let [nxt (:next node)]
               (-> inst (assoc :token nxt) (update :history conj nxt)))

      :gateway (let [nxt (some (fn [[cond-key target]]
                                 (when (or (= :else cond-key)
                                           (cond-eval cond-key (:vars inst)))
                                   target))
                               (:branches node))]
                 (if nxt
                   (-> inst (assoc :token nxt) (update :history conj nxt))
                   (assoc inst :status :failed)))

      :task (let [h (get handlers (:task-type node))
                  out (if h (or (h (:vars inst)) {}) {})
                  nxt (:next node)]
              (-> inst
                  (update :vars merge out)
                  (assoc :token nxt)
                  (update :history conj nxt)))

      ;; unknown node type -> terminate defensively
      (assoc inst :status :failed))))

(defn run
  "Drive the instance to an :end node (or :status :step-limit when max-steps hit).
   Appends visited nodes to :history."
  ([process-def inst handlers cond-eval]
   (run process-def inst handlers cond-eval 100))
  ([process-def inst handlers cond-eval max-steps]
   (loop [i inst, n 0]
     (let [node (get process-def (:token i))]
       (cond
         (= :end (:type node)) (step process-def i handlers cond-eval)
         (>= n max-steps) (assoc i :status :step-limit)
         :else (recur (step process-def i handlers cond-eval) (inc n)))))))
