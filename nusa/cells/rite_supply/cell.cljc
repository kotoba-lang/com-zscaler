(ns nusa.cells.rite-supply.cell
  "LangGraph Pregel wrapper for the nusa 幣 rite_supply cell — R0 scaffold.
  1:1 port of cells/rite_supply/cell.py (ADR-2606039800). .solve() raises at R0."
  (:require [clojure.string]))

(defn solve
  [_input-state]
  (throw (ex-info "nusa R0 scaffold: activate rite_supply via Council ADR (post-2606039800 ratification)"
                  {:scaffold true})))
