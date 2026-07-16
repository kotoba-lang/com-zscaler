(ns magatama.cells.shionome-social-post.cell
  "shionome_social_post — DRY-RUN capital-flow social post (shionome).
  Resident in Kotoba WASM. Per ADR-2606072200. Mirror (G5), no-trade body scan (G2),
  member-signed (G7), dry-run only (G8). Live posting Council Lv6+ + operator +
  member-signature gated.

  1:1 port of shionome_social_post/cell.py — the compiled graph wraps a single `draft`
  super-step; `solve` runs it (START → draft → END) over the input state."
  (:require [magatama.cells.shionome-social-post.state-machine :as sm]))

(defn solve
  "Run the compiled graph: draft a DRY-RUN post (refuses trade-token body G2 / <2 sources G3)."
  [input-state]
  (sm/run-chain input-state))
