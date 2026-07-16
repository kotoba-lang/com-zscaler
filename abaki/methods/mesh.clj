;; mesh.clj — abaki 暴 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:abaki (anti-monopoly & chokepoint intelligence).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes entity→domain control
;; edges as Datom assertions and derives chokepoint concentration via Datalog,
;; routed to ROUTE-AROUND. The full beneficial-ownership graph stays in methods.
;;
;; Posture: route-around, NOT punishment; a dependency map for bypass, never a
;; target-list.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns abaki)

(defn observe []
  ;; observe — entity control over a domain/chokepoint (disclosed, aggregate).
  (kqe-assert! "abaki" "incumbent" "controls" "compute-supply")
  (kqe-assert! "abaki" "incumbent" "controls" "data-pipeline")
  (kqe-assert! "abaki" "registry" "controls" "namespace")
  ;; derive — control concentration → route-around priority (Datalog).
  (kqe-query "route-around(?d) :- controls(?d)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
