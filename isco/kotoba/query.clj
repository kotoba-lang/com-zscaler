(ns query
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- load-seed [path]
  (edn/read-string (slurp path)))

(defn- find-attr [attrs pred]
  (first (filter pred attrs)))

(defn- code-attr [attrs]
  (:db/ident (find-attr attrs #(= (:db/unique %) :db.unique/identity))))

(defn- suffix-attr [attrs suffix]
  (:db/ident (find-attr attrs #(= (name (:db/ident %)) suffix))))

(defn- build-indexes [seed code-attr name-attr parent-attr level-attr]
  {:by-id (into {} (map (juxt :db/id identity) seed))
   :by-code (into {} (map (juxt #(get % code-attr) identity) seed))
   :children (group-by #(get % parent-attr) seed)
   :code-attr code-attr
   :name-attr name-attr
   :parent-attr parent-attr
   :level-attr level-attr})

(defn- get-code [row idx] (get row (:code-attr idx)))
(defn- get-name [row idx] (get row (:name-attr idx)))
(defn- get-level [row idx] (get row (:level-attr idx)))

(defn- print-row [row idx]
  (println (get-code row idx) (get-name row idx)))

(defn- census [idx]
  (let [rows (vals (:by-code idx))
        total (count rows)
        levels (frequencies (map #(get-level % idx) rows))
        leaves (count (filter #(empty? (get-in idx [:children (:db/id %)] [])) rows))]
    (println "total:" total)
    (doseq [[level n] (sort levels)]
      (println "level" level ":" n))
    (println "leaves:" leaves)))

(defn- cmd-children [idx c]
  (if-let [row (get-in idx [:by-code c])]
    (doseq [child (sort-by #(get-code % idx) (get-in idx [:children (:db/id row)] []))]
      (print-row child idx))
    (println "not found:" c)))

(defn- cmd-ancestors [idx c]
  (if-let [start (get-in idx [:by-code c])]
    (loop [row start visited #{}]
      (when (and row (not (visited (:db/id row))))
        (print-row row idx)
        (recur (get-in idx [:by-id (get row (:parent-attr idx))])
               (conj visited (:db/id row)))))
    (println "not found:" c)))

(defn- cmd-tree [idx c]
  (if-let [start (get-in idx [:by-code c])]
    (let [counter (atom 0)]
      (letfn [(walk [row depth]
                (when (< @counter 500)
                  (println (str (str/join (repeat depth "  "))
                                (get-code row idx)
                                " "
                                (get-name row idx)))
                  (swap! counter inc)
                  (doseq [child (sort-by #(get-code % idx)
                                         (get-in idx [:children (:db/id row)] []))]
                    (walk child (inc depth)))))]
        (walk start 0)))
    (println "not found:" c)))

;; depth = number of parent hops to a root (level-value-agnostic: works for both
;; keyword levels :major.. and numeric levels 1..). Cycle-guarded.
(defn- node-depth [idx row]
  (loop [r row d 0 seen #{}]
    (let [p (get-in idx [:by-id (get r (:parent-attr idx))])]
      (if (and p (not (seen (:db/id r))))
        (recur p (inc d) (conj seen (:db/id r)))
        d))))

(defn- cmd-gaps [idx]
  (let [rows (vals (:by-code idx))
        deepest (apply max 0 (map #(node-depth idx %) rows))
        gaps (sort-by #(get-code % idx)
                      (filter #(and (< (node-depth idx %) deepest)
                                    (empty? (get-in idx [:children (:db/id %)] [])))
                              rows))]
    (doseq [row gaps]
      (println "GAP" (get-code row idx) (get-level row idx) (get-name row idx)))
    (println "gaps:" (count gaps))))

(defn- usage []
  (println "Usage: bb query.clj <seed.edn> <command> [code]"))

(defn -main [& args]
  (if (>= (count args) 2)
    (let [[seed-path command code] args
          d (load-seed seed-path)
          attrs (:attributes d)
          ca (code-attr attrs)
          na (suffix-attr attrs "name")
          pa (suffix-attr attrs "parent")
          la (suffix-attr attrs "level")
          idx (build-indexes (:seed d) ca na pa la)]
      (case command
        "census" (census idx)
        "children" (cmd-children idx code)
        "ancestors" (cmd-ancestors idx code)
        "tree" (cmd-tree idx code)
        "gaps" (cmd-gaps idx)
        (println "unknown command:" command)))
    (usage)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
