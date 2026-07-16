#!/usr/bin/env bb
;; Axis 3 — Homeostasis (恒常性 / 和).
;; Port of src/etzhayyim_organism/sensors/homeostasis.py
(ns etzhayyim-organism.sensors.homeostasis
  "Axis 3 — Homeostasis: substrate boundary holds (no RisingWave/Postgres/Stripe
   in app code; Apache 2.0 + Charter Rider on first-party packages; lefthook
   lints prohibitions).
   Observable: CHARTER-RIDER.md, lefthook.yml hooks, NOTICE files, ADR registry,
   deps.toml."
  (:require [etzhayyim-organism.sensors.common :as c]))

(def prohibited-imports
  "Strings whose presence in source would violate the substrate boundary.
   Mirrors PROHIBITED_IMPORTS in the Python file."
  ["from @atproto/api"
   "import { Stripe"
   "kysely"
   "@noble/ciphers"
   "@signalapp/libsignal-client"])

(defn read
  "Compute the homeostasis AxisReading for `repo` (string path)."
  [repo]
  (let [score    (volatile! 0)
        ev       (volatile! [])
        add!     (fn [s e] (vswap! score + s) (vswap! ev conj e))]

    (when (c/has? repo "CHARTER-RIDER.md")
      (add! 2 "CHARTER-RIDER.md present at root"))

    ;; lefthook.yml — count hooks by counting "  - " indented list items
    (let [lefthook (c/read-text repo "lefthook.yml")
          n-hooks  (count (re-seq #"\n  - " lefthook))]
      (cond
        (>= n-hooks 4) (add! 2 (str "lefthook.yml has ≥4 lint hooks (" n-hooks " observed)"))
        (and (pos? (count lefthook))) (add! 1 (str "lefthook.yml present (" n-hooks " hooks)"))))

    ;; NOTICE files
    (let [notice-files (c/count-glob repo "**/NOTICE")]
      (cond
        (>= notice-files 20) (add! 2 (str "NOTICE files propagated (" notice-files " found)"))
        (>= notice-files 1)  (add! 1 (str "NOTICE files present (" notice-files " found)"))))

    ;; ADR registry
    (let [adr-count (c/count-glob repo "90-docs/adr/*.md")]
      (cond
        (>= adr-count 30) (add! 2 (str "ADR registry healthy (" adr-count " ADRs)"))
        (>= adr-count 10) (add! 1 (str "ADR registry growing (" adr-count " ADRs)"))))

    (when (c/has? repo "deps.toml")
      (add! 2 "deps.toml SSoT present"))

    (let [final-score (min @score 10)
          next-action (if (>= final-score 9)
                        "Council attestation gate on religious-corp identity PRs"
                        "Restore lefthook + NOTICE + CHARTER-RIDER scaffolding")]
      (c/->AxisReading "homeostasis" final-score @ev next-action 2))))
