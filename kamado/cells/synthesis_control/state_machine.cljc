(ns kamado.cells.synthesis-control.state-machine
  "cljc port of cells/synthesis_control/cell.py (ADR-2606051500).
  R0 scaffold — closed-loop refining process-control (distillation/cracking/
  reforming/hydrotreating on G1 feedstock). Actuation is member/operator-signed
  Transparent Force (§1.12.B, G5). NOT a certified functional-safety system
  (G11; IEC 61508/61511 SIL = R5/Lv7+). .solve() raises until Council activation.")

(defn solve [_state]
  (throw (ex-info "kamado R0 scaffold: synthesis_control needs Council Lv6+ + operator + a certified-safety review (G8/G11); no live process actuation at R0 (ADR-2606051500)"
                  {:cell :synthesis-control :actor :kamado :status :r0-scaffold})))
