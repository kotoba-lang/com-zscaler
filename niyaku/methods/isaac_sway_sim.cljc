(ns niyaku.methods.isaac-sway-sim
  "isaac_sway_sim — STS crane anti-sway transfer driven through the clean-room
  `isaacsim.core.api` surface (kotodama.nv_compat).

  1:1 Clojure port of `20-actors/niyaku/methods/isaac_sway_sim.py`.

  The ship-to-shore crane is, dynamically, a cart + hanging load — the Cartpole
  topology (prismatic trolley + revolute load). The Cartpole's stable equilibrium
  theta = π is the load hanging straight down; theta deviating from π is sway.

  The Python module imports the clean-room `kotodama.nv_compat.isaacsim` package
  (a Python physics surface) and drives a Cartpole / DoublePendulum articulation.
  There is NO Clojure-importable kotodama package, so on this host
  `isaac-available?` is always false (mirroring the Python graceful-skip path: the
  Python tests `@isaac`-skip when kotodama is absent). The pure control-law parts
  (HANG, StsAntiSway, BoomLuffController, report->datoms, resolve-py-src) port 1:1;
  the Isaac-driven sims throw the equivalent of Python's ImportError, and the
  isaac-gated tests skip exactly as the Python suite does.

  Portable .cljc. Host file/path probing behind #?(:clj …)."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])))

(def HANG Math/PI) ;; cartpole theta of a load hanging straight down (stable)

;; *file* is only reliably bound during THIS file's own top-level compilation; capturing
;; it lazily inside a function body (below, resolve-py-src) breaks under bb test:actors's
;; auto-discovery execution model ((apply require nss) then a separate run-tests pass) —
;; the same bug class fixed for himotoki/keizu this session. Captured once, here, at load time.
(def ^:private this-file *file*)

;; ── HALF_EVEN round to n places (Python round() parity) ──────────────────────

(defn- round-half-even
  "Python round(x, n): banker's rounding (HALF_EVEN) to n decimal places."
  [x n]
  #?(:clj (-> (java.math.BigDecimal/valueOf (double x))
              (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
              (.doubleValue))
     :default (let [p (Math/pow 10 n)
                    scaled (* (double x) p)
                    fl (Math/floor scaled)
                    diff (- scaled fl)
                    r (cond
                        (< diff 0.5) fl
                        (> diff 0.5) (inc fl)
                        :else (if (even? (long fl)) fl (inc fl)))]
                (/ r p))))

;; ── kotodama py-src resolution (host file-probe; faithful to the Python walk) ─

(def ^:private kotodama-rel
  ["40-engine" "kotoba" "crates" "kotoba-kotodama" "py" "src"])

(defn resolve-py-src
  "Locate the kotodama package source.

  Order: NIYAKU_KOTODAMA_SRC env override (if it has kotodama/nv_compat) → walk
  parent dirs for a populated 40-engine/kotoba/.../py/src → monorepo-relative
  default. Returns the first hit, else the default (which may not exist)."
  []
  #?(:clj
     (let [join (fn [& parts] (.getPath ^java.io.File (apply io/file parts)))
           dir? (fn [p] (.isDirectory (io/file p)))
           env (System/getenv "NIYAKU_KOTODAMA_SRC")]
       (if (and env (dir? (apply join env "kotodama" "nv_compat" [])))
         (.getCanonicalPath (io/file env))
         (let [here (-> this-file io/file .getCanonicalFile .getParent)]
           (loop [here here, n 0]
             (let [cand (apply io/file here kotodama-rel)]
               (cond
                 (dir? (io/file cand "kotodama" "nv_compat"))
                 (.getCanonicalPath cand)
                 (>= n 8)
                 ;; default (monorepo root is 3 levels up from methods/)
                 (.getCanonicalPath
                   (apply io/file (-> this-file io/file .getParent) ".." ".." ".." kotodama-rel))
                 :else
                 (recur (.getParent (io/file here)) (inc n))))))))
     :default (str/join "/" kotodama-rel)))

(defn isaac-available?
  "True iff the clean-room `isaacsim.core.api` surface can be imported.

  No Clojure-importable kotodama package exists on this host, so this is always
  false (the Python `@isaac`-skip equivalent)."
  []
  false)

(defn load-isaac
  "Import and return the Isaac API symbols. Throws on this host (no Clojure
  kotodama) — the equivalent of Python's ImportError."
  []
  (throw (ex-info "kotodama.nv_compat.isaacsim not importable on this host (no Clojure kotodama)"
                  {:error :import})))

;; ── anti-sway feedback in Cartpole coordinates ───────────────────────────────

