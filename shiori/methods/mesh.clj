;; mesh.clj — shiori 栞 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:shiori (human-Wellbecoming detractor observatory +
;; transparent intervention). Observatory on-kse pattern (ADR-2606230001 §4):
;; observes detractor→cohort edges as Datom assertions and derives detraction
;; concentration via Datalog, routed to RELIEF (救い). The full relief-gap synthesis
;; stays in the actor's existing methods.
;;
;; Posture: G1 relief MAP, NEVER per-person affect/manipulation — cohort-aggregate
;; only; drivers are PATTERNS not entities; anti-addictive; shiori PROPOSES, ossekai
;; carries (transparent intervention).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns shiori)

(defn observe []
  ;; observe — structural detractors bearing on cohorts (aggregate; no person).
  (kqe-assert! "shiori" "precarity" "detracts" "gig-cohort")
  (kqe-assert! "shiori" "isolation" "detracts" "elder-cohort")
  (kqe-assert! "shiori" "addictive-design" "detracts" "youth-cohort")
  ;; derive — detraction concentration → relief priority (Datalog).
  (kqe-query "relief(?c) :- detracts(?c)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
