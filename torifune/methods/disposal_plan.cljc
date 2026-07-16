(ns torifune.methods.disposal-plan
  "torifune 鳥船 — debris-responsibility / disposal plan (G5; couples hoshimori). 1:1 Clojure
  port of methods/disposal_plan.py (ADR-2606162355).

  Every :mission MUST carry at least one :disposes edge to a :disposal-plan; a mission with
  none is REFUSED (throws). Emits total added deorbit-debt as a hoshimori-consumable input."
  (:require [clojure.string :as str]
            [torifune.methods.ascent-sim :as core]))

(defn plan [nodes edges]
  (let [missions (filter #(= ":mission" (get % ":organism/kind")) (vals nodes))
        out (mapv
             (fn [m]
               (let [mid (get m ":organism/id")
                     dplans (->> edges
                                 (filter #(and (= ":disposes" (get % ":en/kind"))
                                               (= mid (get % ":en/from"))
                                               (contains? nodes (get % ":en/to"))))
                                 (mapv #(get nodes (get % ":en/to"))))]
                 (when (empty? dplans)
                   (throw (ex-info (str "G5 violation: mission " mid " has NO disposal plan — refused")
                                   {:gate :g5})))
                 {:mission mid :label (get m ":organism/label" mid)
                  :plans (mapv (fn [d] [(get d ":organism/id") (get d ":disposal/method")
                                        (double (get d ":disposal/deorbit-debt" 0.0))]) dplans)
                  :deorbit_debt (reduce + 0.0 (map #(double (get % ":disposal/deorbit-debt" 0.0))
                                                   dplans))}))
             missions)]
    {:missions out :total_deorbit_debt (reduce + 0.0 (map :deorbit_debt out))}))

(defn- fmtg [v] (let [d (double v)] (if (== d (Math/rint d)) (str (long d)) (str d))))

(defn emit-edn [res]
  (let [L (transient [])]
    (conj! L ";; torifune 鳥船 — GENERATED disposal plan (ADR-2606162355). DO NOT hand-edit.")
    (conj! L ";; G5 debris-responsibility — deorbit-debt is an INPUT to hoshimori stewardship.")
    (conj! L "[")
    (doseq [m (:missions res)]
      (doseq [[pid method debt] (:plans m)]
        (conj! L (str "{:disposal/of \"" (:mission m) "\" :disposal/plan \"" pid "\" "
                      ":disposal/method " method " :disposal/deorbit-debt " (fmtg debt) " "
                      ":routed-to :hoshimori-stewardship}"))))
    (conj! L "]")
    (str (str/join "\n" (persistent! L)) "\n")))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (clojure.java.io/file (or (System/getenv "TORIFUNE_ACTOR_DIR") "20-actors/torifune"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-ama-vehicle.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (core/load-file* seed)
           res (plan nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "disposal-plan.kotoba.edn") (emit-edn res))
       (println (str "torifune disposal: " (count (:missions res)) " mission(s), total deorbit-debt "
                     (fmtg (:total_deorbit_debt res)) " → "
                     (clojure.java.io/file outdir "disposal-plan.kotoba.edn")))
       0)))