(defn make-sts-anti-sway
  "Trolley-force feedback law on Cartpole state [x, x_dot, theta, theta_dot].

  phi = theta - π is the sway from the hanging equilibrium. The law positions the
  trolley (kp/kd) while damping sway (k_phi/k_phid). A +force pushes the cart +x
  AND drives phi positive, so all four feedback terms are negative."
  [& {:keys [kp kd k-phi k-phid max-force]
      :or   {kp 6.0 kd 10.0 k-phi 25.0 k-phid 12.0 max-force 100.0}}]
  {:kp kp :kd kd :k-phi k-phi :k-phid k-phid :max-force max-force})

(defn force
  ^double [ctrl state ^double x-target]
  (let [[x x-dot theta theta-dot] state
        phi (- (double theta) HANG)
        u (+ (* (- (double (:kp ctrl))) (- (double x) x-target))
             (* (- (double (:kd ctrl))) (double x-dot))
             (* (- (double (:k-phi ctrl))) phi)
             (* (- (double (:k-phid ctrl))) (double theta-dot)))
        mf (double (:max-force ctrl))]
    (max (- mf) (min mf u))))

(defn- make-transfer-report
  [reached final-x residual-sway-rad peak-sway-rad steps anti-sway]
  {:reached reached
   :final-x final-x
   :residual-sway-rad residual-sway-rad
   :peak-sway-rad peak-sway-rad
   :steps steps
   :anti-sway anti-sway})

(defn run-sts-transfer
  "Drive the trolley to `x-target` through the Isaac API.

  Throws (via load-isaac) if the Isaac surface is unavailable — always the case
  on this host."
  [& {:keys [x-target anti-sway push-force steps physics-dt pos-tol controller]
      :or   {anti-sway true push-force 12.0 steps 1200 physics-dt (/ 1.0 120.0)
             pos-tol 0.05}}]
  (load-isaac)
  ;; unreachable on this host (load-isaac throws); shape preserved for fidelity.
  (let [_ [x-target anti-sway push-force steps physics-dt pos-tol controller]]
    (make-transfer-report false 0.0 0.0 0.0 0 anti-sway)))

;; ── boom / luffing crane (double-pendulum Isaac topology) ────────────────────

(defn make-boom-luff-controller
  "Luffing-jib crane anti-sway on the Isaac DoublePendulum.

  link1 (shoulder) is the powered jib that luffs to a target angle; link2 (elbow)
  is the passive hoist cable + load that must not swing. Only the jib joint is
  actuated; the elbow effort stays 0."
  [& {:keys [kp kd k-load k-loadd max-torque]
      :or   {kp 20.0 kd 12.0 k-load 10.0 k-loadd 6.0 max-torque 60.0}}]
  {:kp kp :kd kd :k-load k-load :k-loadd k-loadd :max-torque max-torque})

(defn torque
  [ctrl q qd ^double q1-target]
  (let [[q1 q2] q
        [q1d q2d] qd
        tau1 (+ (* (- (double (:kp ctrl))) (- (double q1) q1-target))
                (* (- (double (:kd ctrl))) (double q1d))
                (* (- (double (:k-load ctrl))) (double q2))
                (* (- (double (:k-loadd ctrl))) (double q2d)))
        mt (double (:max-torque ctrl))
        tau1 (max (- mt) (min mt tau1))]
    [tau1 0.0])) ;; elbow (cable) is passive

(defn- make-luff-report
  [reached final-boom-rad residual-load-rad peak-load-rad steps]
  {:reached reached
   :final-boom-rad final-boom-rad
   :residual-load-rad residual-load-rad
   :peak-load-rad peak-load-rad
   :steps steps})

(defn run-boom-luff
  "Luff the jib to `q1-target` (rad) through the Isaac DoublePendulum API.

  Throws if the Isaac surface is unavailable — always the case on this host."
  [q1-target & {:keys [steps physics-dt ang-tol controller]
                :or   {steps 3000 physics-dt (/ 1.0 120.0) ang-tol 0.02}}]
  (load-isaac)
  (let [_ [q1-target steps physics-dt ang-tol controller]]
    (make-luff-report false 0.0 0.0 0.0 0)))

(defn report->datoms
  "Serialise a transfer report to kotoba EAVT datom tuples [e a v].

  Entity id `niyaku/sim/<sim-id>`; attributes under :niyaku.sim/*."
  [report sim-id]
  (let [e (str "niyaku/sim/" sim-id)]
    [[e ":niyaku.sim/anti-sway" (:anti-sway report)]
     [e ":niyaku.sim/reached" (:reached report)]
     [e ":niyaku.sim/final-x" (round-half-even (:final-x report) 4)]
     [e ":niyaku.sim/residual-sway-rad" (round-half-even (:residual-sway-rad report) 5)]
     [e ":niyaku.sim/peak-sway-rad" (round-half-even (:peak-sway-rad report) 5)]
     [e ":niyaku.sim/steps" (:steps report)]]))
