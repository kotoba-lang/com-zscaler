(ns noroshi.methods.fibre-loop
  "fibre-loop — noroshi (烽) fibre-optic infrastructure operational loop (R0 :representative).
  1:1 Clojure port of methods/fibre_loop.py. Stdlib only (Math/ + BigDecimal).

  The runnable, tested core behind the `fibre_loop` cell: the three field operations of laying
  fibre-optic cable, end to end — **lay → align → splice** — composed under noroshi's
  constitutional gates. 烽 (the beacon-fire watchtower) is the original optical telecom; this is
  its modern body: a cable plow / ROV laying duct along a planned route, the fibre actively
  aligned to a coupler, and two fibre ends fusion-spliced into a continuous link.

  Each phase is a real, deterministic sub-model, reusing the shared infra-robotics substrate
  (noroshi.methods.substrate ≅ _substrate.py) and noroshi's EXISTING safety-critical aligner
  (noroshi.methods.active-alignment ≅ active_alignment.py) — nothing is reimplemented:

    LAY    — a CableLayPlant (a Plant): cross-track-error dynamics of a cable-lay plow/ROV
             tracking a planned route against a small constant seabed/soil drift. A PI tracking
             controller from the substrate (PID + simulate) drives the cross-track error to ~0.
    ALIGN  — the EXISTING noroshi align(coupler, laser) Hooke-Jeeves search + IEC 60825 laser
             interlock from active-alignment (REUSED, NOT duplicated) → coupling loss (dB).
    SPLICE — a splice-loss-db(offset, cleave-angle) fusion-splice loss model (loss grows with
             lateral offset² and cleave angle²) + a fusion-splice acceptance threshold.

  The whole loop runs offline with no hardware, no network and no live laser. noroshi gates
  apply: N1/G3 civilian-only, G5 IEC 60825 laser-safety (inherited from the reused aligner), G7
  no-server-key (member/operator-signed, server-held-key=false), G8 outward-gated (dry-run=true;
  live actuation is Council Lv6+). A live cable-laying fleet displaces fibre-laying crews ⇒
  G2-coupled to the Displacement Dividend.

  House style: kebab keyword keys; Python ':…' strings stay literal strings; pure fns; mutable
  plant state in an atom (the hikari substrate pattern) for bit-faithful accumulation; I/O at
  #?(:clj) edges; closed-vocab/gate violations → ex-info. ALL float arithmetic EXACT:
  round(x,n)/{:.Nf} reproduce Python HALF_EVEN on the exact double; the PI control loop +
  Hooke-Jeeves iteration order + accept/reject + step-halving are reproduced EXACTLY. Portable
  .cljc, gates 1:1 + test-enforced."
  (:require [clojure.string :as str]
            [noroshi.methods.substrate :as sub]
            [noroshi.methods.active-alignment :as aa]))

;; ── float helper: Python round(x, n) — HALF_EVEN on the exact double ─────────
(def py-round sub/py-round)

;; ── module-level constants (mirror fibre_loop.py) ────────────────────────────
;; noroshi fibre civilian-use allowlist (closed-world, N1). Laying / repairing fibre is never a
;; force use.
(def PERMITTED-USES ["lay" "align" "splice" "inspect" "repair" "bury"])

;; Fusion-splice acceptance threshold (dB). A good arc-fusion splice is typically ≤0.05–0.1 dB
;; insertion loss (ITU-T G.652/G.657 field practice); we take 0.10 dB as the acceptance ceiling.
(def SPLICE-LOSS-MAX-DB 0.10)

;; Splice-loss model coefficients (:representative — arithmetic, no measured splicer).
;; Lateral core offset dominates (loss ∝ offset²); a non-zero cleave angle adds an
;; angular-mismatch term.
(def ^:private SPLICE-K-OFFSET 0.0016)   ; dB per (µm offset)²
(def ^:private SPLICE-K-ANGLE 0.012)     ; dB per (degree of cleave-angle mismatch)²

