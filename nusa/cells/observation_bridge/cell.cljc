(ns nusa.cells.observation-bridge.cell
  "LangGraph Pregel wrapper for the nusa 幣 observation_bridge cell — R0 scaffold.
  1:1 port of cells/observation_bridge/cell.py (ADR-2606039800).
  G3: routes legal/legislative questions to danjo/chigiri/moushibumi/yakushi; never adjudicates."
  (:require [clojure.string]))

;; G3: where each off-actor concern is routed. nusa holds none of these itself.
(def routes
  {"legislative-trace" "danjo (ADR-2605301600) — non-adjudicating fact trace"
   "legal-characterization" "chigiri (ADR-2605262700) — UPL boundary, licensed counsel"
   "public-comment" "moushibumi (ADR-2605312400) — neutral 意見公募 / 請願 support"
   "cannabis-derived-medicine" "yakushi (ADR-2605250500) — 薬機法/医師法 boundary (G10)"})

(defn route [concern]
  (if-let [r (get routes concern)]
    r
    (throw (ex-info (str "unknown concern " (pr-str concern) "; nusa routes only "
                         (pr-str (vec (sort (keys routes))))) {:gate "G3"}))))

(defn solve
  [_input-state]
  (throw (ex-info "nusa R0 scaffold: activate observation_bridge via Council ADR (post-2606039800 ratification)"
                  {:scaffold true})))
