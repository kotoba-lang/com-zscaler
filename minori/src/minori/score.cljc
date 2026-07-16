(ns minori.score
  "The GROWTH score minori climbs — the composite of ADR-2606261114's four levers,
   read ON DEMAND from the committed valuation MAP + the live SoS roster (edge-primary,
   no stored verdict). Non-parasitism gated: η<1 ⇒ raw growth is NOT rewarded; the
   reward is the movement of η toward 1 + SoS adoption (the Part-3 capture-rate lever)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn read-edn [path] (edn/read-string (slurp path)))

(defn clamp01 [x] (max 0.0 (min 1.0 (double x))))

(defn roster-adoption
  "Adoption = fraction of the actor roster that holds + runs its SoS reward (ADR-2606212200).
   adopted = count of :actors in system-of-systems.edn; target from the score model."
  [sos-path target]
  (let [adopted (count (:actors (read-edn sos-path)))]
    {:adopted adopted :target target :p (clamp01 (/ (double adopted) (double target)))}))

(defn growth
  "Compute G ∈ [0,1] + components + the non-parasitism-gated reward.
   STUB vs GROUNDED honesty: `:eta-estimate`/`:capture-estimate` are the loop's optimistic
   projections; `:eta-grounded`/`:capture-grounded` are set ONLY by real measurement (the
   scoreboard / the valuation MAP). The η/capture COMPONENTS use grounded-if-present (truth
   beats projection), and — critically — the NET-GIVER GATE fires only on GROUNDED η, so a
   stub increment can never win the phase transition: η≥1 must be MEASURED, never assumed."
  [{:keys [eta-estimate capture-estimate phi-realized eta-grounded capture-grounded]
    :or {eta-estimate 0.0 capture-estimate 0.0 phi-realized 0.0}}
   model adoption]
  (let [{:keys [weights targets]} model
        eta-eff     (double (or eta-grounded eta-estimate))                ; truth beats projection
        capture-eff (double (or capture-grounded capture-estimate))
        eta-p      (clamp01 eta-eff)
        capture-p  (clamp01 (/ capture-eff (double (:capture targets))))
        phi-p      (clamp01 (/ (double phi-realized) (double (:phi-potential targets))))
        adopt-p    (clamp01 (:p adoption))
        comps      {:eta eta-p :adoption adopt-p :capture capture-p :phi phi-p}
        G          (reduce-kv (fn [acc k w] (+ acc (* w (get comps k 0.0)))) 0.0 weights)
        ;; non-parasitism: a net taker is never rewarded for raw growth. The gate is on GROUNDED η
        ;; only — a stub can project but never CROSS it. reward = η+adoption movement while gated.
        net-giver? (>= (double (or eta-grounded 0.0)) (double (:eta targets)))
        reward     (if net-giver?
                     G
                     (* 0.5 (+ eta-p adopt-p)))]               ; gated: only the give-back levers count
    {:G (double G)
     :components comps
     :eta eta-eff
     :eta-grounded eta-grounded
     :grounded? (boolean eta-grounded)
     :net-giver? net-giver?
     :gated? (not net-giver?)
     :reward (double reward)}))
