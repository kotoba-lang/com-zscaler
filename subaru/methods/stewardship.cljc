(ns subaru.methods.stewardship
  "subaru 昴 — orbital stewardship (G5; couples hoshimori + torifune G5). 1:1 Clojure port of
  methods/stewardship.py (ADR-2606162355).

  Every occupied :shell MUST carry a :disposes edge to a :disposal-plan (else throws). Checks
  darksat on the bus. Emits deorbit-debt as a hoshimori-consumable stewardship input."
  (:require [clojure.string :as str]
            [subaru.methods.link-budget :as core]))

(defn stewardship [nodes edges]
  (core/check-g1 nodes)
  (let [occupied (->> edges (filter #(= ":occupies" (get % ":en/kind"))) (mapv #(get % ":en/to")))
        rows (mapv
              (fn [sid]
                (let [dplans (->> edges
                                  (filter #(and (= ":disposes" (get % ":en/kind"))
                                                (= sid (get % ":en/from"))
                                                (contains? nodes (get % ":en/to"))))
                                  (mapv #(get nodes (get % ":en/to"))))]
                  (when (empty? dplans)
                    (throw (ex-info (str "G5 violation: occupied shell " sid
                                         " has NO disposal plan — refused") {:gate :g5})))
                  {:shell sid :label (get-in nodes [sid ":organism/label"] sid)
                   :plans (mapv (fn [d] [(get d ":organism/id") (get d ":disposal/method")]) dplans)
                   :deorbit_debt (reduce + 0.0 (map #(double (get % ":disposal/deorbit-debt" 0.0))
                                                    dplans))}))
              occupied)
        buses (filter #(= ":bus" (get % ":organism/kind")) (vals nodes))
        darksat-all (and (seq buses) (every? #(boolean (get % ":bus/darksat")) buses))]
    {:shells rows :total_deorbit_debt (reduce + 0.0 (map :deorbit_debt rows))
     :darksat_all darksat-all
     :g5_pass (and (every? #(<= (:deorbit_debt %) 0.0) rows) darksat-all)}))

(defn- fmtg [v] (let [d (double v)] (if (== d (Math/rint d)) (str (long d)) (str d))))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report-md [_nodes _edges res]
  (let [L (transient [])]
    (conj! L "# subaru 昴 — orbital stewardship report (G5; feeds hoshimori)\n")
    (conj! L (str "> **G5 — orbital stewardship.** Low-deorbit-debt orbit + mandatory disposal plan "
                  "+ night-sky brightness mitigation (darksat, Wellbecoming §1.13). subaru's "
                  "footprint feeds hoshimori's congestion integral and must REDUCE, not add to it.\n"))
    (conj! L "\n| occupied shell | disposal method(s) | deorbit-debt |")
    (conj! L "|---|---|---:|")
    (doseq [r (:shells res)]
      (let [methods (str/join ", " (map (fn [[_ m]] (lstrip-colon m)) (:plans r)))]
        (conj! L (str "| " (:label r) " | " methods " | " (fmtg (:deorbit_debt r)) " |"))))
    (conj! L (str "\n**Total deorbit-debt routed to hoshimori: " (fmtg (:total_deorbit_debt res))
                  "** · darksat applied to all buses: " (if (:darksat_all res) "✅" "❌") "\n"))
    (conj! L (str "\n**G5: " (if (:g5_pass res) "✅ PASS" "❌ FAIL") "**\n"))
    (conj! L "\n---\n_subaru 昴 · ADR-2606162355 · orbital-stewardship · couples hoshimori._\n")
    (str/join "\n" (persistent! L))))

#?(:clj
   (defn -main [& argv]
     (let [argv (vec argv)
           here (clojure.java.io/file (or (System/getenv "SUBARU_ACTOR_DIR") "20-actors/subaru"))
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (clojure.java.io/file (first argv))
                  (clojure.java.io/file here "data" "seed-constellation.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (clojure.java.io/file (nth argv (inc (.indexOf argv "--out"))))
                    (clojure.java.io/file here "out"))
           {:keys [nodes edges]} (core/load-file* seed)
           res (stewardship nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "stewardship-report.md") (report-md nodes edges res))
       (println (str "subaru stewardship: " (count (:shells res)) " occupied shell(s), debt "
                     (fmtg (:total_deorbit_debt res)) ", G5 " (if (:g5_pass res) "PASS" "FAIL")
                     " → " (clojure.java.io/file outdir "stewardship-report.md")))
       0)))
