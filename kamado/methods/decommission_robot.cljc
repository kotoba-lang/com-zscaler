(ns kamado.methods.decommission-robot
  "decommission-robot — kamado 竈 hot-zone decommission robot loop (R0 :representative).
  1:1 Clojure port of methods/decommission_robot.py (ADR-2606051500 / 2606091800).

  A robot REMOVES THE HUMAN from the H₂S / benzene / pyrophoric hot zone of a fossil
  refinery being wound down (G9 — labor liberation; the freed worker routes to the
  Basic-High-Income cohort). The crux is a hard safety gate: the robot may ONLY enter
  and cut once the atmosphere has been purged / inerted BELOW the entry limit. Entry
  into an un-purged zone is structurally unrepresentable — it raises SafetyError,
  never a warning.

  Two runnable, tested halves:
    * `purge-to-entry` — a closed-loop purge/inert controller (substrate PID via
      `simulate`) drives the hazardous-gas concentration of a GasConcentrationPlant
      down to a target entry limit and reports whether entry is permitted.
    * `plan-cut-entry` — the entry + cut motion plan, gated fail-fast on:
        G3  intervention is decommission/remediate/convert/monitor/purge/inert only
            (closed-world civilian allowlist; restart-fossil/expand cannot pass)
        ENTRY GATE  final-ppm > gas-limit ⇒ SafetyError (the safety crux)
        G5/G7  no-server-key: member/operator signs, platform never
        G8     witness quorum ≥2 independent robot DIDs
        safety envelope  per-step joint-rate ceiling (slower if a human may be present)

  kamado gates apply: offline sim only (cell.py .solve() stays Council-gated, G8); no
  process actuation; server-held-key false, dry-run true on every record (G5/G11).

  House style: Python ':…' strings stay literal strings; Python dict keys ↔ kebab keyword
  result keys; pure fns; I/O at #?(:clj) edges. ALL round()/{:.Nf} = HALF_EVEN on the
  exact double via substrate. IK iteration order + accept/reject reproduced exactly;
  mutable plant state in an atom for bit-faithful accumulation."
  (:require [kamado.methods.substrate :as sub]))

;; kamado decommission/transition civilian-use allowlist (closed-world, N1/G3).
;; A "restart-fossil"/"expand"/"throughput-revamp" use is NOT a member, so the
;; closed-world refusal in assert-civilian rejects it (mirrors G3 of the
;; decommission_plan state machine — fossil life-extension is unrepresentable).
(def PERMITTED-USES ["decommission" "remediate" "purge" "inert" "monitor" "convert"])

;; Representative atmospheric entry limits (ppm). OSHA-style references; the exact
;; numbers are :representative — the structural gate is "below the limit".
(def H2S-ENTRY-PPM 10.0)
(def BENZENE-ENTRY-PPM 1.0)

;; Otete-class purge/cut arm :representative geometry — a 2-link planar reach (m).
(def KAMADO-ARM (sub/->planar-arm [1.2 1.0]))

;; ── GasConcentrationPlant (mutable plant; state in an atom) ──────────────────
;; Hazardous-gas concentration dynamics of a confined refinery zone (ppm):
;;   dC/dt = -k_purge · command + leak   (C floored at 0)
;; A purge / inert flow (positive = forced ventilation / N₂ inerting) sweeps the gas
;; out; a small constant leak feeds it back in, so a finite purge flow is needed just
;; to hold a low concentration. The atom mirrors the Python dataclass' in-place mutation.
(defn ->gas-concentration-plant
  [{:keys [k-purge leak c]
    :or {k-purge 0.20 leak 0.30 c 120.0}}]
  (atom {:k-purge k-purge :leak leak :c c}))

(defn gas-measure [plant] (:c @plant))

(defn gas-step!
  "Advance the zone by dt seconds under purge-flow `command`. flow = max(0, command)
  (purge flow is physically non-negative); C floored at 0."
  [plant command dt]
  (swap! plant
         (fn [{:keys [k-purge leak c] :as st}]
           (let [flow (max 0.0 command)
                 dcdt (+ (* (- k-purge) flow) leak)]
             (assoc st :c (max 0.0 (+ c (* dcdt dt)))))))
  nil)

