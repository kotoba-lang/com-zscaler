;; mesh.clj — tsuchifumi 土踏 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:tsuchifumi (earthing / ambient-EMF exposure
;; Wellbecoming observatory). Observatory on-kse pattern (ADR-2606230001 §4):
;; observes source→cohort exposure edges as Datom assertions and derives exposure
;; concentration via Datalog. The full edge-primary exposure analysis stays in methods.
;;
;; Posture: a Wellbecoming exposure MAP; cohort-aggregate, anti-pseudoscience
;; (disclosed exposure, never a medical claim).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns tsuchifumi)

(defn observe []
  ;; observe — ambient-EMF / earthing-deprivation exposure on cohorts (aggregate).
  (kqe-assert! "tsuchifumi" "indoor-insulation" "exposes" "urban-cohort")
  (kqe-assert! "tsuchifumi" "ambient-emf" "exposes" "urban-cohort")
  (kqe-assert! "tsuchifumi" "footwear" "exposes" "general-cohort")
  ;; derive — exposure concentration → exposure map (Datalog).
  (kqe-query "exposure(?c) :- exposes(?c)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
