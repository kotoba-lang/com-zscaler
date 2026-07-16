#!/usr/bin/env bash
# himawari — clj/bb test suite (ADR-2606021200 py→clj port wave); wired into the fleet
# green-check. Runs all cljc test namespaces via babashka from the repo root.
# All 7 cells now ported:
# - polysilicon_refine  G2/N6 XUAR-exclusion + chain-of-custody
# - ingot_wafer         ingot growth + wafer slicing (G4 renewable + G5 kerf recovery)
# - cell_process        cell process line + flash IV (G3 high-GWP abatement + G6 Ag→Cu)
# - module_assembly     module assembly + flash IV + EL imaging (G11 provenance + G12)
# - panel_loading       積込 robot cycle (G7 labor-liberation + G12 internal-only)
# - outbound_logistics  輸送 handoff (G13 hikari-only + kami-autodrive GNC)
# - supply_procurement  調達 (G2 XUAR/G8 SBOM + okaimono commons-first)
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(def nss (quote [himawari.cells.polysilicon-refine.test-state-machine
                             himawari.cells.ingot-wafer.test-state-machine
                             himawari.cells.cell-process.test-state-machine
                             himawari.cells.module-assembly.test-state-machine
                             himawari.cells.panel-loading.test-state-machine
                             himawari.cells.outbound-logistics.test-state-machine
                             himawari.cells.supply-procurement.test-state-machine]))
              (apply require (quote clojure.test) nss)
              (let [r (apply clojure.test/run-tests nss)]
                (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
