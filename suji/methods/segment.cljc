(ns suji.methods.segment
  "suji (筋) — anthropometric sagittal-plane segment chain. 1:1 Clojure port of
  `methods/segment.py` (ADR-2606061900). Stdlib only.

  The skeleton this actor reasons over is a 2-D (sagittal) articulated chain of rigid
  body segments — the `PlanarChain` articulation that kami-genesis solves. Each segment
  carries a mass, a length, and a centre-of-mass (CoM) location along its long axis,
  derived from total body mass M (kg) and stature H (m) using standard regression
  fractions (Winter 4e Table 4.1 / Drillis & Contini via Winter).

  These are population-average values for an adult; they are :representative (G7), not a
  scan of any individual.

  NON-DIAGNOSTIC (G1): this module computes masses and lengths. It says nothing about
  health. It is the mass-distribution input to a statics problem.

  House style: a Segment / BodyModel is a kebab-keyword map; pure fns. The :weight-n and
  :com-m are derived on demand by `weight-n` / `com-m` (mirroring the Python @property)."
  (:require [clojure.string :as str]))

(def gravity 9.80665)                       ;; m/s^2

;; Winter (4e) Table 4.1 — segment mass as a fraction of total body mass M.
(def ^:private mass-frac
  {"head_neck" 0.081
   "thorax_abdomen" 0.355
   "pelvis" 0.142
   "upper_arm" 0.028
   "forearm" 0.016
   "hand" 0.006})

;; Segment length as a fraction of stature H (Drillis & Contini via Winter).
(def ^:private len-frac
  {"head_neck" 0.182
   "thorax_abdomen" 0.288
   "pelvis" 0.095
   "upper_arm" 0.186
   "forearm" 0.146
   "hand" 0.108})

;; CoM location as a fraction of segment length, from the PROXIMAL joint (Winter Table 4.1).
(def ^:private com-frac
  {"head_neck" 0.55
   "thorax_abdomen" 0.50
   "pelvis" 0.50
   "upper_arm" 0.436
   "forearm" 0.430
   "hand" 0.506})

;; The Python _MASS_FRAC dict iteration order (insertion order) drives build_body's loop;
;; preserve it so the segments map matches Python exactly.
(def ^:private segment-order
  ["head_neck" "thorax_abdomen" "pelvis" "upper_arm" "forearm" "hand"])

(defn weight-n
  "Gravitational force on this segment (a single segment; not the pair)."
  [seg]
  (* (:mass-kg seg) gravity))

(defn com-m
  "Distance of the CoM from the proximal joint, along the long axis."
  [seg]
  (* (:com-frac seg) (:length-m seg)))

(defn seg
  "BodyModel.seg(name) — segment by name."
  [body name]
  (get (:segments body) name))

(def ^:private paired-set #{"upper_arm" "forearm" "hand"})

(defn build-body
  "Construct the sagittal segment chain for a member of mass M and stature H.

  Paired limb segments (arm/forearm/hand) store the mass of ONE limb; callers that load
  both arms onto a single midline joint multiply by 2."
  ([] (build-body 70.0 1.70))
  ([total-mass-kg] (build-body total-mass-kg 1.70))
  ([total-mass-kg stature-m]
   (when (or (<= total-mass-kg 0) (<= stature-m 0))
     (throw (ex-info "total_mass_kg and stature_m must be positive"
                     {:type :value-error})))
   (let [segments (reduce
                   (fn [m name]
                     (assoc m name
                            {:name name
                             :mass-kg (* (mass-frac name) total-mass-kg)
                             :length-m (* (len-frac name) stature-m)
                             :com-frac (com-frac name)
                             :paired (contains? paired-set name)}))
                   ;; array-map preserves insertion order for ≤8 keys (we have 6).
                   (array-map)
                   segment-order)]
     {:total-mass-kg total-mass-kg :stature-m stature-m :segments segments})))

(defn head-mass-kg
  "Head+neck mass. ~5.4 kg at 67 kg matches Hansraj's 12-lb head (G7 anchor)."
  ([] (head-mass-kg 70.0))
  ([total-mass-kg] (* (mass-frac "head_neck") total-mass-kg)))
