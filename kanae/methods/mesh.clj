;; mesh.clj — kanae 鼎 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kanae (government fiscal-flow visualization).
;; Observatory on-kse pattern (ADR-2606230001 §4): fired on the actor's KSE topic;
;; observes fund-flow edges as Datom assertions and derives flow concentration via
;; Datalog. The full kami-engine fiscal viz stays in the actor's existing methods.
;;
;; Posture: aggregate-first fiscal-flow MAP, non-adjudicating (danjo finds, kanae
;; renders); never a verdict.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kanae)

(defn observe []
  ;; observe — public fund flows (source → recipient), aggregate-first.
  (kqe-assert! "kanae" "treasury" "funds" "ministry")
  (kqe-assert! "kanae" "ministry" "funds" "program")
  (kqe-assert! "kanae" "program" "funds" "contractor")
  (kqe-query "fiscal-flow(?r) :- funds(?r)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
