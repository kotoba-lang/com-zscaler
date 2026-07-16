#!/usr/bin/env bb
;; uzu 渦 — seed ↔ ontology integrity validator (self-validating substrate).
(ns uzu.methods.validate
  "validate.cljc — uzu 渦 integrity checker (ADR-2606211500).

  Verifies the seed against the ontology so a malformed substrate is caught structurally,
  not at run time — the self-validating-seed pattern (cf. jinushi/tatara). Pure checks over
  classified rows + the ontology map; :clj only loads the files. Returns
  {:ok bool :errors [...] :warnings [...] :stats {...}}.

  The checks also defend the design invariants directly:
    • signals/preferences in range, ids unique, tape contiguous            (well-formedness)
    • flow :class ∈ enum, magnitude > 0, native :unit present              (measurement)
    • WITHIN-CLASS UNIT CONSISTENCY — all flows of a class share one unit   (I2: totals-by-class
                                                                            is only meaningful if so)
    • circulation endpoints resolve + :cross-class flag matches the actual
      from/to class difference                                             (the coupling graph)
    • circulation is a closed loop (every flow has an out-edge)            (open system circulates)"
  (:require [clojure.string :as str]
            [uzu.methods.uzu-edn :as ue]
            #?(:clj [clojure.edn :as edn])
            #?(:clj [clojure.java.io :as io])))

(defn- in01? [x] (and (number? x) (<= 0.0 (double x) 1.0)))
(defn- dups [xs] (->> xs frequencies (filter (fn [[_ n]] (> n 1))) (map key) vec))

(defn validate
  "Validate classified seed {:tape :organisms :flows :edges} against the ontology map.
  Returns {:ok :errors :warnings :stats}."
  [{:keys [tape organisms flows edges]} ontology]
  (let [regime-enum (get-in ontology [:enums :regime])
        class-enum  (get-in ontology [:enums :flow-class])
        unit-of     (into {} (map (fn [[c m]] [c (:unit m)]) (:unit-classes ontology)))
        flow-ids    (set (map :id flows))
        flow-class  (into {} (map (juxt :id :class) flows))
        E (atom []) Wn (atom [])
        err! #(swap! E conj %)
        warn! #(swap! Wn conj %)]
    ;; — tape —
    (doseq [s tape]
      (when-not (contains? regime-enum (:regime s)) (err! (str "tape step " (:step s) ": bad regime " (:regime s))))
      (when-not (and (in01? (:nutrient (:signal s))) (in01? (:threat (:signal s))))
        (err! (str "tape step " (:step s) ": signal out of 0..1"))))
    (let [steps (map :step tape)]
      (when (seq (dups steps)) (err! (str "duplicate tape steps: " (dups steps))))
      (when (and (seq steps) (not= (sort steps) (range (count steps))))
        (warn! "tape steps are not contiguous from 0")))
    ;; — organisms —
    (when (seq (dups (map :id organisms))) (err! (str "duplicate organism ids: " (dups (map :id organisms)))))
    (doseq [o organisms]
      (when-not (and (in01? (:nutrient (:prefs o))) (in01? (:threat (:prefs o))))
        (err! (str "organism " (:id o) ": prefs out of 0..1")))
      (when-not (and (number? (:temp o)) (pos? (:temp o))) (err! (str "organism " (:id o) ": temp must be > 0")))
      (when-not (and (number? (:energy0 o)) (pos? (:energy0 o))) (err! (str "organism " (:id o) ": energy0 must be > 0"))))
    ;; — flows —
    (when (seq (dups (map :id flows))) (err! (str "duplicate flow ids: " (dups (map :id flows)))))
    (doseq [f flows]
      (when-not (contains? class-enum (:class f)) (err! (str "flow " (:id f) ": bad class " (:class f))))
      (when-not (and (number? (:magnitude f)) (pos? (:magnitude f))) (err! (str "flow " (:id f) ": magnitude must be > 0")))
      (when (str/blank? (str (:unit f))) (err! (str "flow " (:id f) ": missing native unit"))))
    ;; — within-class unit consistency (I2) —
    (doseq [[cls fs] (group-by :class flows)]
      (let [units (set (map :unit fs))
            canon (get unit-of cls)]
        (when (> (count units) 1)
          (err! (str "class " cls " mixes units " units " — totals-by-class would be meaningless")))
        (when (and canon (not (contains? units canon)))
          (warn! (str "class " cls " unit " units " ≠ ontology unit " canon)))))
    ;; — circulation —
    (doseq [e edges]
      (when-not (flow-ids (:from e)) (err! (str "edge from unknown flow " (:from e))))
      (when-not (flow-ids (:to e)) (err! (str "edge to unknown flow " (:to e))))
      (when (and (flow-ids (:from e)) (flow-ids (:to e)))
        (let [actually-cross (not= (flow-class (:from e)) (flow-class (:to e)))]
          (when (not= (boolean (:cross-class e)) actually-cross)
            (err! (str "edge " (:from e) "→" (:to e) ": cross-class flag " (:cross-class e)
                       " ≠ actual " actually-cross))))))
    (let [out (set (map :from edges))
          orphans (remove out flow-ids)]
      (when (seq orphans) (warn! (str "flows with no out-edge (open leak): " (vec orphans)))))
    {:ok (empty? @E)
     :errors @E
     :warnings @Wn
     :stats {:tape (count tape) :organisms (count organisms)
             :flows (count flows) :edges (count edges)
             :classes (count (group-by :class flows))}}))

#?(:clj
   (defn validate-files
     "Load the seed + ontology from disk and validate."
     [seed-path ontology-path]
     (let [rows (edn/read-string (slurp seed-path))
           ont (ue/ontology-map (edn/read-string (slurp ontology-path)))
           classified {:tape (->> rows (filter #(= (:type %) :world-step)) (sort-by :step) vec)
                       :organisms (vec (filter #(= (:type %) :organism) rows))
                       :flows (vec (filter #(= (:type %) :flow) rows))
                       :edges (vec (filter #(= (:type %) :circulation) rows))}]
       (validate classified ont))))

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/uzu/kotoba/seed.edn")
           ont (or (second args) "20-actors/uzu/kotoba/ontology.uzu.edn")
           r (validate-files seed ont)]
       (println (str "uzu seed integrity: " (if (:ok r) "OK" "FAIL")
                     " — " (:errors r) " error(s), " (count (:warnings r)) " warning(s)"))
       (println (str "stats=" (:stats r)))
       (doseq [e (:errors r)] (println (str "  ERROR: " e)))
       (doseq [w (:warnings r)] (println (str "  warn:  " w)))
       (when-not (:ok r) (System/exit 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