(defn purge-time
  "MODELLED time (s) for a hazardous-gas zone to fall from concentration `c0` to the safe-entry
  target `c-target` under a CONSTANT purge flow — the linear-dilution model `gas-step!` integrates:
  dc/dt = leak − k-purge·flow (constant for a fixed flow), so t = (c0 − c-target)/(k-purge·flow −
  leak). Returns nil when the purge cannot win against the leak (k-purge·flow ≤ leak — an UNVENTABLE
  zone the entry gate must refuse), and 0.0 when already at/below target. This is a MODELLING
  estimate of the entry-wait (so robotics can sequence the H₂S/benzene hot-zone decommission, G9),
  NOT a certified safe-entry clearance — actual entry requires certified continuous gas detection,
  never a computed time (G11 safety-honesty: kamado is not a certified safety system). It models; it
  neither actuates nor clears entry. Takes the zone spec (`:k-purge`, `:leak`) + the purge flow +
  c0 + c-target."
  [{:keys [k-purge leak] :or {leak 0.0}} flow c0 c-target]
  (let [net-rate (- (* k-purge (max 0.0 flow)) leak)]
    (cond
      (<= c0 c-target) 0.0
      (<= net-rate 0.0) nil
      :else (/ (- c0 c-target) net-rate))))

;; ── PurgeController — PI purge controller driving concentration DOWN ─────────
;; substrate `simulate` calls (step error dt) with error = setpoint − pv = target − C.
;; When C is above target this error is negative; the purge flow must be POSITIVE to
;; sweep the gas out, so the controller acts on the *negated* error. Output is clamped
;; to a non-negative purge-flow band (anti-windup inherited from the PID).
(defn ->purge-controller
  [{:keys [kp ki max-flow] :or {kp 1.2 ki 0.6 max-flow 200.0}}]
  {:pid (sub/->pid {:kp kp :ki ki :out-min 0.0 :out-max max-flow})})

(defn- purge-controller-reset [c]
  (sub/pid-reset (:pid c)))

(defn- purge-controller-step [c error dt]
  ;; error = target - C; demand to reduce = C - target = -error.
  (sub/pid-step (:pid c) (- error) dt))

;; ── purge-to-entry ───────────────────────────────────────────────────────────
(defn purge-to-entry
  "Run the purge/inert loop until hazardous-gas concentration ≤ target. Returns a
  PurgeResult-shaped map (kebab keys mirror the Python dataclass field names).

  Raises (assert-civilian) BEFORE any run if `use` is non-civilian / a fossil
  life-extension verb. `:entry-permitted` is true iff the loop converges with the
  final concentration at/below the target entry limit — the value the entry gate in
  `plan-cut-entry` consumes."
  [& {:keys [gas target-ppm use initial-ppm k-purge leak kp ki max-flow steps dt]
      :or {use "purge" initial-ppm 120.0 k-purge 0.20 leak 0.30
           kp 1.2 ki 0.6 max-flow 200.0 steps 6000 dt 0.1}}]
  (sub/assert-civilian use PERMITTED-USES) ; N1/G3 gate before any actuation modelling
  (let [zone (->gas-concentration-plant {:k-purge k-purge :leak leak :c initial-ppm})
        controller (->purge-controller {:kp kp :ki ki :max-flow max-flow})
        ;; tol is a concentration band around the target entry limit (ppm).
        res (sub/simulate {:plant zone
                           :measure-fn gas-measure
                           :plant-step-fn gas-step!
                           :controller controller
                           :controller-reset-fn purge-controller-reset
                           :controller-step-fn purge-controller-step
                           :setpoint target-ppm
                           :steps steps :dt dt :tol 0.25})
        final-ppm (sub/py-round (gas-measure zone) 4)
        settling-seconds (if (>= (:settling-step res) 0)
                           (* (:settling-step res) dt)
                           -1.0)
        ;; Entry is permitted only when the loop actually settled at/below the limit.
        entry-permitted (and (:converged res) (<= final-ppm (+ target-ppm 1e-6)))]
    {:use use
     :gas gas
     :target-ppm target-ppm
     :initial-ppm initial-ppm
     :final-ppm final-ppm
     :entry-permitted entry-permitted
     :purge-seconds (sub/py-round (* steps dt) 3)
     :settling-seconds (sub/py-round settling-seconds 3)
     :representative true}))

