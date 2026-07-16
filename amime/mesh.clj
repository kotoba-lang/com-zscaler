;; mesh.clj — amime 網目 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:amime (multi-site energy FLOW NETWORK).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes site→site flow edges as
;; Datom assertions and derives N-1 chokepoint concentration via Datalog, routed to
;; RESILIENCE. The full transportation-flow / contingency sim stays in methods.
;;
;; Posture: a COMMONS mesh, NEVER a market (no price, no trade representable); a
;; resilience map, never a target-list; SIM ONLY — amime never dispatches.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns amime)

(defn observe []
  ;; observe — ordered energy flow between sites over capacity-bounded links.
  (kqe-assert! "amime" "wind-farm" "flows-to" "city-load")
  (kqe-assert! "amime" "solar-array" "flows-to" "city-load")
  (kqe-assert! "amime" "hydro" "flows-to" "industrial-load")
  ;; derive — flow concentration / chokepoint → resilience priority (Datalog).
  (kqe-query "resilience(?l) :- flows-to(?l)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
