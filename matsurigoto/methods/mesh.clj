;; mesh.clj — matsurigoto 政 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:matsurigoto (governance registry / Transparent
;; force). Observatory on-kse pattern (ADR-2606230001 §4): observes office→mandate
;; holding edges as Datom assertions and derives a governance map via Datalog. The
;; full registry stays in the actor's existing methods.
;;
;; Posture: a TRANSPARENT governance registry (open + on-chain + 1 SBT = 1 vote);
;; observational, non-adjudicating.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns matsurigoto)

(defn observe []
  ;; observe — offices holding governance mandates (disclosed, transparent).
  (kqe-assert! "matsurigoto" "council" "holds" "ratify-mandate")
  (kqe-assert! "matsurigoto" "treasury" "holds" "fund-mandate")
  (kqe-assert! "matsurigoto" "registry" "holds" "roster-mandate")
  ;; derive — mandate concentration → governance map (Datalog).
  (kqe-query "governance(?m) :- holds(?m)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
