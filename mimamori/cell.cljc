(ns mimamori.cell
  "mimamori cell entry — kotodama-cell-runner contract (ADR-2605192415 §7.1).

  Registered in 50-infra/cluster/murakumo/cell-runner/cells.edn as
  MimamoriHeartbeatCell (node benjamin, cron 23 * * * *, healthz 13080).
  `fire` runs ONE deterministic heartbeat (ADR-2606112300 / pattern 2606091000):

      replay synthetic seed → §D4 offer-matching → keeper-side social-capital
      mint (moyai reuse) → aggregate coverage → ONE content-addressed tx
      appended to the actor-local kotoba commit-DAG → chain verified.

  NO external I/O — the live legs (real roster / §1.16 outreach / musubi
  ceremony / live social-capital mint) remain G7-gated. The returned summary is
  aggregate-only (G5): counts and CIDs, never a DID."
  (:require [mimamori.methods.autorun :as autorun]
            [mimamori.methods.bond :as bond]
            #?(:clj [clojure.java.io :as io])))

#?(:clj
   (defn- actor-dir
     "20-actors/mimamori, resolved from this namespace's classpath location so the
     cell runs from any cwd (the cell-runner's contract)."
     []
     (-> (io/resource "mimamori/cell.cljc") io/file .getParentFile)))

#?(:clj
   (def log-default
     (delay (io/file (actor-dir) "data" "mimamori.datoms.kotoba.edn"))))

#?(:clj
   (defn fire
     "One heartbeat. Idempotent per log state (cycle derives from log length)."
     ([] (fire nil))
     ([log-path]
      (let [seed (bond/load-seed-file (io/file (actor-dir) "data" "seed-mimamori-bonds.json"))
            target (or log-path @log-default)
            summary (autorun/run-cycle seed target)]
        (println (str "MimamoriHeartbeatCell cycle " (:cycle summary) ": "
                      "unkept " (:unkept-before summary)
                      "→" (get-in summary [:coverage :unkept-count])
                      " via " (:offers-emitted summary) " offers, "
                      (get-in summary [:shakai :minted-units]) " social-capital minted, "
                      "chain " (:chain-length summary) " ok → "
                      (subs (:cid summary) 0 16) "…"))
        summary))))
