;; mesh.clj — tanemaki 種蒔き KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:tanemaki (Public Fund grant-steward). Observatory
;; on-kse pattern (ADR-2606230001 §4): observes grantee→screen pass edges as Datom
;; assertions and derives a public DD scorecard via Datalog. The full rubric/weight
;; synthesis stays in the actor's existing methods.
;;
;; Posture: G1 steward NOT sovereign — the scorecard is advisory; every grant is
;; decided by 1 SBT = 1 vote (no `:fund` route here). G2 the fund GIVES, never
;; INVESTS (grant/escrow/in-kind only; equity/debt unrepresentable). Seed fictional.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns tanemaki)

(defn observe []
  ;; observe — candidate grantees passing public DD screens (advisory, fictional).
  (kqe-assert! "tanemaki" "grantee-a" "passes" "openness-screen")
  (kqe-assert! "tanemaki" "grantee-a" "passes" "stewardship-screen")
  (kqe-assert! "tanemaki" "grantee-b" "passes" "openness-screen")
  ;; derive — screen-pass concentration → DD scorecard (advisory) (Datalog).
  (kqe-query "dd-scorecard(?s) :- passes(?s)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
