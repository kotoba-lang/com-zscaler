(ns nusa.cells.heritage-ingest.cell
  "LangGraph Pregel wrapper for the nusa 幣 heritage_ingest cell — R0 scaffold.
  1:1 port of cells/heritage_ingest/cell.py (ADR-2606039800). .solve() raises at R0."
  (:require [clojure.string]))

(defn solve
  [_input-state]
  (throw (ex-info "nusa R0 scaffold: activate heritage_ingest via Council ADR (post-2606039800 ratification)"
                  {:scaffold true})))
