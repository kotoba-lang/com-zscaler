(ns maps.methods.bpmn-xml
  "Pure BPMN 2.0 XML parser — regex-based, dependency-free.
   Produces a process-def map keyed by keyword node-ids."
  (:require [clojure.string :as str]))

(defn- get-attr
  "Extract a double-quoted attribute value from an XML element string."
  [block aname]
  (second (re-find (re-pattern (str aname "=\"([^\"]+)\"")) block)))

(defn- tag-regex
  "Regex that matches both self-closing and non-self-closing bpmn:TAG elements.
   The tag name is anchored with a lookahead so it ends exactly
   (next char must be whitespace, > or /) — e.g. 'task' won't match 'taskDefinition'."
  [tag]
  (re-pattern (str "(?s)<bpmn:" tag "(?=[\\s/>])(?:[^>]*[^/])?>.*?</bpmn:" tag ">|<bpmn:" tag "(?=[\\s/>])[^>]*/>")))

(def ^:private task-tags
  ["task" "userTask" "scriptTask" "sendTask" "receiveTask"
   "manualTask" "businessRuleTask" "serviceTask" "callActivity"])

(defn parse-bpmn
  "Parse a BPMN 2.0 XML string into a process-def map:
   node-id-keyword -> {:type :start|:task|:gateway|:end
                       :next   target-kw       ; for :start and :task
                       :task-type string       ; for :task
                       :branches [[cond target] ...] ; for :gateway
                       :name string}"
  [xml-string]
  (let [starts    (re-seq (tag-regex "startEvent") xml-string)
        ends      (re-seq (tag-regex "endEvent") xml-string)
        tasks     (mapcat #(re-seq (tag-regex %) xml-string) task-tags)
        gateways  (re-seq (tag-regex "exclusiveGateway") xml-string)
        flows     (re-seq (tag-regex "sequenceFlow") xml-string)

        start-ids (mapv #(keyword (get-attr % "id")) starts)

        nodes (->> (concat 
                    (map #(vector :start %) starts)
                    (map #(vector :end %) ends)
                    (map #(vector :task %) tasks)
                    (map #(vector :gateway %) gateways))
                   (map (fn [[type block]]
                          (let [id (get-attr block "id")
                                name (get-attr block "name")
                                task-type (when (= type :task)
                                            (second (re-find #"<zeebe:taskDefinition type=\"([^\"]+)\"" block)))
                                entry (cond-> {:type type :name name}
                                        (= type :task) (assoc :task-type task-type))]
                            [(keyword id) entry])))
                   (into {}))

        explicit-flow-map (reduce (fn [m block]
                                    (let [src (get-attr block "sourceRef")
                                          tgt (get-attr block "targetRef")
                                          cond-text (second (re-find #"<bpmn:conditionExpression[^>]*>([^<]+)</bpmn:conditionExpression>" block))
                                          cond-key (if cond-text cond-text :else)]
                                      (update m src conj [cond-key (keyword tgt)])))
                                  {}
                                  flows)

        ;; When the file has no explicit sequence flows, infer linear document-order links.
        implicit-flow-map (when (empty? explicit-flow-map)
                            (->> (concat
                                  (map #(vector :start %) starts)
                                  (map #(vector :end %) ends)
                                  (map #(vector :task %) tasks)
                                  (map #(vector :gateway %) gateways))
                                 (sort-by #(str/index-of xml-string (second %)))
                                 (map #(keyword (get-attr (second %) "id")))
                                 (partition 2 1)
                                 (map (fn [[a b]] [(name a) [[:else b]]]))
                                 (into {})))

        flow-map (if (seq explicit-flow-map) explicit-flow-map implicit-flow-map)]

    (with-meta
      (reduce-kv (fn [m node-id node]
                   (let [outgoing (get flow-map (name node-id))]
                     (case (:type node)
                       (:start :task) (let [single (first outgoing)]
                                        (assoc m node-id (assoc node :next (second single))))
                       :gateway (let [branches (if (some #(= :else (first %)) outgoing)
                                                 (let [cond-branches (remove #(= :else (first %)) outgoing)
                                                       else-branch (filter #(= :else (first %)) outgoing)]
                                                   (vec (concat cond-branches else-branch)))
                                                 (vec outgoing))]
                                  (assoc m node-id (assoc node :branches branches)))
                       (assoc m node-id node))))
                 {}
                 nodes)
      {:start-ids start-ids})))

(defn start-node
  "Return the keyword id of the first startEvent in the process-def (document order)."
  [process-def]
  (first (:start-ids (meta process-def))))
