;; mesh.clj — junkan 循 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:junkan (ANALYSIS-ONLY system-dynamics observer).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes stock→flow feedback
;; edges as Datom assertions and derives Meadows leverage candidates via Datalog.
;; The full CLD / 好循環-悪循環 reading stays in the actor's existing methods.
;;
;; Posture: ANALYSIS-ONLY — junkan reads feedback loops and names leverage points;
;; it never touches/actuates the system (kaname synthesizes, ossekai acts).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns junkan)

(defn observe []
  ;; observe — causal feedback edges (stock → flow) from passive observation.
  (kqe-assert! "junkan" "trust" "feeds" "participation")
  (kqe-assert! "junkan" "participation" "feeds" "commons-output")
  (kqe-assert! "junkan" "commons-output" "feeds" "trust")
  ;; derive — leverage candidates over the loop (Datalog).
  (kqe-query "leverage(?f) :- feeds(?f)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
