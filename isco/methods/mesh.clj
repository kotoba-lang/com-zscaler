;; mesh.clj — isco KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:isco (ILO ISCO-08 occupation classification mirror).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes occupation→group
;; classification edges as Datom assertions and derives the classification taxonomy
;; via Datalog. The full 619-code multi-DID tree stays in the actor's methods.
;;
;; Posture: a reference classification MIRROR (ILO ISCO-08, public standard);
;; non-adjudicating taxonomy.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns isco)

(defn observe []
  ;; observe — occupations classified into ISCO groups (public standard).
  (kqe-assert! "isco" "software-developer" "classified" "group-251")
  (kqe-assert! "isco" "data-scientist" "classified" "group-251")
  (kqe-assert! "isco" "nurse" "classified" "group-222")
  ;; derive — group membership → classification taxonomy (Datalog).
  (kqe-query "taxonomy(?g) :- classified(?g)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
