;; mesh.clj — tsugite 継ぎ手 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:tsugite (world peoples-continuity mirror). Observatory
;; on-kse pattern (ADR-2606230001 §4): observes pressure→collective edges as Datom
;; assertions and derives pressure concentration via Datalog, routed to CONTINUITY
;; (継承). The full migration/language-community analysis stays in the actor's methods.
;;
;; Posture: G1 continuity MAP, NEVER person-tracking — aggregate/collective scale
;; only, no individual/location/biometric, never a border-enforcement aid.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns tsugite)

(defn observe []
  ;; observe — pressures borne by human COLLECTIVES (aggregate; no person data).
  (kqe-assert! "tsugite" "displacement" "pressures" "refugee-population")
  (kqe-assert! "tsugite" "assimilation" "pressures" "language-community")
  (kqe-assert! "tsugite" "statelessness" "pressures" "indigenous-people")
  ;; derive — pressure concentration → continuity priority (Datalog).
  (kqe-query "continuity(?c) :- pressures(?c)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
