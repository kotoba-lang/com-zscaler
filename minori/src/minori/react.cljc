(ns minori.react
  "The react beat: 観測(sense) → 計測(measure) → 仮説(hypothesize) → 実装/行動(act, DRY-RUN)
   → 評価(observe/learn) → persist. Charter-clean: the intervention catalog cannot represent a
   predatory mechanism, an action that lowers η (net-taker), or an outward send (no-server-key /
   dry-run only). Each beat advances the growth estimates toward the targets by one charter-aligned
   step and returns the growth delta dG for evaluation."
  (:require [minori.score :as score]))

(defn catalog
  "The ONLY representable interventions — each a give-back lever toward the score.
   :kind ∈ {:observe :measure :implement :social-action :symbiosis}. No :extract/:capture/:manipulate
   member exists (the property that makes the loop charter-clean)."
  []
  [{:id :wire-donation-flow   :kind :observe       :cost 0.20 :wellbecoming 0.9 :d {:capture-estimate 0.0015}}
   {:id :wire-oss-adoption    :kind :observe       :cost 0.20 :wellbecoming 0.9 :d {:capture-estimate 0.0015}}
   {:id :build-eta-meter      :kind :measure       :cost 0.30 :wellbecoming 0.8 :d {:phi-realized 0.8}}
   {:id :build-phi-meter      :kind :measure       :cost 0.30 :wellbecoming 0.8 :d {:phi-realized 0.8}}
   {:id :deepen-symbiosis     :kind :symbiosis     :cost 0.40 :wellbecoming 1.0 :d {:eta-estimate 0.10}}
   {:id :prepare-donor-digest :kind :social-action :cost 0.25 :wellbecoming 0.7 :d {:capture-estimate 0.0010}}
   {:id :prepare-contrib-call :kind :social-action :cost 0.25 :wellbecoming 0.7 :d {:capture-estimate 0.0010}}])

(defn- project
  "Projected G after applying intervention i's dry-run deltas to state."
  [state model adoption i]
  (let [state' (merge-with + (select-keys state [:eta-estimate :capture-estimate :phi-realized])
                           (:d i))
        g0 (:G (score/growth state model adoption))
        g1 (:G (score/growth (merge state state') model adoption))]
    (assoc i :state' (merge state state') :dG (- g1 g0))))

(defn rank
  "Score each charter-clean intervention by projected ΔG × wellbecoming / cost; pick argmax.
   Gate: drop any intervention that would LOWER η (non-parasitism — never reward a net-taker move)."
  [state model adoption]
  (let [projected (->> (catalog)
                       (map #(project state model adoption %))
                       (remove #(neg? (get-in % [:d :eta-estimate] 0.0)))   ; never lower η
                       (map #(assoc % :utility (/ (* (max (:dG %) 1e-9) (:wellbecoming %)) (:cost %)))))]
    (apply max-key :utility projected)))

(defn beat
  "Run one react beat over the loop state. Returns the measurement, the pick, the next state,
   the growth delta, and the gated reward. `done` = the set of already-applied intervention ids
   (so a repeated beat advances to the NEXT lever rather than re-pulling the same one)."
  [{:keys [state done] :or {state {} done #{}}} model adoption]
  (let [m0   (score/growth state model adoption)
        ;; prefer an intervention not yet applied; if all applied, allow symbiosis (η keeps climbing)
        fresh (remove #(contains? done (:id %)) (catalog))
        pool  (if (seq fresh) fresh (filter #(= :symbiosis (:kind %)) (catalog)))
        pick  (->> pool
                   (map #(project state model adoption %))
                   (remove #(neg? (get-in % [:d :eta-estimate] 0.0)))
                   (map #(assoc % :utility (/ (* (max (:dG %) 1e-9) (:wellbecoming %)) (:cost %))))
                   (apply max-key :utility))
        state' (:state' pick)
        m1   (score/growth state' model adoption)]
    {:measure-before m0
     :pick   (select-keys pick [:id :kind :dG :utility :wellbecoming :cost])
     :state' state'
     :done'  (conj done (:id pick))
     :measure-after m1
     :dG     (- (:G m1) (:G m0))
     :reward (:reward m1)
     :gated? (:gated? m1)}))
