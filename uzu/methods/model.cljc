#!/usr/bin/env bb
;; uzu 渦 — generative model: the INFORMATION half (free energy in nats, not joules).
(ns uzu.methods.model
  "model.cljc — uzu 渦 generative model + free-energy machinery (ADR-2606211500).

  This is the INFORMATION side of the information-energy coupled organism. Everything
  here is measured in NATS / probability, NOT in the metabolic energy units of
  ledger.cljc. The two are coupled (metabolism.cljc maps an action choice to an energy
  cost) but DELIBERATELY never share a unit — equating information and energy is the
  'philosophy soup' the design warns against (info is copyable + non-conserved; energy
  is conserved + depletes).

  The organism is an active-inference agent over a tiny discrete world:
    hidden regimes  R = {:scarce :benign :abundant :hostile}     (the truth, unseen)
    observation     o = {:nutrient ∈0..1 :threat ∈0..1}          (a noisy signal)
    belief          q = P(R)                                     (the μ — the 'vortex')
    preference      C = a target observation                     (= what MATTERS to
                                                                    THIS subject = meaning)

  μ (the belief) is reconstructed as a FOLD over the perception history (`fold-beliefs`)
  — the information structure lives as a fold over the append-only log, not as a stored
  mutable cell. Perception updates the belief by minimizing variational free energy
  (`update-belief` = the discrete Bayesian step). Action is chosen by minimizing
  EXPECTED free energy (`choose`) = pragmatic value (match preference C) + epistemic
  value (resolve uncertainty). Because C is the only subject-specific term, the SAME
  perception drives DIFFERENT actions in two organisms with different C — meaning is
  subject-dependent, by construction (test-enforced)."
  (:require [clojure.string :as str]))

(def regimes [:scarce :benign :abundant :hostile])

;; what each regime is EXPECTED to look like (the likelihood means)
(def regime-signature
  {:scarce   {:nutrient 0.15 :threat 0.20}
   :benign   {:nutrient 0.55 :threat 0.15}
   :abundant {:nutrient 0.90 :threat 0.10}
   :hostile  {:nutrient 0.40 :threat 0.85}})

;; what an ACTION is expected to MAKE the next observation look like, per regime.
;; forage actively gathers (predicts more nutrient than passive observation);
;; flee retreats to safety (low threat, unfed) regardless of regime;
;; rest / explore just leave the regime's own signature (explore adds info, below).
(def forage-outcome
  {:scarce {:nutrient 0.45 :threat 0.20} :benign {:nutrient 0.80 :threat 0.15}
   :abundant {:nutrient 0.95 :threat 0.10} :hostile {:nutrient 0.55 :threat 0.85}})
(def flee-outcome {:nutrient 0.10 :threat 0.05})

(defn predicted-outcome [action regime]
  (case action
    :forage  (get forage-outcome regime)
    :flee    flee-outcome
    (:rest :explore) (get regime-signature regime)))

;; ── probability helpers ──────────────────────────────────────────────────────
(defn- sq [x] (* (double x) (double x)))
(defn dist2
  "Squared distance between two {:nutrient :threat} observations."
  [a b]
  (+ (sq (- (:nutrient a) (:nutrient b)))
     (sq (- (:threat a) (:threat b)))))

(def pref-weights
  "Per-dimension urgency for the PREFERENCE distance (pragmatic value only — NOT
  perception). Threat is weighted heavier than appetite: a threat-averse organism
  treats danger as more urgent than a missed meal, so it reliably retreats from a
  hostile regime instead of gambling a forage. Perception (likelihoods) stays unweighted."
  {:nutrient 1.0 :threat 3.0})

(defn pref-dist2
  "Weighted squared distance for preference matching (urgency-weighted)."
  [a b]
  (+ (* (:nutrient pref-weights) (sq (- (:nutrient a) (:nutrient b))))
     (* (:threat pref-weights)   (sq (- (:threat a) (:threat b))))))

(def uniform (let [p (/ 1.0 (count regimes))] (zipmap regimes (repeat p))))

(defn normalize [m]
  (let [z (reduce + (vals m))]
    (if (<= z 0.0) uniform
        (into {} (map (fn [[k v]] [k (/ v z)]) m)))))

