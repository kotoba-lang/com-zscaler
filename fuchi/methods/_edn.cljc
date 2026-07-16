(ns fuchi.methods._edn
  "_edn.cljc — 扶持 (fuchi) minimal EDN reader. Faithful counterpart of `methods/_edn.py`
  (the snake_case Python module the test suites import as `from _edn import load_edn`).

  The actor ALREADY ships a 1:1 port of the same reader as `fuchi.methods.edn`
  (`methods/edn.cljc`); per house style this module does NOT re-inline the parser — it
  simply re-exports the sibling so the `_edn` namespace exists (mirroring the Python
  filename `_edn.py`) and `load-edn` / `parse-edn` resolve identically.

  Keeps keywords as their \":ns/name\" STRINGS, byte-for-byte the Python `load_edn` shape."
  (:require [fuchi.methods.edn :as edn]))

(def parse-edn edn/parse-edn)

#?(:clj (def load-edn edn/load-edn))
