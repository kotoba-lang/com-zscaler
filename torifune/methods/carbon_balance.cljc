(ns torifune.methods.carbon-balance
  "torifune 鳥船 — zero-net-carbon propellant accounting (G2). 1:1 Clojure port of
  methods/carbon_balance.py (ADR-2606162355).

  net = Σ over stages (stage prop-mass-kg × carbon-balance of the propellant that fuels its
  engine). G2 pass iff every fueled propellant is net ≤ 0 and none is disfavored."
  (:require [clojure.string :as str]
            [torifune.methods.ascent-sim :as core]))

(def disfavored #{":disfavored-fossil" ":disfavored-hypergolic-toxic"})

(defn propellant-for-engine [nodes edges engine-id]
  (some (fn [e] (when (and (= ":fuels" (get e ":en/kind")) (= engine-id (get e ":en/to")))
                  (get nodes (get e ":en/from")))) edges))

(defn balance [nodes edges]
  (let [stages (->> (core/node-ids nodes) (map #(get nodes %))
                    (filter #(= ":stage" (get % ":organism/kind"))))
        rows (keep
              (fn [s]
                (let [eng (core/engine-for-stage nodes edges (get s ":organism/id"))
                      prop (when eng (propellant-for-engine nodes edges (get eng ":organism/id")))]
                  (when prop
                    (let [cb (double (get prop ":propellant/carbon-balance" 0.0))
                          mass (double (get s ":stage/prop-mass-kg" 0.0))]
                      {:stage (get s ":organism/id") :prop (get prop ":organism/label")
                       :kind (get prop ":propellant/kind") :carbon_per_kg cb :mass_kg mass
                       :contrib_kgco2e (* cb mass)}))))
              stages)
        net (reduce + 0.0 (map :contrib_kgco2e rows))
        used-disfavored (->> rows (filter #(contains? disfavored (:kind %)))
                             (mapv (fn [r] [(:stage r) (:kind r)])))]
    {:rows rows :net_kgco2e net :used_disfavored used-disfavored
     :g2_pass (and (<= net 0.0) (empty? used-disfavored))}))

(defn- fmt0 [v] (format "%.0f" (double v)))
(defn- fmtg [v] (let [d (double v)] (if (== d (Math/rint d)) (str (long d)) (str d))))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report-md [_nodes _edges res]
  (let [L (transient [])]
    (conj! L "# torifune 鳥船 — propellant carbon balance (G2 zero-net-carbon)\n")
    (conj! L (str "> **G2 — zero-net-carbon propellant only.** Green-H₂ hydrolox / kamado-synthetic "
                  "methalox (net≤0); fossil + toxic-hypergolic are DISFAVORED. Carbon is MEASURED, "
                  "never assumed (Rider §2(d)).\n"))
    (conj! L "\n| stage | propellant | kind | kgCO₂e/kg | prop mass (kg) | contribution (kgCO₂e) |")
    (conj! L "|---|---|---|---:|---:|---:|")
    (doseq [r (:rows res)]
      (conj! L (str "| " (:stage r) " | " (:prop r) " | " (lstrip-colon (:kind r)) " | "
                    (fmtg (:carbon_per_kg r)) " | " (fmt0 (:mass_kg r)) " | "
                    (fmt0 (:contrib_kgco2e r)) " |")))
    (conj! L (str "\n**Net mission carbon balance: " (fmt0 (:net_kgco2e res)) " kgCO₂e** — "
                  (if (:g2_pass res) "✅ G2 PASS (net ≤ 0)" "❌ G2 FAIL") "\n"))
    (if (seq (:used_disfavored res))
      (conj! L (str "\n⚠️ **disfavored propellant fueled:** "
                    (str/join ", " (map (fn [[sid k]] (str sid " (" (lstrip-colon k) ")"))
                                        (:used_disfavored res))) "\n"))
      (conj! L "\n_No disfavored propellant fueled into the vehicle._\n"))
    (conj! L "\n---\n_torifune 鳥船 · ADR-2606162355 · zero-net-carbon · measured not assumed._\n")
    (str/join "\n" (persistent! L))))

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
           res (balance nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "carbon-report.md") (report-md nodes edges res))
       (println (str "torifune carbon: net " (fmt0 (:net_kgco2e res)) " kgCO2e ("
                     (if (:g2_pass res) "G2 PASS" "G2 FAIL") ") → "
                     (clojure.java.io/file outdir "carbon-report.md")))
       0)))
