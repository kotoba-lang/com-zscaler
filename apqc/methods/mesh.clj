;; mesh.clj — apqc KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:apqc (APQC Process Classification Framework mirror).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes process→category
;; classification edges as Datom assertions and derives the process taxonomy via
;; Datalog. The full PCF tree stays in the actor's existing methods.
;;
;; Posture: a reference process-classification MIRROR (APQC PCF, public framework);
;; non-adjudicating taxonomy.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns apqc)

(defn observe []
  ;; observe — business processes classified into PCF categories (public framework).
  (kqe-assert! "apqc" "procure-to-pay" "classified" "category-supply")
  (kqe-assert! "apqc" "order-to-cash" "classified" "category-sales")
  (kqe-assert! "apqc" "hire-to-retire" "classified" "category-hr")
  ;; derive — category membership → process taxonomy (Datalog).
  (kqe-query "taxonomy(?c) :- classified(?c)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
