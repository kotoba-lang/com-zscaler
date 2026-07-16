#!/usr/bin/env bb
;; tsuchifumi 土踏み — system-dynamics model (stocks/flows; clj-native, pure stdlib).
(ns tsuchifumi.methods.sysdyn
  "tsuchifumi 土踏み — system-dynamics model of earthing under-institutionalization
  × ambient-EMF (ADR-2606212000).

  A Forrester/Meadows STOCK-AND-FLOW model integrated with explicit Euler steps.
  It is a DISCLOSED-PARAMETER WHAT-IF, **NOT a prediction** (G6, 非終末論): every
  output is a DISTRIBUTION (p10/p50/p90) over a parameter-uncertainty ENSEMBLE; a
  single point forecast is structurally unrepresentable (`point-forecast` raises,
  exactly like shionome's `build-live`). Forecasting belongs to mitooshi.

  Stocks (state vector):
    E  ambient-EMF level            (0..E-cap; logistic growth with wireless density)
    A  earthing-ACCESS              (0..1; tracks infrastructure with lag, eroded by urbanization)
    I  grounding-INFRASTRUCTURE     (0..1; the POLICY LEVER — greenspace + grounded-design standards)
    B  bioelectric BURDEN (hyp.)    (0..5; the HYPOTHESIZED accumulated burden — flagged contested,
                                     a model variable, never an asserted clinical quantity)

  Flows (per dt):
    E' = g_emf · E · (1 − E/E_cap)                  ; logistic ambient-EMF growth
    I' = policy_invest − depreciation · I           ; the lever scenarios move
    A' = alpha · (I − A) − urb · A                   ; access tracks infra, urbanization erodes it
    B' = k_exp · E · (1 − A)                         ; exposure inflow (low access ⇒ more accrues)
         − k_ground · A · B                          ; earthing discharge (the contested hypothesis)
         − nat_recover · B                           ; natural recovery

  Scenarios move ONLY policy_invest + initial I (the institutional lever):
    :neglect  — no grounding/greenspace policy
    :baseline — status-quo drift
    :relief   — institutionalize earthing/greenspace access (what the actor proposes)

  Deterministic: ensemble jitter is sha256-seeded (no Math/random) → resume-safe."
  (:require [clojure.string :as str]))

;; ── deterministic seeded unit noise (no Math/random; hakoniwa pattern) ───────
(defn- sha256-hex [^String s]
  (let [b (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and (int %) 0xff)) b))))

(defn- hash-unit
  "Deterministic double in [0,1) from a seed string (top 52 bits of sha256)."
  [s]
  (let [h (sha256-hex s)]
    (/ (double (Long/parseLong (subs h 0 13) 16)) (Math/pow 2 52))))

(defn- jitter
  "Deterministic multiplicative jitter in [1-frac, 1+frac) for key `k`, replica `idx`."
  [seed idx k frac]
  (let [u (hash-unit (str seed "|" idx "|" (name k)))]
    (+ (- 1.0 frac) (* u 2.0 frac))))

;; ── parameters + scenarios ───────────────────────────────────────────────────
(def default-params
  {:g_emf 0.06 :E_cap 1.5 :alpha 0.20 :urb 0.05
   :depreciation 0.04 :k_exp 0.08 :k_ground 0.18 :nat_recover 0.03
   :policy_invest 0.03})

(def default-init {:E 0.8 :A 0.35 :I 0.35 :B 0.3})

(def scenarios
  {:neglect  {:label "neglect — no grounding/greenspace policy"
              :params {:policy_invest 0.0}  :init {:I 0.2}}
   :baseline {:label "baseline — status-quo drift"
              :params {:policy_invest 0.03} :init {:I 0.35}}
   :relief   {:label "relief — institutionalize earthing/greenspace access"
              :params {:policy_invest 0.10} :init {:I 0.5}}})

(def ^:private jittered-keys [:g_emf :alpha :urb :depreciation :k_exp :k_ground :nat_recover])

(defn- clamp [x lo hi] (max (double lo) (min (double hi) (double x))))

;; ── one Euler step + one trajectory ──────────────────────────────────────────
(defn step
  "One Euler step (dt) of the stock-and-flow system. Returns the next state map."
  [{:keys [E A I B]} p dt]
  (let [{:keys [g_emf E_cap alpha urb depreciation k_exp k_ground nat_recover policy_invest]} p
        dE (* g_emf E (- 1.0 (/ E E_cap)))
        dI (- policy_invest (* depreciation I))
        dA (- (* alpha (- I A)) (* urb A))
        dB (- (* k_exp E (- 1.0 A))
              (* k_ground A B)
              (* nat_recover B))]
    {:E (clamp (+ E (* dt dE)) 0 E_cap)
     :I (clamp (+ I (* dt dI)) 0 1)
     :A (clamp (+ A (* dt dA)) 0 1)
     :B (clamp (+ B (* dt dB)) 0 5)}))

