(ns suji.methods.strain
  "suji (筋) — sustained-load strain → 強張り (stiffness) over a work session. 1:1 Clojure
  port of `methods/strain.py` (ADR-2606061900). Stdlib only.

  緊張 (tension, the instantaneous %MVC from muscle) becomes 強張り (stiffness) when held.
  A static posture is an isometric contraction sustained for the length of a work session,
  and isometric load has a known endurance limit that falls steeply with %MVC (Rohmert).

  Endurance model: T_end(f) ≈ 0.2 · f^-2.32 minutes. Stiffness index ∈ [0,1) =
  1 - exp(-dose), where dose combines the acute and chronic terms over the session.

  NON-DIAGNOSTIC (G1): a stiffness index is a normalised load-time dose, not a medical
  finding. SELF-REFERENCED (G3): indices compared against the SAME member's other postures.

  Numerics: math.inf → Double/POSITIVE_INFINITY; math.exp/pow map to Math/."
  (:require [suji.methods.muscle :as muscle]))

(def chronic-threshold-pct 2.0)   ;; below this %MVC, essentially no sustained recruitment
(def chronic-weight 0.45)         ;; weight of the chronic low-load dose vs acute
(def endurance-floor-pct 8.0)     ;; below this %MVC, acute endurance treated as long

(def ^:private inf Double/POSITIVE_INFINITY)

(defn endurance-minutes
  "Rohmert-type isometric endurance time (minutes) at a given %MVC. Returns ∞ below the
  endurance floor (low-load static work has no acute failure point)."
  [mvc-pct]
  (if (<= mvc-pct endurance-floor-pct)
    inf
    (let [f (/ mvc-pct 100.0)]
      (* 0.2 (Math/pow f -2.32)))))

(defn muscle-strain
  "Stiffness accrued by one muscle holding `mvc-pct` for `session-minutes`."
  [t session-minutes]
  (when (< session-minutes 0)
    (throw (ex-info "session_minutes must be >= 0" {:type :value-error})))
  (let [mvc-pct (:mvc-pct t)
        t-end (endurance-minutes mvc-pct)
        acute (if (Double/isInfinite t-end) 0.0 (/ session-minutes t-end))
        excess (/ (max 0.0 (- mvc-pct chronic-threshold-pct)) 100.0)
        chronic (* chronic-weight excess (/ session-minutes 60.0))
        dose (+ acute chronic)
        stiffness (- 1.0 (Math/exp (- dose)))]
    {:name (:name t)
     :mvc-pct mvc-pct
     :session-minutes session-minutes
     :endurance-minutes t-end
     :acute-dose acute
     :chronic-dose chronic
     :stiffness-index stiffness
     :over-endurance (and (not (Double/isInfinite t-end)) (> session-minutes t-end))}))

(defn session-strain
  "Stiffness map for a whole work session (default 2 hours of continuous posture)."
  ([tensions] (session-strain tensions 120.0))
  ([tensions session-minutes]
   (mapv #(muscle-strain % session-minutes) tensions)))

(defn stiffness-band
  "A coarse human-readable band for the stiffness index (display only, non-diagnostic)."
  [stiffness-index]
  (cond
    (< stiffness-index 0.20) "low"
    (< stiffness-index 0.45) "moderate"
    (< stiffness-index 0.70) "high"
    :else "very-high"))
