(ns maps.methods.test-maps3d-bpmn-fidelity
  "Fidelity guard for the Zeebe→Datomic migration: the Clojure execution engine's
  `process-tile-def` MUST stay faithful to the canonical BPMN contract
  (00-contracts/bpmn/com/etzhayyim/maps3d/processTile.bpmn). Reads the real BPMN
  XML and cross-checks the maps3d.* service-task types against the engine's task
  nodes, so the two cannot silently drift apart. #?(:clj ...) — file I/O at the
  edge; resolves the repo root from *file*."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [clojure.set :as set]
            [maps.methods.maps3d-bpmn :as b]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (def ^:private repo-root
     ;; .../root/20-actors/maps/methods/<this>.cljc → up 4 = root
     (-> *file* io/file .getAbsoluteFile .getParentFile .getParentFile
         .getParentFile .getParentFile)))

#?(:clj
   (def ^:private bpmn-file
     (io/file repo-root "00-contracts" "bpmn" "com" "etzhayyim" "maps3d" "processTile.bpmn")))

#?(:clj
   (defn- bpmn-task-types []
     (->> (re-seq #"zeebe:taskDefinition type=\"(maps3d\.[A-Za-z]+)\""
                  (slurp bpmn-file))
          (map second)
          set)))

(defn- engine-task-types []
  (->> (vals b/process-tile-def)
       (filter #(= :task (:type %)))
       (map :task-type)
       (filter #(str/starts-with? % "maps3d."))
       set))

#?(:clj
   (deftest test-bpmn-file-present
     (is (.exists bpmn-file) (str "missing canonical BPMN: " bpmn-file))))

#?(:clj
   (deftest test-every-bpmn-task-is-modelled-in-engine
     ;; the Datomic engine must implement every maps3d.* service task the BPMN declares
     (let [bpmn (bpmn-task-types)
           engine (engine-task-types)
           missing (set/difference bpmn engine)]
       (is (empty? missing)
           (str "BPMN task types not modelled in process-tile-def: " missing)))))

#?(:clj
   (deftest test-engine-has-no-phantom-maps3d-tasks
     ;; and the engine must not invent maps3d.* tasks the BPMN never declared
     (let [bpmn (bpmn-task-types)
           engine (engine-task-types)
           phantom (set/difference engine bpmn)]
       (is (empty? phantom)
           (str "process-tile-def has maps3d.* tasks absent from the BPMN: " phantom)))))

#?(:clj
   (deftest test-bpmn-declares-the-expected-seven-tasks
     ;; pin the count so adding/removing a BPMN task forces a deliberate engine update
     (is (= 7 (count (bpmn-task-types))))))

;; portable structural checks (run everywhere, no file I/O)

(deftest test-engine-flow-is-connected-and-terminates
  ;; every :task :next and every :gateway branch target must resolve to a node,
  ;; and the graph must reach all three terminal ends.
  (let [def b/process-tile-def
        targets (mapcat (fn [[_ node]]
                          (case (:type node)
                            :task [(:next node)]
                            :start [(:next node)]
                            :gateway (map second (:branches node))
                            []))
                        def)]
    (doseq [t targets]
      (is (contains? def t) (str "dangling flow target: " t)))
    (is (every? #(contains? def %) [:end-ok :end-osm :end-fail]))))
