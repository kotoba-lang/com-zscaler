(ns tasuke.methods.-edn
  "_edn.cljc — minimal EDN reader (subset: [] {} :kw \"str\" num bool nil). ADR-2606060900.
  Clojure port of `methods/_edn.py`.

  The actor's established EDN helper already lives in the sibling `tasuke.methods.edn`
  (a byte-identical port of this same `_edn.py`; only the docstring differs — the .py
  pair `_edn.py` ⇄ the shared family parser). To avoid two divergent copies of the
  reader, this namespace re-exports `parse-edn` / `load-edn` from `tasuke.methods.edn`
  under the names the Python `_edn` module exposes (`load_edn`, `parse`). Keywords are
  kept as their \":ns/name\" STRINGS, exactly as the Python `load_edn` yields.

  Stdlib only; file I/O at the #?(:clj) edge (delegated to tasuke.methods.edn)."
  (:require [tasuke.methods.edn :as edn]))

;; ── re-exports (the Python _edn public surface) ───────────────────────────────
(def parse-edn
  "Parse a full EDN string (keywords as \":…\" strings) — see tasuke.methods.edn/parse-edn."
  edn/parse-edn)

#?(:clj
   (def load-edn
     "Read + parse an EDN file at `path` — see tasuke.methods.edn/load-edn."
     edn/load-edn))
