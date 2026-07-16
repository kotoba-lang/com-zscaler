(ns manabi.cells.cert-prep.practice-question
  "PracticeQuestionCell — manabi.cert_prep R0 scaffold per ADR-2605264400.

  Synthetic practice question generation via baien-server-moemoekyun-*
  (Murakumo fleet, judah LiteLLM gateway per ADR-2605215000).

  G16 STRUCTURAL: questionSource closed enum at PDS write time rejects
  official-isaca-reproduced / official-isc2-reproduced / commercial-test-bank-reproduced.

  Only synthetic-baien-generated and user-imported-personal-only are valid.

  Generated questions carry deterministic sha256 questionSeed so regeneration
  for review does not require persisting question text bodies (copyright +
  privacy minimization).

  Faithful 1:1 cljc port of cells/cert_prep/practice_question.py.")

(defn solve
  "Synthetic practice question generation.
  R0 scaffold — raises until ADR-2605264400 Council ratify."
  [_state]
  (throw (ex-info
           (str "manabi.cert_prep R0 scaffold: practice_question cell not activated. "
                "Requires ADR-2605264400 Council ratify + judah LiteLLM gateway "
                "operational with baien-server-moemoekyun-* fleet entry + "
                "system-prompt-level G15/G16/N11 enforcement verified.")
           {:cell :practice-question
            :status :r0-scaffold})))