(defn entropy
  "Shannon entropy of the belief in nats (information, NOT energy)."
  [q]
  (- (reduce + (map (fn [p] (if (> p 1e-12) (* p (Math/log p)) 0.0)) (vals q)))))

(defn likelihoods
  "P(o | regime) for each regime, as a softmax over -distance / temp.
  Lower temp ⇒ sharper (more confident) inference."
  [obs temp]
  (let [t (max 1e-9 (double temp))]
    (into {} (map (fn [r] [r (Math/exp (- (/ (dist2 obs (regime-signature r)) t)))]) regimes))))

(def default-leak
  "Volatility / forgetting: how much the prior decays toward uniform before each
  perception. The world's regime CHANGES every step, so a static Bayesian filter would
  freeze on early evidence and stop tracking. Leaking the prior toward uniform lets a
  fresh observation dominate — the organism keeps re-reading a changing world."
  0.7)

(defn predict
  "Predictive prior: relax the belief toward uniform by `leak` (a crude transition model
  for a volatile world)."
  [q leak]
  (let [u (/ 1.0 (count regimes))]
    (normalize (into {} (map (fn [r] [r (+ (* (- 1.0 leak) (get q r 0.0)) (* leak u))]) regimes)))))

(defn update-belief
  "One perception step: relax the prior for volatility, then posterior ∝ prior ×
  likelihood. This minimizes the variational free energy of the observation under the
  (predictive) belief in the discrete case."
  ([q obs temp] (update-belief q obs temp default-leak))
  ([q obs temp leak]
   (let [qp (predict q leak)
         lik (likelihoods obs temp)]
     (normalize (into {} (map (fn [r] [r (* (get qp r 0.0) (get lik r 0.0))]) regimes))))))

(defn free-energy
  "Variational free energy ≈ surprise of `obs` under belief `q` (negative log model
  evidence), in NATS. This is the quantity inference minimizes; it is NOT subtracted
  from the energy ledger."
  [q obs temp]
  (let [lik (likelihoods obs temp)
        evidence (reduce + (map (fn [r] (* (get q r 0.0) (get lik r 0.0))) regimes))]
    (- (Math/log (max 1e-12 evidence)))))

(defn fold-beliefs
  "μ as a FOLD over the perception history: replay observations from `prior` to
  reconstruct the current belief. The information structure is the fold, not a cell."
  [prior observations temp]
  (reduce (fn [q obs] (update-belief q obs temp)) prior observations))

(defn most-likely [q] (key (apply max-key val q)))

;; ── action selection: minimize EXPECTED free energy ──────────────────────────
(def actions
  "Canonical action order — also the tie-break priority for argmin."
  [:forage :flee :rest :explore])

(defn pragmatic-cost
  "Expected distance of an action's predicted outcome from the preference C,
  marginalized over the belief q. Lower = the action is expected to realize what
  this subject prefers. C is the ONLY subject-specific input here = meaning."
  [q C action]
  (reduce + (map (fn [r] (* (get q r 0.0) (pref-dist2 (predicted-outcome action r) C))) regimes)))

(def epistemic-weight
  "Weight of the epistemic (information-gain) drive relative to the pragmatic
  (preference-match) drive. Small: pragmatic value leads; exploration only wins when
  preferences are nearly indifferent AND the belief is uncertain. (Pragmatic costs are
  squared distances ≲ 2, so this must stay well below 1 or the agent explores forever.)"
  0.02)

(defn epistemic-value
  "Information the action is expected to yield. Only :explore is epistemically
  driven, scaled by current belief entropy (uncertain ⇒ explore is worth more)."
  [q action]
  (if (= action :explore) (* epistemic-weight (entropy q)) 0.0))

(defn expected-free-energy
  "G(a) = pragmatic cost − epistemic value. Minimized over actions."
  [q C action]
  (- (pragmatic-cost q C action) (epistemic-value q action)))

(defn choose
  "Pick the action minimizing expected free energy among the AFFORDABLE actions
  (affordability is the energy ledger's veto — a starving organism can only rest).
  `affordable` is a seq of action keywords; canonical order breaks ties."
  [q C affordable]
  (let [ranked (filter (set affordable) actions)
        cand (if (seq ranked) ranked [:rest])]
    (apply min-key #(expected-free-energy q C %) cand)))
