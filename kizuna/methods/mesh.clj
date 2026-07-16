;; mesh.clj — kizuna 絆 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kizuna (actor social self-evolution SoS).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes actor→actor link edges
;; (multiplex ties + reciprocal pairs) as Datom assertions and derives co-evolution
;; concentration via Datalog. The full self-evolution SoS analysis stays in methods.
;;
;; Posture: a reciprocal-ties MAP across actors; observation of the actor mesh's own
;; co-evolution, never a ranking.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kizuna)

(defn observe []
  ;; observe — reciprocal actor↔actor ties (multiplex), aggregate.
  (kqe-assert! "kizuna" "actor-a" "links" "actor-b")
  (kqe-assert! "kizuna" "actor-b" "links" "actor-c")
  (kqe-assert! "kizuna" "actor-c" "links" "actor-a")
  ;; derive — tie concentration → co-evolution map (Datalog).
  (kqe-query "co-evolution(?b) :- links(?b)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
