;; mesh.clj — keizu 系図 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:keizu (government power-relations KG). Compiled
;; by kotoba-clj into a kotoba:kais WASM component, placed by the KOTOBA Mesh
;; lattice. Kotoba-native slice: observe office→body relation edges as Datom
;; assertions, derive power-relation concentration via Datalog → accountability.
;; The full 調達/お金/発言/委員会 weave stays in the actor's existing methods.
;;
;; Posture: map-not-target, non-adjudicating, edge-primary, no-doxxing
;; (person-excluded — structural offices/bodies only).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns keizu)

(defn run [ctx]
  ;; observe — structural office→body ties (on-record; no person profiles).
  (kqe-assert! "keizu" "ministry-office" "ties" "advisory-council")
  (kqe-assert! "keizu" "agency" "ties" "procurement-committee")
  (kqe-assert! "keizu" "bureau" "ties" "budget-panel")
  ;; derive — power-relation concentration → accountability map (Datalog).
  (kqe-query "accountability(?b) :- ties(?b)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "accountability(?b) :- ties(?b)."))