;; ── result records (frozen dataclasses → plain maps, kebab keys) ─────────────
(defn ->lay-result
  "Outcome of the cross-track tracking run for the cable-lay plow/ROV."
  [{:keys [use initial-xte-m final-xte-m track-converged settling-seconds max-abs-xte-m]}]
  {:use use
   :initial-xte-m initial-xte-m
   :final-xte-m final-xte-m
   :track-converged track-converged
   :settling-seconds settling-seconds
   :max-abs-xte-m max-abs-xte-m})

(defn ->splice-result
  "Outcome of a single fusion splice against the acceptance threshold."
  [{:keys [lateral-offset-um cleave-angle-deg loss-db threshold-db passed]}]
  {:lateral-offset-um lateral-offset-um
   :cleave-angle-deg cleave-angle-deg
   :loss-db loss-db
   :threshold-db threshold-db
   :passed passed})

(defn ->fibre-segment-result
  "The composed lay → align → splice record for one fibre segment (dry-run, R0)."
  [m]
  (select-keys m [:use :track-converged :final-xte-m :lay-settling-seconds
                  :coupling-loss-db :align-converged :splice-loss-db :splice-passed
                  :witness-ok :overall-ok :server-held-key :dry-run :representative]))

;; ── LAY: cross-track-error tracking plant ────────────────────────────────────
;; CableLayPlant is a mutable dataclass (k, drift, e). We mirror its in-place mutation with an
;; atom holding the cross-track error `e`, so simulate's step sequence + accumulation match the
;; Python object exactly.
(defn ->cable-lay-plant
  "Cross-track-error dynamics of a cable-lay plow / ROV tracking a planned route.

    de/dt = k·command + drift

  Defaults mirror the Python CableLayPlant dataclass field defaults exactly."
  ([] (->cable-lay-plant {}))
  ([{:keys [k drift e] :or {k 1.0 drift 0.05 e 0.0}}]
   {:k k :drift drift :state (atom {:e e})}))

(defn cable-lay-measure
  "plant.measure() → current cross-track error e."
  [plant]
  (:e @(:state plant)))

(defn cable-lay-step!
  "plant.step(command, dt): e += (k·command + drift)·dt. In-place mutation (mirrors Python)."
  [plant command dt]
  (let [{:keys [k drift]} plant
        dedt (+ (* k command) drift)]
    (swap! (:state plant) update :e + (* dedt dt))
    nil))

(defn lay-segment
  "Track the planned route from an initial cross-track error to ~0. Raises (assert-civilian)
  first. `route-xte0` is the plow's initial lateral offset (m); the PI tracking loop drives it
  to the setpoint (0) while rejecting the constant drift. Returns convergence + settling."
  ([route-xte0] (lay-segment route-xte0 {}))
  ([route-xte0 {:keys [use k drift kp ki cmd-limit steps dt tol]
                :or {use "lay" k 1.0 drift 0.05 kp 3.0 ki 1.5 cmd-limit 5.0
                     steps 4000 dt 0.01 tol 1e-3}}]
   (sub/assert-civilian use PERMITTED-USES)   ; N1 gate before any actuation modelling
   (let [plant (->cable-lay-plant {:k k :drift drift :e route-xte0})
         pid (sub/->pid {:kp kp :ki ki :out-min (- cmd-limit) :out-max cmd-limit})
         res (sub/simulate {:plant plant
                            :measure-fn cable-lay-measure
                            :step-fn cable-lay-step!
                            :controller pid
                            :setpoint 0.0
                            :steps steps :dt dt :tol tol})
         settling-step (:settling-step res)
         settling-seconds (if (>= settling-step 0) (* settling-step dt) -1.0)]
     (->lay-result
      {:use use
       :initial-xte-m (py-round route-xte0 6)
       :final-xte-m (py-round (:final-value res) 6)
       :track-converged (:converged res)
       :settling-seconds (py-round settling-seconds 3)
       :max-abs-xte-m (:max-abs-error res)}))))

