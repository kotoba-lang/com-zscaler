#!/usr/bin/env bb
(ns junkan.methods.country-region-actors
  "Design registry helpers for country/region system-dynamics loop actors.

  This does not run live ingestion. It validates actor designs: parent chains,
  required gates, domain inheritance, region fission coverage, and aggregate-only
  posture. The registry lets one pattern scale from IN to country/region actors."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def required-gates
  #{:analysis-only
    :hypothesis-only
    :aggregate-only
    :non-essentialist
    :map-not-ranking
    :candidate-not-directive})

(def scopes #{:world :country :region :city})
(def statuses #{:designed :seeded :active :retired})

(defn registry [path]
  (edn/read-string (slurp path)))

(defn domains [rows]
  (into {} (map (juxt :id identity) (filter #(= :loop-domain (:type %)) rows))))

(defn actors [rows]
  (vec (filter #(= :loop-actor (:type %)) rows)))

(defn actors-by-id [rows]
  (into {} (map (juxt :id identity) (actors rows))))

(defn inherited-domain [domain-map actor]
  (let [domain-id (first (:domains actor))]
    (get domain-map domain-id)))

(defn effective-stocks [domain-map actor]
  (if (= :inherit-domain (:stocks actor))
    (:stocks (inherited-domain domain-map actor))
    (:stocks actor)))

(defn effective-loops [domain-map actor]
  (if (= :inherit-domain (:loops actor))
    (:loops (inherited-domain domain-map actor))
    (:loops actor)))

(defn parent-chain [actor-map actor]
  (loop [a actor acc [] seen #{}]
    (let [p (:parent a)]
      (cond
        (nil? p) acc
        (seen p) (conj acc {:cycle p})
        :else (if-let [pa (get actor-map p)]
                (recur pa (conj acc p) (conj seen p))
                (conj acc {:missing p}))))))

(defn validate [rows]
  (let [errs (atom [])
        warns (atom [])
        domain-map (domains rows)
        actor-map (actors-by-id rows)
        err! #(swap! errs conj %)
        warn! #(swap! warns conj %)]
    (doseq [d (vals domain-map)]
      (when-not (seq (:stocks d)) (err! (str (:id d) " domain missing stocks")))
      (when-not (seq (:loops d)) (err! (str (:id d) " domain missing loops")))
      (when-not (seq (:source-classes d)) (err! (str (:id d) " domain missing source classes"))))
    (doseq [a (actors rows)]
      (when-not (contains? scopes (:scope a)) (err! (str (:id a) " invalid scope " (:scope a))))
      (when-not (contains? statuses (:status a)) (err! (str (:id a) " invalid status " (:status a))))
      (when (str/blank? (str (:id a))) (err! "actor missing id"))
      (when-not (every? domain-map (:domains a))
        (err! (str (:id a) " references unknown domain " (:domains a))))
      (let [missing-gates (remove (set (:gates a)) required-gates)]
        (when (seq missing-gates)
          (err! (str (:id a) " missing required gates " (vec missing-gates)))))
      (doseq [p (parent-chain actor-map a)]
        (cond
          (:missing p) (err! (str (:id a) " missing parent " (:missing p)))
          (:cycle p) (err! (str (:id a) " parent cycle at " (:cycle p)))))
      (when (and (= :region (:scope a)) (nil? (:region a)))
        (err! (str (:id a) " region actor missing :region")))
      (when (and (= :country (:scope a)) (:region a))
        (err! (str (:id a) " country actor should not set :region")))
      (when-not (seq (:languages a))
        (when-not (= :world (:scope a))
          (warn! (str (:id a) " has no language coverage"))))
      (when-not (seq (effective-stocks domain-map a))
        (err! (str (:id a) " has no effective stocks")))
      (when-not (seq (effective-loops domain-map a))
        (err! (str (:id a) " has no effective loops"))))
    (let [ids (map :id (actors rows))]
      (doseq [[id n] (frequencies ids) :when (> n 1)]
        (err! (str "duplicate actor id " id " (" n "x)"))))
    {:errors @errs
     :warnings @warns
     :stats {:domains (count domain-map)
             :actors (count (actors rows))
             :by-scope (frequencies (map :scope (actors rows)))
             :by-status (frequencies (map :status (actors rows)))}}))

(defn hierarchy [rows]
  (let [actor-map (actors-by-id rows)]
    (->> (actors rows)
         (map (fn [a]
                {:id (:id a)
                 :scope (:scope a)
                 :jurisdiction (:jurisdiction a)
                 :region (:region a)
                 :parent (:parent a)
                 :children (vec (sort (map :id (filter #(= (:id a) (:parent %)) (vals actor-map)))))
                 :status (:status a)}))
         (sort-by :id)
         vec)))

(defn render-design [rows]
  (let [{:keys [errors warnings stats]} (validate rows)]
    (str "# junkan country/region loop actors\n\n"
         "Designed actors: " (:actors stats) " · domains: " (:domains stats)
         " · scope " (pr-str (:by-scope stats))
         " · status " (pr-str (:by-status stats)) "\n\n"
         "Required gates: " (str/join ", " (map name (sort required-gates))) "\n\n"
         "## Hierarchy\n\n"
         "| actor | scope | jurisdiction | region | parent | status |\n"
         "|---|---|---|---|---|---|\n"
         (str/join "\n"
                   (for [a (hierarchy rows)]
                     (str "| " (:id a)
                          " | " (name (:scope a))
                          " | " (:jurisdiction a)
                          " | " (or (some-> (:region a) name) "")
                          " | " (or (:parent a) "")
                          " | " (name (:status a)) " |")))
         "\n\n## Validation\n\n"
         "- errors: " (count errors) "\n"
         "- warnings: " (count warnings) "\n")))

(defn -main [& [path]]
  (let [rows (registry (or path "20-actors/junkan/kotoba/seed.country-region-loop-actors.edn"))]
    (println (render-design rows))
    (let [{:keys [errors]} (validate rows)]
      (System/exit (if (empty? errors) 0 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
