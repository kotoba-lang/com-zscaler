(ns torifune.methods.ascent-sim
  "torifune 鳥船 — civilian ascent / staging GNC sim. 1:1 Clojure port of methods/ascent_sim.py
  (ADR-2606162355).

  Computes the staged Tsiolkovsky Δv budget + payload-to-orbit margin over the launch-vehicle
  ontology. The engineering core the other methods import.

  CONSTITUTIONAL (read before any change):
    G1 — CIVILIAN LAUNCH ONLY, NEVER a weapon-delivery / ballistic-strike vehicle. The
      trajectory class is restricted to {:ascent :orbit-insertion :rendezvous :deorbit} and the
      payload class to civilian classes — strike trajectories + munition/kinetic payloads are
      UNREPRESENTABLE (check-g1 throws). No targeting / impact-point solver exists here.
    G8 — sourcing honesty. Numbers are representative engineering estimates, never measured
      flight data (no Ama flight campaign exists; that is Council-gated).

  House style: Python ':…' keyword strings stay strings; pure fns; file I/O only at edges.
  Portable .cljc."
  (:require [clojure.string :as str]))

;; ── minimal EDN reader (subset) — mirrors ascent_sim.py _TOK/_tokens/_atom/_parse faithfully.
;; Keywords kept as \":ns/name\" strings (NOT clojure keywords) so the pipeline stays string-keyed.