(defn simulate-one
  "Integrate one trajectory for `steps` Euler steps. Returns a vector of B values
  (the burden stock) of length steps+1, including the initial state."
  [init params steps dt]
  (loop [s init n steps out [(:B init)]]
    (if (zero? n)
      out
      (let [s' (step s params dt)]
        (recur s' (dec n) (conj out (:B s')))))))

;; ── percentiles (nearest-rank) ───────────────────────────────────────────────
(defn percentile [sorted-vec p]
  (let [n (count sorted-vec)]
    (if (zero? n) 0.0
        (nth sorted-vec (min (dec n) (max 0 (Math/round (* (/ p 100.0) (dec n)))))))))

(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

;; ── ensemble → DISTRIBUTION over the burden trajectory (G6) ───────────────────
(defn ensemble
  "Run a K-replica parameter-uncertainty ensemble and return, PER STEP, the
  {p10 p50 p90} distribution of the burden stock B — never a single point (G6).
  opts: :params :init :steps :dt :replicas :frac :seed."
  [{:keys [params init steps dt replicas frac seed]
    :or {params default-params init default-init steps 30 dt 1.0
         replicas 21 frac 0.15 seed "tsuchifumi-sysdyn"}}]
  (let [trajs (vec (for [idx (range replicas)]
                     (let [pj (reduce (fn [m k] (update m k * (jitter seed idx k frac)))
                                      params jittered-keys)]
                       (simulate-one init pj steps dt))))
        bands (vec (for [t (range (inc steps))]
                     (let [col (sort (map #(nth % t) trajs))]
                       {"t" t
                        "p10" (round3 (percentile col 10))
                        "p50" (round3 (percentile col 50))
                        "p90" (round3 (percentile col 90))})))]
    {"steps" steps "dt" dt "replicas" replicas "bands" bands}))

(defn run-scenarios
  "Run the ensemble for each scenario (:neglect :baseline :relief) and return a
  comparison map. opts forwarded to `ensemble` (minus :params/:init, set per scenario)."
  ([] (run-scenarios {}))
  ([opts]
   (into {}
         (for [[k {:keys [label params init]}] scenarios]
           [k {"label" label
               "result" (ensemble (merge {:steps 30 :dt 1.0 :seed (str "tsuchifumi-" (name k))}
                                          opts
                                          {:params (merge default-params params)
                                           :init   (merge default-init init)}))}]))))

(defn summary
  "A compact comparison: final-step p50 burden per scenario + the relief dividend
  (neglect − relief) at the horizon. Distribution-only; the dividend is a p50 delta."
  [scen-map]
  (let [final-p50 (fn [k] (-> (get-in scen-map [k "result" "bands"]) last (get "p50")))
        n (final-p50 :neglect) b (final-p50 :baseline) r (final-p50 :relief)]
    {"final_burden_p50" {"neglect" n "baseline" b "relief" r}
     "relief_dividend_p50" (round3 (- n r))
     "note" "Distribution-only what-if under DISCLOSED parameters (G6); NOT a forecast."}))

;; ── G6 guard — a single point forecast is unrepresentable ────────────────────
(defn point-forecast
  "G6 — a deterministic single-number prediction is structurally refused. The model
  emits distributions (p10/p50/p90) only; forecasting belongs to mitooshi (見通し)."
  [& _]
  (throw (ex-info (str "tsuchifumi G6: the system-dynamics model is distribution-only "
                       "(p10/p50/p90 over a parameter ensemble); a single point forecast "
                       "is unrepresentable (非終末論). Use `ensemble` / `run-scenarios`.")
                  {:gate :G6})))

;; ── CLI (bb) ─────────────────────────────────────────────────────────────────
#?(:clj
   (defn -main [& _]
     (let [scen (run-scenarios)
           s (summary scen)]
       (println "# tsuchifumi 土踏み — system-dynamics scenarios (30y, 21-replica ensemble)\n")
       (println "Distribution-only (p10/p50/p90); NOT a forecast (G6).\n")
       (doseq [[k _] scenarios]
         (let [bands (get-in scen [k "result" "bands"])
               f (last bands)]
           (println (format "%-9s final burden(hyp) p50=%.3f  [p10=%.3f p90=%.3f]"
                            (name k) (double (get f "p50")) (double (get f "p10")) (double (get f "p90"))))))
       (println (str "\nrelief dividend (neglect−relief, p50) = " (get s "relief_dividend_p50")))
       (println "\n(The burden is a HYPOTHESIZED model variable, not an asserted clinical quantity — G6/G2.)"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
