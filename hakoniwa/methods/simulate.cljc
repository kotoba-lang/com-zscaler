(ns hakoniwa.methods.simulate
  "hakoniwa 箱庭 — forward simulation kernel (Friedkin-Johnsen opinion dynamics over the box).
  1:1 Clojure port of `methods/simulate.py` (ADR-2606111500).

  Runs a CONTAINED miniature world of FICTIONAL latent personas forward in discrete steps and
  produces an ENSEMBLE of trajectories. The spread of the ensemble's population statistic IS the
  forecast distribution (distribution.cljc) — hakoniwa never asserts a single foretold future
  (G2 / 非終末論).

  Kernel (per persona i, deterministic):
      x_i(t+1) = λ_i · Σ_j w_ij · x_j(t) + (1 - λ_i) · a_i
  Ensemble: K replicas; replica r perturbs each persona's anchor by a DETERMINISTIC seeded
  jitter — sha256 of f\"{seed}:{r}:{persona_id}\" → first 4 bytes big-endian / 2^32 → [0,1) →
  centred to [-amp, amp]. NO Math/random, so the run is byte-reproducible and pywasm-portable.

  CRITICAL byte-parity invariants (the whole ballgame for a stochastic sim):
    - the sha256-seeded jitter byte→float derivation reproduced EXACTLY (verified vs python3);
    - the FJ inner-relaxation iteration order + convergence test reproduced EXACTLY;
    - persona id (pids) order = EDN insertion order (deterministic);
    - all float arithmetic is IEEE-754 double (matches CPython float), so plain Clojure double
      arithmetic is byte-identical to Python's.

  House style: Python ':…' keyword strings stay strings; pure fns; inference/I/O at #?(:clj)
  edges (this module is pure). Portable .cljc."
  (:require [hakoniwa.methods.world :as world]
            #?(:clj [clojure.java.io :as io])))

(def default-steps 12)
(def default-replicas 64)
(def default-seed 7)
(def default-jitter 0.10) ;; anchor perturbation amplitude across replicas (the ensemble spread)
(def max-iter 200)
(def tol 1.0e-6)

(defn clamp01 ^double [^double x]
  (if (< x 0.0) 0.0 (if (> x 1.0) 1.0 x)))

;; ── sha256-seeded jitter (the byte→float derivation that makes the sim reproducible) ──
(defn- sha256-bytes
  "SHA-256 digest of a UTF-8 string → byte-array (mirrors hashlib.sha256(s.encode).digest())."
  [^String s]
  #?(:clj (.digest (java.security.MessageDigest/getInstance "SHA-256")
                   (.getBytes s "UTF-8"))
     :cljs (throw (ex-info "sha256 jitter requires the :clj edge" {}))))