(def ^:private tok-re
  #"[\s,]+|;[^\n]*|(\[|\]|\{|\}|\"(?:\\.|[^\"\\])*\"|[^\s,\[\]{}]+)")

(defn tokens [s]
  (let [m (re-matcher tok-re s)]
    ((fn step []
       (lazy-seq
        (when (.find m)
          (let [t (.group m 1)]
            (if (nil? t) (step) (cons t (step))))))))))

(defn atom-of [t]
  (cond
    (str/starts-with? t "\"")
    (-> (subs t 1 (dec (count t)))
        (str/replace "\\\"" "\"")
        (str/replace "\\\\" "\\"))
    (= t "true") true
    (= t "false") false
    (= t "nil") nil
    (str/starts-with? t ":") t
    :else
    (let [as-long (try (Long/parseLong t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
      (if (not= as-long ::nan)
        as-long
        (let [as-dbl (try (Double/parseDouble t) (catch #?(:clj Exception :cljs :default) _ ::nan))]
          (if (not= as-dbl ::nan) as-dbl t))))))

(def ^:private end-marker ::end)

(defn- parse-step [toks i]
  (let [t (nth toks i) i (inc i)]
    (cond
      (= t "[") (loop [i i out []]
                  (let [[x i] (parse-step toks i)]
                    (if (= x end-marker) [out i] (recur i (conj out x)))))
      (= t "{") (loop [i i out {}]
                  (let [[k i] (parse-step toks i)]
                    (if (= k end-marker) [out i]
                        (let [[v i] (parse-step toks i)] (recur i (assoc out k v))))))
      (or (= t "]") (= t "}")) [end-marker i]
      :else [(atom-of t) i])))

(defn read-edn [text]
  (let [toks (vec (tokens text))]
    (first (parse-step toks 0))))

;; ── constants ───────────────────────────────────────────────────────────────
(def g0 9.80665)

(def regime-dv
  {":leo-low" 9400.0 ":leo-high" 9600.0 ":sso" 9700.0
   ":meo" 11000.0 ":geo" 13000.0 ":heo" 12000.0})

(def civilian-traj #{":ascent" ":orbit-insertion" ":rendezvous" ":deorbit"})
(def civilian-payload #{":connectivity-sat" ":earth-observation-sat" ":science" ":crewed" ":cargo"})
(def banned-attrs [":traj/impact-point" ":traj/depressed" ":payload/yield"
                   ":payload/warhead" ":guidance/terminal" ":target/coords"])

(defn load-graph
  "Return {:nodes nodes-by-id :edges edges} preserving node first-touch order in ::node-order."
  [forms]
  (reduce
   (fn [{:keys [nodes] :as acc} f]
     (cond
       (not (map? f)) acc
       (contains? f ":organism/id")
       (let [nid (get f ":organism/id") had? (contains? nodes nid) nodes' (assoc nodes nid f)]
         (assoc acc :nodes (if had? (with-meta nodes' (meta nodes))
                               (vary-meta nodes' update ::node-order (fnil conj []) nid))))
       (and (contains? f ":en/from") (contains? f ":en/to")) (update acc :edges conj f)
       :else acc))
   {:nodes (with-meta {} {::node-order []}) :edges []}
   forms))

(defn node-ids [nodes] (or (::node-order (meta nodes)) (keys nodes)))

#?(:clj
   (defn load-file* [path]
     (load-graph (read-edn (slurp (str path))))))

(defn check-g1
  "G1: civilian launch only. Throws ex-info on any non-civilian trajectory/payload class or
  banned weapon attribute."
  [nodes]
  (doseq [[nid n] nodes]
    (do
      (doseq [b banned-attrs]
        (when (contains? n b)
          (throw (ex-info (str "G1 violation: weapon attribute " b " on " nid) {:gate :g1}))))
      (when (= ":trajectory" (get n ":organism/kind"))
        (let [cls (get n ":traj/class")]
          (when-not (contains? civilian-traj cls)
            (throw (ex-info (str "G1 violation: non-civilian trajectory class " cls " on " nid)
                            {:gate :g1})))))
      (when (= ":payload" (get n ":organism/kind"))
        (let [cls (get n ":payload/class")]
          (when-not (contains? civilian-payload cls)
            (throw (ex-info (str "G1 violation: non-civilian payload class " cls " on " nid)
                            {:gate :g1})))))))
  true)

(defn engine-for-stage [nodes edges stage-id]
  (some (fn [e] (when (and (= ":powers" (get e ":en/kind")) (= stage-id (get e ":en/to")))
                  (get nodes (get e ":en/from")))) edges))

(defn simulate
  "Staged Tsiolkovsky Δv (computed on read; transient — N1)."
  [nodes edges]
  (check-g1 nodes)
  (let [stages (->> (vals nodes)
                    (filter #(= ":stage" (get % ":organism/kind")))
                    (sort-by #(get % ":stage/index" 0)))
        mission (first (filter #(= ":mission" (get % ":organism/kind")) (vals nodes)))
        payload-kg (double (get mission ":mission/payload-to-orbit-kg" 0.0))
        target (get mission ":mission/target-regime")
        masses (mapv (fn [s] [(double (get s ":stage/dry-mass-kg" 0.0))
                              (double (get s ":stage/prop-mass-kg" 0.0)) s]) stages)
        n (count masses)
        ;; above-of[k] = Σ_{i>k}(dry_i+prop_i) + payload, built from the top down
        above-of (loop [k (dec n) acc payload-kg out (vec (repeat n 0.0))]
                   (if (neg? k) out
                       (let [[dry prop _] (nth masses k)]
                         (recur (dec k) (+ acc dry prop) (assoc out k acc)))))
        per-stage (mapv (fn [k]
                          (let [[dry prop s] (nth masses k)
                                eng (engine-for-stage nodes edges (get s ":organism/id"))
                                isp (double (get eng ":engine/isp-s" 0.0))
                                m0 (+ dry prop (nth above-of k))
                                mf (+ dry (nth above-of k))
                                dv (if (and (> mf 0) (> m0 mf)) (* isp g0 (Math/log (/ m0 mf))) 0.0)]
                            {:stage (get s ":organism/id")
                             :label (get s ":organism/label" (get s ":organism/id"))
                             :isp_s isp :dv_ms dv :m0_kg m0 :mf_kg mf}))
                        (range n))
        total-dv (reduce + 0.0 (map :dv_ms per-stage))
        required (get regime-dv target 9400.0)]
    {:per_stage per-stage :total_dv_ms total-dv :required_dv_ms required
     :dv_margin_ms (- total-dv required) :payload_kg payload-kg :target_regime target}))

(defn- fmt0 [v] (format "%.0f" (double v)))
(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn report-md [_nodes _edges res]
  (let [tgt (lstrip-colon (or (:target_regime res) "—"))
        margin (:dv_margin_ms res)
        verdict (if (> margin 0) "✅ orbit achievable" "❌ insufficient Δv")
        L (transient [])]
    (conj! L "# torifune 鳥船 — Ama 天-class ascent / Δv report (civilian launch)\n")
    (conj! L (str "> **G1 — CIVILIAN LAUNCH ONLY, NEVER a weapon-delivery / ballistic-strike "
                  "vehicle.** Trajectory class ∈ {ascent, orbit-insertion, rendezvous, deorbit}; "
                  "payload class is civilian; strike trajectories + munition payloads are "
                  "unrepresentable. **G2** propellant is zero-net-carbon. **G8** numbers are "
                  "representative engineering estimates, never measured flight data.\n"))
    (conj! L (str "**Mission**: " (fmt0 (:payload_kg res)) " kg → " tgt " · required Δv ≈ "
                  (fmt0 (:required_dv_ms res)) " m/s\n"))
    (conj! L "\n## Staged Δv budget (Tsiolkovsky, computed on read)\n")
    (conj! L "| stage | Isp (s) | m₀ (kg) | m_f (kg) | Δv (m/s) |")
    (conj! L "|---|---:|---:|---:|---:|")
    (doseq [s (:per_stage res)]
      (conj! L (str "| " (:label s) " | " (fmt0 (:isp_s s)) " | " (fmt0 (:m0_kg s)) " | "
                    (fmt0 (:mf_kg s)) " | " (fmt0 (:dv_ms s)) " |")))
    (conj! L (str "| **total** | | | | **" (fmt0 (:total_dv_ms res)) "** |"))
    (conj! L (str "\n**Δv margin to " tgt ": " (format "%+.0f" (double margin)) " m/s** — " verdict
                  " (reusable reserve folds into this margin).\n"))
    (conj! L (str "\n---\n_torifune 鳥船 · ADR-2606162355 · civilian-launch-only · "
                  "weapon-unrepresentable · zero-net-carbon · representative estimates. Actual "
                  "launch operation is Council + operator-DID gated (no-server-key)._\n"))
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
           {:keys [nodes edges]} (load-file* seed)
           res (simulate nodes edges)]
       (.mkdirs outdir)
       (spit (clojure.java.io/file outdir "ascent-report.md") (report-md nodes edges res))
       (println (str "torifune: " (count nodes) " nodes, " (count edges) " 縁 → "
                     (clojure.java.io/file outdir "ascent-report.md")))
       (println (str "  total Δv " (fmt0 (:total_dv_ms res)) " m/s, margin "
                     (format "%+.0f" (double (:dv_margin_ms res))) " m/s to "
                     (lstrip-colon (or (:target_regime res) "—"))))
       0)))
