(ns monosashi.methods.score
  "monosashi 物差し — predictive-actor skill yardstick. ADR-2606271800.

  Binds mitooshi's leak-free proper-scoring with tsuchifumi's system-dynamics into a
  DISTRIBUTION-ONLY reliability band of each predictive actor's forecast skill. Pure +
  deterministic (no Math/random, no wall clock — the caller supplies :eval/as-of).

  Charter invariants enforced HERE (their evaluation home):
    G1/G6 — the output is a p10/p50/p90 BAND over the per-forecast skill ensemble; a single
            point grade is unrepresentable (skill-band has no :eval/point, and
            :eval/point-asserted is structurally false).
    G3    — ANTI-GOODHART reward-firewall: assert-no-reward REFUSES any :eval/reward / :eval/target
            / :eval/payout / :eval/incentive / :eval/stake key (a denylist; the REAL firewall is
            structural — band-datoms whitelists attrs and the lexicon has no reward field). A
            measure must never become a target (mirrors mio's :consumed-reward ban).
    G5    — leak-free: :score/observed-at is REQUIRED on every residual and must be ≤ :eval/as-of,
            compared as parsed instants (offset-correct), never as raw strings.
    G12   — anti-pseudoscience: bands are grouped per (actor, baseline) so skill is always vs ONE
            documented baseline; :eval/skilled is true ONLY if p50 skill > 0."
  (:require [clojure.string :as str]))

;; ── input shape (mitooshi score residual, per scored forecast) ───────────────────────────────
;; {:forecast/actor "mitooshi" :forecast/series-id "transit-load-shibuya" :forecast/fid "f-…"
;;  :forecast/band-width 22.0              ; p90-p10 of the forecast distribution (same-series coherence)
;;  :score/observed-at "2026-06-28T12:00:00Z"   ; REQUIRED (G5); compared as an instant
;;  :score/skill 0.41                       ; mitooshi: 1 - score_model/score_baseline (>0 ⇒ beats baseline)
;;  :score/pit 0.65                         ; F(y) ∈ [0,1]; Uniform if calibrated
;;  :score/baseline-id "climatology"}       ; the ONE documented baseline this skill is vs (G12)

;; ── instant comparison (offset-correct; raw-string compare is unsound off the Z happy path) ────
#?(:clj
   (defn- ->instant [s]
     (try (.toInstant (java.time.OffsetDateTime/parse (str s)))
          (catch Exception _
            (throw (ex-info (str "monosashi: unparseable ISO-8601 timestamp " (pr-str s)
                                 " (need an explicit offset, e.g. …Z or +09:00).") {:ts s}))))))

(defn instant<=
  "True iff timestamp a ≤ b, compared as parsed instants (offset- and fraction-correct)."
  [a b]
  #?(:clj  (<= (compare (->instant a) (->instant b)) 0)
     :cljs (if (and (str/ends-with? (str a) "Z") (str/ends-with? (str b) "Z"))
             (<= (compare (str a) (str b)) 0)   ; Z-normalised string order = time order
             (throw (ex-info "monosashi(cljs): instant compare needs Z-normalised timestamps" {})))))

(defn assert-no-reward
  "G3 ANTI-GOODHART (defense-in-depth denylist). Refuse any reward/target coupling on an evaluation
  map. The structural firewall is band-datoms' attr whitelist + the lexicon having no reward field;
  this is the belt-and-suspenders layer. A measure must never become a reward TARGET (Goodhart)."
  [m]
  (doseq [k [:eval/reward :eval/target :eval/payout :eval/incentive :eval/stake :eval/bounty :eval/prize]]
    (when (contains? m k)
      (throw (ex-info (str "G3: monosashi is a MEASURE, never a reward TARGET — " k
                           " is forbidden (anti-Goodhart; reward lives in mio, decoupled).")
                      {:key k}))))
  m)

(defn- assert-leak-free
  "G5. :score/observed-at is REQUIRED (a missing field must not silently skip the check) and must be
  at-or-before the evaluation as-of, compared as instants."
  [residuals as-of]
  (doseq [r residuals]
    (let [obs (:score/observed-at r)]
      (when (str/blank? (str obs))
        (throw (ex-info (str "G5: residual " (:forecast/fid r) " is missing :score/observed-at "
                             "— refused (cannot verify leak-freedom by omission).") {:fid (:forecast/fid r)})))
      (when-not (instant<= obs as-of)
        (throw (ex-info (str "G5: residual " (:forecast/fid r) " has :score/observed-at " obs
                             " AFTER :eval/as-of " as-of " — cannot grade with future outcomes.")
                        {:fid (:forecast/fid r) :observed-at obs :as-of as-of})))))
  residuals)

(defn percentile
  "Linear-interpolated percentile of a numeric coll. p ∈ [0,1]. Deterministic; empty → nil."
  [coll p]
  (let [xs (vec (sort (map double coll)))
        n (count xs)]
    (cond
      (zero? n) nil
      (= n 1) (first xs)
      :else (let [idx (* (double p) (dec n))
                  lo (int (Math/floor idx))
                  hi (int (Math/ceil idx))
                  frac (- idx lo)]
              (+ (xs lo) (* frac (- (xs hi) (xs lo))))))))