(defn jitter
  "Deterministic per-(replica, persona) anchor jitter in [-amp, amp]. No Math/random.
  Exactly reproduces simulate.py `_jitter`:
    h = sha256(f\"{seed}:{replica}:{pid}\").digest()
    u = int.from_bytes(h[:4], \"big\") / float(1 << 32)   ;; first 4 bytes, big-endian, unsigned
    return (u * 2.0 - 1.0) * amp"
  ^double [seed replica pid ^double amp]
  (let [h (sha256-bytes (str seed ":" replica ":" pid))
        ;; int.from_bytes(h[:4], "big") as an unsigned 32-bit integer
        u32 (long (reduce (fn [acc i] (+ (* acc 256) (bit-and (long (aget ^bytes h (int i))) 0xff)))
                          0 (range 4)))
        u (/ (double u32) 4294967296.0)]
    (* (- (* u 2.0) 1.0) amp)))

;; ── topology ────────────────────────────────────────────────────────────────
(defn- num->double ^double [v] (double v))

(defn build-topology
  "Return {:pids :sus :base-anchor :weight :incoming :exposure}.
    incoming[i] = list of [j w_ij] row-normalised so Σ_j w_ij == 1 (empty → fully anchored).
    exposure[i] = list of [push at-step] the persona is exposed to (additive from at-step on)."
  [nodes edges]
  (let [P (world/personas nodes)
        pids (vec (world/ordered-keys P)) ;; EDN insertion order → deterministic
        sus (reduce (fn [m i] (assoc m i (num->double (get (get P i) ":persona/susceptibility" 0.5)))) {} pids)
        base-anchor (reduce (fn [m i] (assoc m i (clamp01 (num->double (get (get P i) ":persona/initial-stance" 0.5))))) {} pids)
        weight (reduce (fn [m i] (assoc m i (num->double (get (get P i) ":persona/weight" 1.0)))) {} pids)
        sus-set (set pids)
        ;; raw incoming (in edge order, mirroring Python list append order)
        raw-in (reduce (fn [m e]
                         (if (= (get e ":en/kind") ":influences")
                           (let [j (get e ":en/from") i (get e ":en/to")]
                             (if (and (contains? sus-set j) (contains? sus-set i))
                               (update m i (fnil conj []) [j (num->double (get e ":en/weight" 1.0))])
                               m))
                           m))
                       (reduce (fn [m i] (assoc m i [])) {} pids)
                       edges)
        incoming (reduce (fn [m i]
                           (let [lst (get raw-in i)
                                 tot (reduce + 0.0 (map second lst))]
                             (assoc m i (if (> tot 0)
                                          (mapv (fn [[j w]] [j (/ w tot)]) lst)
                                          []))))
                         {} pids)
        sig (world/signals nodes)
        sig-set (set (world/ordered-keys sig))
        exposure (reduce (fn [m e]
                           (if (= (get e ":en/kind") ":exposed-to")
                             (let [i (get e ":en/from") s (get e ":en/to")]
                               (if (and (contains? (set pids) i) (contains? sig-set s))
                                 (update m i (fnil conj [])
                                         [(num->double (get (get sig s) ":signal/push" 0.0))
                                          (long (get (get sig s) ":signal/at-step" 0))])
                                 m))
                             m))
                         (reduce (fn [m i] (assoc m i [])) {} pids)
                         edges)]
    {:pids pids :sus sus :base-anchor base-anchor :weight weight
     :incoming incoming :exposure exposure}))

(defn- anchor-at-step
  ^double [^double base exposures ^long step ^double jit]
  (let [a (reduce (fn [^double a [^double push at]]
                    (if (>= step (long at)) (+ a push) a))
                  (+ base jit)
                  exposures)]
    (clamp01 a)))

(defn run-replica
  "One deterministic forward run; returns final stance map x[i] (mirrors run_replica)."
  [pids sus base-anchor incoming exposure steps seed replica jitter-amp]
  (let [jit (reduce (fn [m i] (assoc m i (jitter seed replica i jitter-amp))) {} pids)
        x0 (reduce (fn [m i] (assoc m i (anchor-at-step (get base-anchor i) (get exposure i) 0 (get jit i)))) {} pids)]
    (loop [step 1, x x0]
      (if (> step (long steps))
        x
        (let [anchor (reduce (fn [m i] (assoc m i (anchor-at-step (get base-anchor i) (get exposure i) step (get jit i)))) {} pids)
              ;; iterate the FJ map to its fixed point within this step (inner relaxation)
              x* (loop [_iter 0, x x]
                   (if (>= _iter (long max-iter))
                     x
                     (let [nx (reduce (fn [m i]
                                        (let [in (get incoming i)
                                              nbr (reduce (fn [^double s [j ^double w]] (+ s (* w (double (get x j))))) 0.0 in)
                                              lam (if (seq in) (double (get sus i)) 0.0)]
                                          (assoc m i (clamp01 (+ (* lam nbr) (* (- 1.0 lam) (double (get anchor i))))))))
                                      {} pids)
                           delta (reduce (fn [^double mx i]
                                           (let [d (Math/abs (- (double (get nx i)) (double (get x i))))]
                                             (if (> d mx) d mx)))
                                         (Double/NEGATIVE_INFINITY) pids)]
                       (if (< delta (double tol))
                         nx
                         (recur (inc _iter) nx)))))]
          (recur (inc step) x*))))))

(defn population-statistic
  "Aggregate-first readout: population weighted-mean final stance (G1 — never per-persona).
  `member-ids` nil → all keys of x (in x's key order). Mirrors population_statistic."
  ([x weight] (population-statistic x weight nil))
  ([x weight member-ids]
   (let [ids (or member-ids (keys x))
         wsum (reduce (fn [^double s i] (+ s (double (get weight i)))) 0.0 ids)]
     (if (<= wsum 0)
       0.0
       (/ (reduce (fn [^double s i] (+ s (* (double (get weight i)) (double (get x i))))) 0.0 ids)
          wsum)))))

(defn ensemble
  "Return [outcomes-per-replica meta]. outcomes is a vector of the town-wide statistic.
  1:1 with simulate.ensemble (incl. the :all member-id wiring)."
  ([nodes edges] (ensemble nodes edges {}))
  ([nodes edges {:keys [steps replicas seed jitter]
                 :or {steps default-steps replicas default-replicas
                      seed default-seed jitter default-jitter}}]
   (let [{:keys [pids sus base-anchor weight incoming exposure]} (build-topology nodes edges)
         outs (world/outcomes nodes)
         member-ids (when (seq outs)
                      (let [first-out (get outs (first (world/ordered-keys outs)))]
                        (when (not= (get first-out ":outcome/measures") ":all")
                          pids))) ;; only :all is wired in R0; named-population is a future facet
         results (mapv (fn [r]
                         (let [x (run-replica pids sus base-anchor incoming exposure steps seed r jitter)]
                           (population-statistic x weight member-ids)))
                       (range replicas))
         ;; meta keys are STRING keys mirroring the Python dict (preserves insertion order)
         meta (array-map "personas" (count pids) "edges" (count edges) "steps" steps
                         "replicas" replicas "seed" seed "jitter" jitter)]
     [results meta])))

;; ── swarm ensemble (G5/G8 gated LLM-persona variant; ADR-2606111500 R1) ───────────────────────
;; Same synthetic-agents → ensemble → distribution interface as `ensemble`, but each agent's
;; per-step update is delegated to a pluggable `step-fn`:
;;     (step-fn stance neighbour-mean susceptibility anchor) → {"stance" v "via" kw}
;; (the murakumo.persona-step shape). The step-fn's stance is CLAMPED to [0,1] — a rogue or
;; over-range step CANNOT escape the unit interval. With NO step-fn the deterministic scalar
;; Friedkin-Johnsen KERNEL is used (delegates to `ensemble`; swarm_via [":kernel"]) — the default
;; and test path; the LLM swarm is the gated variant that falls back to this kernel when offline.
(defn- swarm-run-replica
  "One forward run where each agent step is the supplied step-fn (clamped to [0,1]). Mirrors
  run-replica's deterministic jitter + anchor-at-step; collects each step's :via into `vias`."
  [pids sus base-anchor incoming exposure steps seed replica jitter-amp step-fn vias]
  (let [jit (reduce (fn [m i] (assoc m i (jitter seed replica i jitter-amp))) {} pids)
        x0 (reduce (fn [m i] (assoc m i (anchor-at-step (get base-anchor i) (get exposure i) 0 (get jit i)))) {} pids)]
    (loop [step 1, x x0]
      (if (> step (long steps))
        x
        (let [anchor (reduce (fn [m i] (assoc m i (anchor-at-step (get base-anchor i) (get exposure i) step (get jit i)))) {} pids)
              nx (reduce (fn [m i]
                           (let [in (get incoming i)
                                 nbr (reduce (fn [^double s [j ^double w]] (+ s (* w (double (get x j))))) 0.0 in)
                                 out (step-fn (double (get x i)) nbr (double (get sus i)) (double (get anchor i)))]
                             (swap! vias conj (get out "via"))
                             (assoc m i (clamp01 (double (get out "stance"))))))
                         {} pids)]
          (recur (inc step) nx))))))

(defn swarm-ensemble
  "Return [outcomes-per-replica meta] like `ensemble`, driving each agent with `step-fn`
  (clamped). With no :step-fn it IS `ensemble` (scalar kernel), tagged meta swarm_via [\":kernel\"].
  meta[\"swarm_via\"] is the sorted distinct set of :via values the run used."
  ([nodes edges] (swarm-ensemble nodes edges {}))
  ([nodes edges {:keys [steps replicas seed jitter step-fn]
                 :or {steps default-steps replicas default-replicas
                      seed default-seed jitter default-jitter}}]
   (if (nil? step-fn)
     (let [[results meta] (ensemble nodes edges {:steps steps :replicas replicas :seed seed :jitter jitter})]
       [results (assoc meta "swarm_via" [":kernel"])])
     (let [{:keys [pids sus base-anchor weight incoming exposure]} (build-topology nodes edges)
           outs (world/outcomes nodes)
           member-ids (when (seq outs)
                        (let [first-out (get outs (first (world/ordered-keys outs)))]
                          (when (not= (get first-out ":outcome/measures") ":all")
                            pids)))
           vias (atom #{})
           results (mapv (fn [r]
                           (let [x (swarm-run-replica pids sus base-anchor incoming exposure
                                                      steps seed r jitter step-fn vias)]
                             (population-statistic x weight member-ids)))
                         (range replicas))
           meta (array-map "personas" (count pids) "edges" (count edges) "steps" steps
                           "replicas" replicas "seed" seed "jitter" jitter
                           "swarm_via" (vec (sort @vias)))]
       [results meta]))))

#?(:clj
   (defn -main
     "CLI entry (file I/O at the edge): ensemble summary over the seed scenario."
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           scenario (if (and (seq argv) (not (clojure.string/starts-with? (first argv) "--")))
                      (io/file (first argv))
                      (io/file here "data" "seed-scenario.kotoba.edn"))
           opt (fn [flag dflt cast]
                 (if (some #{flag} argv) (cast (nth argv (inc (.indexOf argv flag)))) dflt))
           steps (opt "--steps" default-steps #(Long/parseLong %))
           replicas (opt "--replicas" default-replicas #(Long/parseLong %))
           seed (opt "--seed" default-seed #(Long/parseLong %))
           {:keys [nodes edges]} (world/load-file* scenario)
           [results meta] (ensemble nodes edges {:steps steps :replicas replicas :seed seed})]
       (println (str "hakoniwa: " (get meta "personas") " synthetic personas, " (count edges)
                     " 縁, " steps " steps × " replicas " replicas → ensemble mean "
                     (format "%.4f" (/ (reduce + 0.0 results) (count results)))))
       (println "  (distribution-only output via distribution.cljc — never a point assertion, G2)")
       0)))