;; ── plan-cut-entry ───────────────────────────────────────────────────────────
(defn plan-cut-entry
  "Plan the robot's entry + cut into a hot zone. THE ENTRY GATE IS THE CRUX.

  Gate order is fail-fast:
    1. G3 / N1   civilian-use allowlist (assert-civilian) — restart-fossil/expand cannot pass.
    2. ENTRY GATE  `final-ppm > gas-limit` ⇒ SafetyError. The robot MUST NOT enter an
                   un-purged zone; this is structural, not advisory.
    3. G5/G7     no-server-key (require-member-signature).
    4. G8        witness quorum ≥2 (recorded, escalates Council Lv6+ on a miss; does not raise).
    5. envelope  IK reach + per-step joint-rate ceiling (slower if a human may be present).

  Returns a dry-run plan map (server-held-key false, dry-run true). String keys mirror
  the Python dict's camelCase attr names (':…' kept literal)."
  [& {:keys [target-xy final-ppm gas-limit member-sig witness-sigs use q-start
             human-present steps dt server-sig displacement-cohort-ref]
      :or {use "decommission" q-start [0.0 0.0] human-present false steps 60 dt 0.1
           server-sig "" displacement-cohort-ref "bhi:cohort:hot-zone-decommission"}}]
  (sub/assert-civilian use PERMITTED-USES) ; G3/N1 — closed-world refusal of fossil life-extension

  ;; ── THE SAFETY CRUX (entry gate): never enter an un-purged hot zone. ──
  (when (> final-ppm gas-limit)
    (throw (sub/safety-error
            (str "ENTRY REFUSED: zone hazardous-gas concentration " (sub/fmt-fixed final-ppm 3)
                 " ppm exceeds the entry limit " (sub/fmt-fixed gas-limit 3) " ppm. The robot must "
                 "NOT enter or cut until the atmosphere is purged/inerted below the limit "
                 "(structural gate, G11/G9 — this removes the human from the hot zone, it never "
                 "substitutes an unsafe entry)."))))

  (sub/require-member-signature member-sig server-sig) ; G5/G7
  (let [quorum (sub/witness-quorum-ok witness-sigs) ; G8 (record, do not raise)
        [x y] target-xy
        reachable (sub/reachable KAMADO-ARM x y)
        joints-goal (when reachable (sub/ik2 KAMADO-ARM x y true))
        env (sub/->safety-envelope {:max-joint-speed 1.0 :human-proximity-speed 0.25
                                    :max-reach (sub/max-reach KAMADO-ARM)})
        [traj envelope-ok violations]
        (if (some? joints-goal)
          (let [traj (sub/joint-trajectory q-start joints-goal steps)
                check (sub/check-trajectory env traj dt human-present)]
            [traj (:ok check) (:violations check)])
          [[] false []])]
    {":use" use
     ":targetXy" (vec target-xy)
     ":finalPpm" (sub/py-round final-ppm 4)
     ":gasLimitPpm" (sub/py-round gas-limit 4)
     ":entryPermitted" true ; passed the entry gate above
     ":reachable" reachable
     ":jointsGoal" (when (some? joints-goal) (vec joints-goal))
     ":trajectorySteps" (count traj)
     ":envelopeOk" envelope-ok
     ":envelopeViolations" violations
     ":humanPresent" human-present
     ":memberSig" member-sig
     ":witnessOk" (:ok quorum)
     ":escalateCouncilLv6" (get quorum :escalate-council-lv6 false)
     ":displacementCohortRef" displacement-cohort-ref ; G9: freed worker → BHI cohort
     ":serverHeldKey" false ; G5 structural invariant
     ":dryRun" true}))     ; G8/G11: R0 offline only

;; ── to-datoms ─────────────────────────────────────────────────────────────────
(defn to-datoms
  "Project a purge + entry-cut plan into kotoba EAVT-shaped datoms (G6). Aggregate-only
  (no person data; G4). Python ':…' attr names stay literal string keys."
  [purge plan & {:keys [job-id refinery robot-id] :or {robot-id "kamado-arm-01"}}]
  {":decommission/id" job-id
   ":decommission/refinery" refinery
   ":decommission/robot" robot-id
   ":decommission/use" (get plan ":use")
   ":decommission/gas" (:gas purge)
   ":decommission/target-ppm" (:target-ppm purge)
   ":decommission/initial-ppm" (:initial-ppm purge)
   ":decommission/final-ppm" (:final-ppm purge)
   ":decommission/entry-permitted" (get plan ":entryPermitted")
   ":decommission/purge-settling-seconds" (:settling-seconds purge)
   ":decommission/reachable" (get plan ":reachable")
   ":decommission/trajectory-steps" (get plan ":trajectorySteps")
   ":decommission/envelope-ok" (get plan ":envelopeOk")
   ":decommission/human-present" (get plan ":humanPresent")
   ":decommission/member-sig" (get plan ":memberSig")
   ":decommission/witness-ok" (get plan ":witnessOk")
   ":decommission/displacement-cohort-ref" (get plan ":displacementCohortRef") ; G9
   ":decommission/server-held-key" (get plan ":serverHeldKey") ; G5: always false
   ":decommission/representative" (:representative purge) ; G11
   ":decommission/dry-run" (get plan ":dryRun")})           ; G8/G11
