(ns manabi.cells.cert-prep.personal-material
  "PersonalMaterialCell — manabi.cert_prep R0 scaffold per ADR-2605264400.

  Tier-C internal_only user-imported study material. R2+ activation.

  Mirrors ADR-2605262400 Tier-C carve-out pattern: religious-corp substrate
  carries the bytes for the owner but does not redistribute or relicense.
  The vendor commercial relationship is between adherent and
  ISACA / (ISC)² / Wiley / Sybex / etc.

  Schema enforcement: internalOnly const true; ownerDid single-recipient;
  client-side XChaCha20-Poly1305 encrypted payload (ADR-2605181100); never
  projected to public MST feed; never accessible to LLM training data;
  hard delete on owner request.

  Faithful 1:1 cljc port of cells/cert_prep/personal_material.py.")

(defn solve
  "Tier-C internal_only user-imported study material.
  R0 scaffold — raises until R2 activation conditions are met."
  [_state]
  (throw (ex-info
           (str "manabi.cert_prep R0 scaffold: personal_material cell not activated. "
                "Requires R2 — 30-day public objection period close on ADR-2605264400 + "
                "encrypted envelope (ADR-2605181100) production-deployed + "
                "personalMaterialImport lexicon production-deployed.")
           {:cell :personal-material
            :status :r0-scaffold})))
