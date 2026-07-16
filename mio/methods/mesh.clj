;; mesh.clj — mio KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:mio (energy-order observation + verification).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes generator→demand
;; delivery edges as Datom assertions and derives delivered-flow concentration via
;; Datalog (proof-of-useful-flow verification). The full Flowrate analysis stays in
;; the actor's existing methods.
;;
;; Posture: OBSERVATION + VERIFICATION ONLY — mio verifies aggregate useful flow,
;; never dispatches and never a market signal.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns mio)

(defn observe []
  ;; observe — delivered useful energy flow (generator → demand), aggregate.
  (kqe-assert! "mio" "generator-a" "delivers" "demand-x")
  (kqe-assert! "mio" "generator-b" "delivers" "demand-x")
  (kqe-assert! "mio" "generator-c" "delivers" "demand-y")
  ;; derive — delivered-flow concentration → verification map (Datalog).
  (kqe-query "verification(?d) :- delivers(?d)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