;; ── SPLICE: fusion-splice loss model ──────────────────────────────────────────
(defn splice-loss-db
  "Fusion-splice insertion loss (dB) — grows with lateral core offset² and cleave-angle
  mismatch². A perfect splice (zero offset, zero angle) is lossless; both inputs are magnitudes.
  round(...,6) matches Python exactly."
  [lateral-offset-um cleave-angle-deg]
  (let [off (Math/abs (double lateral-offset-um))
        ang (Math/abs (double cleave-angle-deg))]
    (py-round (+ (* SPLICE-K-OFFSET off off) (* SPLICE-K-ANGLE ang ang)) 6)))

(defn splice
  "Evaluate a single fusion splice against the acceptance threshold (default fusion ≤0.10 dB)."
  ([lateral-offset-um cleave-angle-deg]
   (splice lateral-offset-um cleave-angle-deg SPLICE-LOSS-MAX-DB))
  ([lateral-offset-um cleave-angle-deg threshold-db]
   (let [loss (splice-loss-db lateral-offset-um cleave-angle-deg)]
     (->splice-result
      {:lateral-offset-um (py-round (Math/abs (double lateral-offset-um)) 6)
       :cleave-angle-deg (py-round (Math/abs (double cleave-angle-deg)) 6)
       :loss-db loss
       :threshold-db threshold-db
       :passed (<= loss threshold-db)}))))

;; ── COMPOSE: lay → align → splice for one segment ──────────────────────────────
(defn lay-align-splice
  "Run the full fibre-segment loop end to end under noroshi's gates (dry-run, R0).

  Gate order (refuse before any modelling):
    1. assert-civilian(use)              — N1/G3 closed-world civilian-use gate.
    2. require-member-signature(...)     — G7 no-server-key: member-signed, no platform sig.
  Then lay (cross-track tracking) → align (the REUSED noroshi Hooke-Jeeves aligner + IEC 60825
  laser gate) → splice (fusion-splice acceptance). Witness quorum (G8) is recorded on the result;
  the whole record is dry-run=true / server-held-key=false at R0 (live actuation is Council
  Lv6+, G8)."
  [route-xte0 member-sig witness-sigs
   & {:keys [use server-sig coupler laser splice-offset-um splice-cleave-angle-deg
             splice-threshold-db lay-kwargs]
      :or {use "lay" server-sig "" coupler nil laser nil
           splice-offset-um 0.4 splice-cleave-angle-deg 0.3
           splice-threshold-db SPLICE-LOSS-MAX-DB lay-kwargs nil}}]
  (sub/assert-civilian use PERMITTED-USES)               ; N1/G3 gate
  (sub/require-member-signature member-sig server-sig)   ; G7 no-server-key gate
  (let [coupler (or coupler (aa/coupler-model))
        ;; align() runs enable-laser() internally → IEC 60825 + N1 laser gate (G5). A weapon
        ;; use raises here.
        laser (or laser (aa/laser-spec :use "alignment"))
        lay (lay-segment route-xte0 (assoc (or lay-kwargs {}) :use use))
        alignment (aa/align coupler laser)
        sp (splice splice-offset-um splice-cleave-angle-deg splice-threshold-db)
        wq (sub/witness-quorum-ok witness-sigs)
        overall-ok (and (:track-converged lay)
                        (:converged alignment)
                        (:passed sp)
                        (:ok wq))]
    (->fibre-segment-result
     {:use use
      :track-converged (:track-converged lay)
      :final-xte-m (:final-xte-m lay)
      :lay-settling-seconds (:settling-seconds lay)
      :coupling-loss-db (:loss-db alignment)
      :align-converged (:converged alignment)
      :splice-loss-db (:loss-db sp)
      :splice-passed (:passed sp)
      :witness-ok (:ok wq)
      :overall-ok (boolean overall-ok)
      :server-held-key false   ; G7
      :dry-run true            ; G8
      :representative true})))  ; G10

