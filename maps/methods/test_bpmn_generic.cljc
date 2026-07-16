(ns maps.methods.test-bpmn-generic
  (:require [clojure.test :refer [deftest is]]
            [maps.methods.bpmn-xml :as bxml]
            [maps.methods.bpmn-run :as brun]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (def ^:private repo-root
     ;; .../root/20-actors/maps/methods/<this>.cljc → up 4 = root
     (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile
         .getParentFile .getParentFile)))

#?(:clj
   (def ^:private get-item-bpmn
     (io/file repo-root "00-contracts" "bpmn" "com" "etzhayyim" "jp-ashiba" "getItem.bpmn")))

#?(:clj
   (def ^:private gateway-bpmn
     (io/file repo-root "00-contracts" "bpmn" "com" "etzhayyim" "maps" "advanceCoverage.bpmn")))

#?(:clj
   (def ^:private plain-task-bpmn
     (io/file repo-root "00-contracts" "bpmn" "com" "etzhayyim" "govUSA-PA" "transport" "procedure-subnational-2.bpmn")))

(deftest test-parse-getItem
  #?(:clj
     (let [xml (slurp get-item-bpmn)
           def (bxml/parse-bpmn xml)]
       (is (some #(= :start (:type (val %))) def) "has a :start node")
       (is (= 1 (count (filter #(= :task (:type (val %))) def))) "exactly one :task")
       (is (= "xrpc.com.etzhayyim.apps.jpAshiba.getItem"
              (:task-type (some #(when (= :task (:type %)) %) (vals def))))
           "task-type matches the file's zeebe taskDefinition")
       (is (some #(= :end (:type (val %))) def) "has an :end node"))))

(deftest test-run-getItem
  #?(:clj
     (let [xml (slurp get-item-bpmn)
           def (bxml/parse-bpmn xml)
           inst (brun/start-instance def "test-getItem-1")
           result (brun/run def inst {} (constantly false) 10)]
       (is (= :done (:status result)) "reaches done"))))

(deftest test-parse-gateway
  #?(:clj
     (let [xml (slurp gateway-bpmn)
           def (bxml/parse-bpmn xml)]
       (is (>= (count (:branches (some #(when (= :gateway (:type %)) %)
                                       (vals def))))
               2)
           "gateway has >= 2 branches"))))

(deftest test-run-gateway
  #?(:clj
     (let [xml (slurp gateway-bpmn)
           def (bxml/parse-bpmn xml)
           inst (brun/start-instance def "test-gateway-1")
           cond-eval (fn [cond-str _vars]
                       (case cond-str
                         "=gapCount &gt; 0" true
                         "=gapCount &lt;= 0" false
                         false))
           result (brun/run def inst {} cond-eval 20)]
       (is (= :done (:status result)) "reaches done"))))

(deftest test-parse-plain-tasks
  #?(:clj
     (let [xml (slurp plain-task-bpmn)
           def (bxml/parse-bpmn xml)]
       (is (= 1 (count (filter #(= :start (:type (val %))) def))) "one start")
       (is (= 3 (count (filter #(= :task (:type (val %))) def))) "three tasks")
       (is (= 1 (count (filter #(= :end (:type (val %))) def))) "one end")
       (let [targets (concat
                       (keep #(when (#{:start :task} (:type (val %))) (:next (val %))) def)
                       (mapcat #(map second (:branches (val %))) (filter #(= :gateway (:type (val %))) def)))]
         (is (every? #(contains? def %) targets) "every target resolves")))))

(deftest test-run-plain-tasks
  #?(:clj
     (let [xml (slurp plain-task-bpmn)
           def (bxml/parse-bpmn xml)
           inst (brun/start-instance def "test-plain-tasks-1")
           result (brun/run def inst {} (constantly false) 10)]
       (is (= :done (:status result)) "reaches done"))))
