;; mesh.clj — hakoniwa 箱庭 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:hakoniwa (forward-simulation observatory).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes persona→scenario
;; simulation edges (SYNTHETIC personas) as Datom assertions and derives a scenario
;; distribution via Datalog, routed to RESILIENCE/preparedness. The full
;; Friedkin-Johnsen ensemble stays in the actor's existing methods.
;;
;; Posture: G1 SYNTHETIC personas only (real persons unrepresentable); G2
;; distribution-only (非終末論, no point forecast); G3 non-steering (resilience use).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns hakoniwa)

(defn observe []
  ;; observe — SYNTHETIC personas exploring scenarios (no PII, fictional agents).
  (kqe-assert! "hakoniwa" "synthetic-persona-1" "simulates" "scenario-a")
  (kqe-assert! "hakoniwa" "synthetic-persona-2" "simulates" "scenario-a")
  (kqe-assert! "hakoniwa" "synthetic-persona-3" "simulates" "scenario-b")
  ;; derive — scenario distribution → resilience/preparedness map (Datalog).
  (kqe-query "distribution(?s) :- simulates(?s)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
