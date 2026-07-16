;; mesh.clj — danjo 弾正 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:danjo (public-accountability oversight). Observatory
;; on-kse pattern (ADR-2606230001 §4): observes budget→agency allocation edges as
;; Datom assertions and derives oversight concentration via Datalog. The full
;; 国会会議録/予算書/調達 discrepancy analysis stays in the actor's existing methods.
;;
;; Posture: non-adjudicating (censor's eye, no sword) — a discrepancy OBSERVATION,
;; never a verdict (UPL boundary).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns danjo)

(defn observe []
  ;; observe — disclosed public budget allocations (record → agency).
  (kqe-assert! "danjo" "budget-line" "allocated" "agency")
  (kqe-assert! "danjo" "procurement" "allocated" "contractor")
  (kqe-assert! "danjo" "subsidy" "allocated" "recipient")
  ;; derive — allocation concentration → oversight map (Datalog).
  (kqe-query "oversight(?a) :- allocated(?a)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