(defn to-datoms
  "Project a fibre-segment result into kotoba EAVT-shaped datoms (G9). Aggregate-only, no person
  data (G4). The transactor appends these to the canonical Datom log; here we return the entity
  map a transactor would write. Keys are Python ':…' strings (literal strings, NOT keywords)."
  [result segment-id]
  {":fibre.segment/id" segment-id
   ":fibre.segment/use" (:use result)
   ":fibre.segment/track-converged" (:track-converged result)
   ":fibre.segment/final-xte-m" (:final-xte-m result)
   ":fibre.segment/lay-settling-seconds" (:lay-settling-seconds result)
   ":fibre.segment/coupling-loss-db" (:coupling-loss-db result)
   ":fibre.segment/align-converged" (:align-converged result)
   ":fibre.segment/splice-loss-db" (:splice-loss-db result)
   ":fibre.segment/splice-passed" (:splice-passed result)
   ":fibre.segment/witness-ok" (:witness-ok result)
   ":fibre.segment/overall-ok" (:overall-ok result)
   ":fibre.segment/server-held-key" (:server-held-key result)   ; G7
   ":fibre.segment/dry-run" (:dry-run result)                   ; G8
   ":fibre.segment/representative" (:representative result)})    ; G10

;; ── report (offline artifact, byte-identical to python3 fibre_loop.py) ─────────
(defn- xte-fmt
  "Render a final-xte-m float the way Python's f-string prints round(x,6): -0.0 stays -0.0, 0.0
  stays 0.0, otherwise the shortest round-trip repr (Double/toString matches Python repr here)."
  [x]
  (aa/py-float-repr x))

(defn report
  "Render the fibre-loop face out/ artifact (honest R0 framing; 1:1 with fibre_loop.report)."
  []
  (let [lay (lay-segment 2.0)
        sp (splice 0.4 0.3)
        seg (lay-align-splice 2.0 "m:ed25519:demo"
                              ["did:web:robot-a" "did:web:robot-b"])
        lines ["# noroshi 烽 — fibre-optic infrastructure loop (lay → align → splice)"
               ""
               "## lay (cross-track route tracking)"
               (str "- initial cross-track error : " (xte-fmt (:initial-xte-m lay)) " m")
               (str "- final cross-track error   : " (xte-fmt (:final-xte-m lay)) " m  "
                    "(" (if (:track-converged lay) "converged" "not-converged") " "
                    "in " (xte-fmt (:settling-seconds lay)) "s)")
               ""
               "## align (reused Hooke-Jeeves aligner + IEC 60825 laser gate)"
               (str "- coupling insertion loss   : " (xte-fmt (:coupling-loss-db seg)) " dB  "
                    "(" (if (:align-converged seg) "converged" "not-converged") ")")
               ""
               "## splice (fusion-splice acceptance)"
               (str "- splice loss               : " (xte-fmt (:loss-db sp)) " dB  "
                    "(threshold " (xte-fmt SPLICE-LOSS-MAX-DB) " dB → "
                    (if (:passed sp) "PASS" "FAIL") ")")
               ""
               (str "## segment overall : " (if (:overall-ok seg) "OK" "NOT-OK") "  "
                    "(serverHeldKey=" (if (:server-held-key seg) "True" "False")
                    ", dryRun=" (if (:dry-run seg) "True" "False") ")")
               ""
               (str "> R0 simulation only — no robot, no live laser, no live cable plow, no live "
                    "actuation (G7/G8). A live cable-laying fleet displaces fibre crews ⇒ "
                    "G2-coupled to the Displacement Dividend "
                    "(ADR-2606032130). :representative — arithmetic + the reused aligner, no "
                    "measured device (G10).")]]
    (str/join "\n" lines)))

#?(:clj
   (defn -main
     "CLI entry: print the offline fibre-loop report (1:1 with `python3 fibre_loop.py`)."
     [& _argv]
     (println (report))
     0))
