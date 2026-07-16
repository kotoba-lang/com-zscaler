;; mesh.clj — kenkyusha 研究者 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kenkyusha (research-frontiers KG). Observatory
;; on-kse pattern (ADR-2606230001 §4): observes field→frontier advance edges as
;; Datom assertions and derives frontier concentration via Datalog → an open
;; research-frontier map. The full shinka cycle stays in the actor's methods.
;;
;; Posture: a disclosed-frontier MAP, non-adjudicating (open knowledge, never a
;; ranking of researchers).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kenkyusha)

(defn observe []
  ;; observe — disclosed research advances per field (open-knowledge, aggregate).
  (kqe-assert! "kenkyusha" "ml" "advances" "frontier-a")
  (kqe-assert! "kenkyusha" "biology" "advances" "frontier-b")
  (kqe-assert! "kenkyusha" "materials" "advances" "frontier-c")
  ;; derive — frontier concentration → research-frontier map (Datalog).
  (kqe-query "frontier(?f) :- advances(?f)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
