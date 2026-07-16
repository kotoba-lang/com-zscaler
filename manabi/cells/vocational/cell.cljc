(ns manabi.cells.vocational.cell
  "VocationalCell — manabi R0 scaffold per ADR-2605261045.

  R0 scaffold. Practical skill curriculum aligned to L5+ adherent productive
  contribution. Skill domains: mitsuho farming, tatekata building, hikari
  maintenance, yakushi QC, silicon assembly, hagukumi caregiving.

  Output: skillAttestation — L5 productive-contribution gating input (G7
  anti-credentialism — no degrees, only skill demonstrations).

  Faithful 1:1 cljc port of cells/vocational/cell.py.")

(defn solve
  "Practical skill curriculum.
  R0 scaffold — raises until ADR-2605261045 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi R0 scaffold: vocational cell not activated. "
                "Requires ADR-2605261045 Council ratify + cross-actor R2 "
                "vocational curriculum coordination (mitsuho + tatekata + hikari + "
                "yakushi peer review).")
           {:cell :vocational
            :status :r0-scaffold})))