(defn- round4 [v] (when (some? v) (/ (Math/rint (* (double v) 10000.0)) 10000.0)))

(defn calibration-deviation
  "Reliability via PIT-HISTOGRAM deviation Σ|freq − 1/bins| over the residuals' PITs.
  0 = uniform = CALIBRATED; larger = mis-calibrated (over- OR under-confident). This is the
  mitooshi `calibration-summary` primitive — NOT mean|PIT−0.5| (which targets 0.25, not 0, and
  cannot see under-dispersion). nil when no PITs. bins default 10."
  ([residuals] (calibration-deviation residuals 10))
  ([residuals bins]
   (let [pits (keep :score/pit residuals)
         n (count pits)]
     (when (pos? n)
       (let [counts (reduce (fn [cs p]
                              (let [c (max 0.0 (min 1.0 (double p)))
                                    idx (min (int (* c bins)) (dec bins))]
                                (update cs idx inc)))
                            (vec (repeat bins 0)) pits)
             expected (/ 1.0 bins)
             dev (reduce + 0.0 (map #(Math/abs (- (/ (double %) n) expected)) counts))]
         (round4 dev))))))

(defn coherence
  "Cross-check vs tsuchifumi system-dynamics (G6 binding), ONLY when the structural model is over the
  SAME series as the forecasts (same quantity / units). `sysdyn` = {:series s :band-width w}.
  1.0 = forecast band-widths cohere with the structural ensemble spread; decays with relative
  divergence. nil when no matching-series sysdyn context. A same-series DIAGNOSTIC, never the grade."
  [residuals sysdyn]
  (when (and (map? sysdyn) (pos? (double (:band-width sysdyn 0)))
             (every? #(= (:series sysdyn) (:forecast/series-id %)) residuals))
    (let [widths (keep :forecast/band-width residuals)]
      (when (seq widths)
        (let [mean-w (/ (reduce + (map double widths)) (count widths))
              rel (/ (Math/abs (- mean-w (double (:band-width sysdyn)))) (double (:band-width sysdyn)))]
          (round4 (max 0.0 (- 1.0 rel))))))))

(defn skill-band
  "Aggregate residuals (assumed one actor, one baseline) into a DISTRIBUTION-ONLY skill band.
  `opts`: :as-of (required, G5)  :actor  :baseline  :sysdyn (optional same-series {:series :band-width})."
  [residuals {:keys [as-of actor baseline sysdyn]}]
  (when (str/blank? (str as-of))
    (throw (ex-info "monosashi: :as-of is required (G5 leak-free boundary)." {})))
  (assert-leak-free residuals as-of)
  (let [skills (keep :score/skill residuals)
        n (count skills)
        p50 (round4 (percentile skills 0.50))
        record (cond-> {:eval/actor actor
                        :eval/baseline baseline                 ; G12: skill is vs THIS one baseline
                        :eval/as-of as-of
                        :eval/n n
                        :eval/kind ":skill-band"
                        :eval/point-asserted false              ; G1 structural marker — never a point
                        :eval/use ":model-assessment"            ; G2 enum (non-speculative)
                        :eval/p10 (round4 (percentile skills 0.10))
                        :eval/p50 p50
                        :eval/p90 (round4 (percentile skills 0.90))
                        :eval/skilled (boolean (and p50 (pos? (double p50)))) ; G12: only if p50 beats baseline
                        :eval/calibration-deviation (calibration-deviation residuals)}
                 (coherence residuals sysdyn) (assoc :eval/coherence (coherence residuals sysdyn)))]
    (assert-no-reward record)))                                  ; G3 anti-Goodhart firewall

(defn evaluate
  "Group a mixed residual stream by (actor, baseline) and emit one skill band per group — so skill is
  never pooled across different baselines (G12). `opts`: :as-of (required), :sysdyn (optional map
  {actor-handle → {:series :band-width}} applied only to that actor's same-series forecasts).
  Deterministic order ([actor baseline] ascending). Returns a vector of skill-band records."
  [residuals {:keys [as-of sysdyn]}]
  (->> (group-by (juxt :forecast/actor :score/baseline-id) residuals)
       (sort-by key)
       (mapv (fn [[[actor baseline] rs]]
               (skill-band rs {:as-of as-of :actor actor :baseline baseline
                               :sysdyn (get sysdyn actor)})))))

(defn narrative
  "Charter-clean one-line summary of a skill band (no certainty/steer tokens — social.cljc re-scans;
  no Clojure literals leak into the prose)."
  [{:eval/keys [actor baseline p10 p50 p90 skilled calibration-deviation coherence n]}]
  (str actor " の予測スキル(対ベースライン " baseline "): n=" n " 件の分布として "
       "p10=" p10 " / 中央値p50=" p50 " / p90=" p90 "。"
       (if skilled "中央値はベースラインを上回ります。" "中央値はベースラインを上回っていません。")
       (when (< n 5) (str " ※n=" n " と少数のため帯域は外挿です。"))
       (when calibration-deviation (str " 較正逸脱(0=均一が良)=" calibration-deviation "。"))
       (when coherence (str " 同系列の構造ダイナミクス整合度=" coherence "。"))
       " これは可能性の分布であり、断定でも投資助言でもありません。"))
