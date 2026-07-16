;; mesh.clj — tokigusuri 時薬 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:tokigusuri (pharma patent-cliff / off-patent-access
;; observatory). Observatory on-kse pattern (ADR-2606230001 §4): observes
;; barrier→drug blocking edges as Datom assertions and derives access-barrier
;; concentration via Datalog, routed to RELEASE (解放). The full exclusivity-barrier
;; analysis stays in the actor's existing methods.
;;
;; Posture: G1 release MAP, NEVER a patent-busting / FTO / infringement / per-company
;; verdict; G2 a medicine is the gated object, never a 取-holder; lawful routes only.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns tokigusuri)

(defn observe []
  ;; observe — exclusivity barriers blocking access to a drug (disclosed).
  (kqe-assert! "tokigusuri" "secondary-patent" "blocks" "drug-x")
  (kqe-assert! "tokigusuri" "data-exclusivity" "blocks" "drug-x")
  (kqe-assert! "tokigusuri" "pay-for-delay" "blocks" "drug-y")
  ;; derive — access-barrier concentration → release priority (Datalog).
  (kqe-query "release(?d) :- blocks(?d)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
