;; mesh.clj — kaname 要 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kaname (cross-domain leverage-point synthesizer).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes entity→domain spanning
;; edges as Datom assertions and derives the leverage map via Datalog, routed to
;; OPENING. The full multiplex betweenness synthesis stays in the actor's methods.
;;
;; Posture: G1 leverage-MAP-never-target-list; G2 OPENING-only routing
;; (capture/seize/control/monopolize unrepresentable).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kaname)

(defn observe []
  ;; observe — cross-domain spanning entities (structural positions only).
  (kqe-assert! "kaname" "compute-chokepoint" "spans" "ai")
  (kqe-assert! "kaname" "compute-chokepoint" "spans" "economy")
  (kqe-assert! "kaname" "standards-body" "spans" "policy")
  ;; derive — leverage (要 / 律速) → OPENING priority (Datalog).
  (kqe-query "opening(?d) :- spans(?d)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
