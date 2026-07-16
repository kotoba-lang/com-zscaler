(ns suji.methods.load
  "suji (筋) — static inverse dynamics: posture → joint moments + spinal load. 1:1 Clojure
  port of `methods/load.py` (ADR-2606061900). Stdlib only.

  The bones half. Given the sagittal joint angles (posture) and the segment masses
  (segment), it solves the STATIC inverse-dynamics problem: the gravitational moment each
  joint must resist to hold the posture against gravity. This is the gravity term of the
  Featherstone RNEA reduced to statics — the closed-form static special case.

  EMPIRICAL ANCHOR (G7/G10): the cervical (neck) model reproduces Hansraj (2014) forward-
  head-posture loads: neutral ≈ head weight, rising to ~5× head weight at 60° flexion.

  NON-DIAGNOSTIC (G1, 医師法 §17): every output is a mechanical quantity — a moment (N·m),
  a muscle force (N), a compressive load (N / kg-force). None is a diagnosis.

  Numerics: math.sin/cos/radians map directly to Math/ (last-ULP identical on the JVM).
  Records are kebab-keyword maps; joints kept as an ordered vector (Python list order)."
  (:require [suji.methods.segment :as segment]))

;; --- Cervical lever model (Hansraj-calibrated) -------------------------------
(def head-com-lever-m 0.10)     ;; effective horizontal lever of head CoM at full flexion
(def cervical-ext-arm-m 0.02)   ;; cervical extensor moment arm

(defn- radians [deg] (Math/toRadians deg))

(defn cervical-load
  "Forward-head-posture cervical load. Reproduces Hansraj (2014) (G7 anchor)."
  ([head-flexion-deg head-weight-n]
   (cervical-load head-flexion-deg head-weight-n head-com-lever-m cervical-ext-arm-m))
  ([head-flexion-deg head-weight-n head-com-lever-m extensor-arm-m]
   (when (<= head-weight-n 0)
     (throw (ex-info "head_weight_n must be positive" {:type :value-error})))
   (when (<= extensor-arm-m 0)
     (throw (ex-info "extensor_arm_m must be positive" {:type :value-error})))
   (let [theta (radians head-flexion-deg)
         rho (/ head-com-lever-m extensor-arm-m)
         moment (* head-weight-n head-com-lever-m (Math/sin theta))
         ext-force (/ moment extensor-arm-m)
         compressive (* head-weight-n (+ (* rho (Math/sin theta)) (Math/cos theta)))]
     {:head-flexion-deg head-flexion-deg
      :head-weight-n head-weight-n
      :extensor-moment-nm moment
      :extensor-force-n ext-force
      :compressive-load-n compressive
      :compressive-load-kgf (/ compressive segment/gravity)
      :multiplier-vs-head (/ compressive head-weight-n)})))

(defn cervical-load-sensitivity
  "Marginal cervical compressive load per degree of forward head flexion AT a given posture — the
  local slope d(compressive-load)/d(flexion) of the Hansraj forward-head-posture model, by central
  finite difference. A purely MECHANICAL quantity (N of compressive load per additional degree;
  G1 non-diagnostic — no clinical key, never a prescription) and self-referenced to the SAME posture
  (G3 — the model's local derivative here, never a population rank). It makes a small posture
  change's MODELLED load effect legible: because the load curve is concave, the first degrees off
  neutral cost the most per degree. Returns {:head-flexion-deg :compressive-load-n :d-load-per-deg-n}."
  ([head-flexion-deg head-weight-n] (cervical-load-sensitivity head-flexion-deg head-weight-n 0.5))
  ([head-flexion-deg head-weight-n delta-deg]
   (let [load-at (fn [a] (:compressive-load-n (cervical-load a head-weight-n)))]
     {:head-flexion-deg head-flexion-deg
      :compressive-load-n (load-at head-flexion-deg)
      :d-load-per-deg-n (/ (- (load-at (+ head-flexion-deg delta-deg))
                              (load-at (- head-flexion-deg delta-deg)))
                           (* 2.0 delta-deg))})))

;; --- Generic static joint moment (RNEA gravity term) -------------------------
(defn- ->joint-load
  ([joint moment-nm] (->joint-load joint moment-nm ""))
  ([joint moment-nm note] {:joint joint :moment-nm moment-nm :note note}))

(defn- horizontal-lever
  "Horizontal moment arm of a flexed segment's CoM about its proximal joint:
  length * com-frac * sin(flexion). (Pure-vertical segment → zero lever.)"
  [length-m com-frac flexion-deg]
  (* length-m com-frac (Math/sin (radians flexion-deg))))

(defn shoulder-moment
  "Gravitational moment about the glenohumeral joint from the held-out arm(s). Both arms
  load the shoulder girdle → ×2."
  [body shoulder-flexion-deg elbow-flexion-deg arms-supported]
  (let [ua (segment/seg body "upper_arm")
        fa (segment/seg body "forearm")
        hand (segment/seg body "hand")
        m0 (* (segment/weight-n ua)
              (horizontal-lever (:length-m ua) (:com-frac ua) shoulder-flexion-deg))
        m (if-not arms-supported
            (let [elbow-x (* (:length-m ua) (Math/sin (radians shoulder-flexion-deg)))
                  fa-x (+ elbow-x (horizontal-lever (:length-m fa) (:com-frac fa)
                                                    (- 90.0 elbow-flexion-deg)))
                  hand-x (+ elbow-x
                            (* (:length-m fa) (Math/sin (radians (- 90.0 elbow-flexion-deg))))
                            (horizontal-lever (:length-m hand) (:com-frac hand)
                                              (- 90.0 elbow-flexion-deg)))]
              (+ m0 (* (segment/weight-n fa) fa-x) (* (segment/weight-n hand) hand-x)))
            m0)]
    (->joint-load "shoulder" (* m 2.0)
                  (if arms-supported "forearms supported" "arms unsupported (hanging)"))))

(defn lumbosacral-moment
  "Gravitational moment about L5/S1 from the leaned trunk + head-arm load above it."
  [body trunk-flexion-deg head]
  (let [thorax (segment/seg body "thorax_abdomen")
        m0 (* (segment/weight-n thorax)
              (horizontal-lever (:length-m thorax) (:com-frac thorax) trunk-flexion-deg))
        head-x (* (:length-m thorax) (Math/sin (radians trunk-flexion-deg)))
        m (+ m0 (* (:head-weight-n head) head-x))]
    (->joint-load "lumbosacral" m "trunk lean + carried head")))

(defn solve-posture-loads
  "Full static inverse-dynamics solve for a posture (the RNEA gravity term)."
  [body posture]
  (let [head-w (* (segment/head-mass-kg (:total-mass-kg body)) segment/gravity)
        cerv (cervical-load (:head-flexion-deg posture) head-w)
        joints [(->joint-load "cervicothoracic" (:extensor-moment-nm cerv)
                              "cervical extensor moment")
                (shoulder-moment body (:shoulder-flexion-deg posture)
                                 (:elbow-flexion-deg posture) (:arms-supported posture))
                (lumbosacral-moment body (:trunk-flexion-deg posture) cerv)]]
    {:cervical cerv :joints joints}))
