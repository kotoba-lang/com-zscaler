(ns manabi.cells.civics-charter.cell
  "CivicsCharterCell — manabi R0 scaffold per ADR-2605261045.

  R0 scaffold. etzhayyim Charter + governance + multi-gen ethics + comparative
  religion + history. G14 invariant: NO \"etzhayyim is exclusive truth\";
  comparative-religion approach mandated including for etzhayyim's own charter.
  G12 invariant: NO political indoctrination; no electoral mobilization.

  Output: charterUnderstandingAttestation — Adherent SBT informed-consent
  baseline per ADR-2605261000 §2.

  Faithful 1:1 cljc port of cells/civics_charter/cell.py.")

(defn solve
  "Charter + governance + comparative religion + history.
  R0 scaffold — raises until ADR-2605261045 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi R0 scaffold: civics_charter cell not activated. "
                "Requires ADR-2605261045 Council ratify + civics_charter curriculum "
                "G4 + G14 attestation (multi-gen + anti-individualism + Wellbecoming "
                "+ non-eschatology + comparative-religion).")
           {:cell :civics-charter
            :status :r0-scaffold})))
