;; mesh.clj — hanrei 判例 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:hanrei (case-law / judicial-precedent mirror).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes case→principle edges as
;; Datom assertions and derives a precedent map via Datalog. The full case-law
;; corpus ingest stays in the actor's existing methods.
;;
;; Posture: a DISCLOSED precedent MAP, non-adjudicating — never legal advice, never
;; a ruling (UPL boundary; precedents are public facts).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns hanrei)

(defn observe []
  ;; observe — published cases establishing legal principles (public record).
  (kqe-assert! "hanrei" "case-a" "establishes" "principle-x")
  (kqe-assert! "hanrei" "case-b" "establishes" "principle-x")
  (kqe-assert! "hanrei" "case-c" "establishes" "principle-y")
  ;; derive — principle concentration → precedent map (Datalog).
  (kqe-query "precedent(?p) :- establishes(?p)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
