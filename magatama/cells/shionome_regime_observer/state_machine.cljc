(ns magatama.cells.shionome-regime-observer.state-machine
  "shionome_regime_observer — FACTUAL cross-asset regime risk-on/off/mixed (shionome).
  Per ADR-2606072200. Descriptive, never advice (G2, トレードはしない).
  Port of shionome_regime_observer/cell.py."
  (:require [magatama.cells.shionome-core :as core]))

;; ── State ────────────────────────────────────────────────────────────────────────
;; {:context map? :net coll? :regime map?}

(defn observe
  "Compute regime from net rows + risk_tags. Mirrors Python _observe node."
  [state]
  (let [ctx       (or (:context state) {})
        net       (or (:net state) (:net ctx) (get ctx "net") [])
        risk-tags (or (:risk_tags ctx) (get ctx "risk_tags") {})]
    (assoc state :regime (core/regime net risk-tags))))

(defn run-chain
  "Thread state through: START → observe → END."
  [state]
  (observe state))
