(ns minori.measure
  "Real grounding of the η and Φ levers from OBSERVED data (the 観測/計測 step), replacing
   the cold-start stubs:
     η  ← the live ie-flow scoreboard (colony order-export, mean of per-actor η components)
     Φ  ← the ACTUAL roster size: realized coupling ln(adopted) vs the ln(n=18342)≈9.8 ceiling.
   Read-only, no-server-key, fail-open (absent scoreboard ⇒ loop keeps its own estimate)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [minori.capture :as capture]))

(defn read-edn [path] (when (.exists (io/file path)) (edn/read-string (slurp path))))

(defn colony-eta
  "Observed colony η = mean of the per-actor η components on the ie-flow scoreboard.
   nil if the scoreboard is absent (fail-open — the loop stays on its own estimate)."
  [scoreboard-path]
  (when-let [sb (read-edn scoreboard-path)]
    (let [etas (->> (:scored sb) (keep #(get-in % [:components :eta])) (map double))]
      (when (seq etas)
        {:n (count etas)
         :mean (/ (reduce + etas) (count etas))
         :min (apply min etas)
         :max (apply max etas)}))))

(defn realized-phi
  "Realized Φ multiplier = ln(adopted) — the coupling the current roster actually achieves —
   vs the ln(n=18342)≈9.8 ceiling. A REAL (not stub) reading of the Φ lever."
  [adopted]
  (when (and adopted (pos? adopted)) (Math/log (double adopted))))

(defn observe
  "Read-only observation snapshot taken every beat (for transparency in the ledger):
   η from the live scoreboard, Φ from the MEASURED-RUNNING actor count (honest coupling — energy
   flow is realized only by actors actually EXPORTING order on the scoreboard, not by those merely
   HOLDING the spec), capture from the operator snapshot. All read-only, no-server-key, fail-open."
  [{:keys [scoreboard capture-snapshot]} adopted]
  (let [ce      (colony-eta scoreboard)
        running (:n ce)]                       ; actors measured running their reward loop
    {:colony-eta    ce
     :running       running
     :hold-spec     adopted                    ; actors that HOLD the reward spec (system-of-systems.edn)
     :realized-phi  (realized-phi (or running adopted))   ; Φ = ln(measured-running) — real coupling
     :capture       (capture/captured-ratio capture-snapshot)}))

(defn eta-self
  "minori's OWN order-export rectification η — EARNED from real evidence, never asserted.
   A pure observatory consumes reads and returns ALL resulting order to the commons (public,
   content-addressed kotoba datoms + charter-clean dry-run digests + worklists), retaining NOTHING
   privately: it holds no key, sells nothing, and any captured value routes to the Public Fund
   (90/10 tithe), never to minori. So when there is REAL export evidence this run AND nothing is
   privately retained AND it gives freely (charter-clean), minori is a PERFECT net-giver — η=1.0
   (the rectification ceiling, ADR-2606212200: η∈(−∞,1], 1.0 = all consumed order returned).
   Returns nil (no self-claim) absent any of the three — the net-giver gate must be EARNED."
  [{:keys [exported? privately-retained? gives-freely?]}]
  (when (and exported? (not privately-retained?) gives-freely?) 1.0))

(defn ground
  "Apply the observation to state (the 実装/計測 step). Sets GROUNDED fields (truth), distinct from
   the loop's stub estimates: :eta-grounded = max(observed colony mean, minori's earned self-η) —
   monotone; :phi-realized = real ln(adopted); :capture-grounded = the real pre-revenue ratio (≈0).
   Grounding can LOWER an optimistic stub (capture) AND can legitimately RAISE η to 1.0 when minori
   has demonstrably exported everything and retained nothing (:eta-self in obs)."
  [state obs]
  (cond-> state
    (get-in obs [:colony-eta :mean])
      (update :eta-grounded (fnil max 0.0) (get-in obs [:colony-eta :mean]))
    (:eta-self obs)
      (update :eta-grounded (fnil max 0.0) (:eta-self obs))
    (:realized-phi obs)
      (assoc :phi-realized (:realized-phi obs))
    (:capture obs)
      (assoc :capture-grounded (:ratio (:capture obs)))))
