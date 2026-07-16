;; mesh.clj — hokorobi 綻び KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:hokorobi (systemic finance-risk observatory).
;; Compiled by kotoba-clj into a kotoba:kais WASM component, placed by the KOTOBA
;; Mesh lattice. Kotoba-native slice: observe risk-source→bearer edges as Datom
;; assertions, derive systemic-risk concentration via Datalog, routed to RESILIENCE.
;; The full analyze logic stays in the actor's .cljc methods.
;;
;; Posture: G1 = resilience map, NEVER a panic/trading/market signal, never-trades.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns hokorobi)

(defn run [ctx]
  ;; observe — risk sources stressing institutions/bearers (disclosed, aggregate).
  (kqe-assert! "hokorobi" "leverage" "stresses" "pension-fund")
  (kqe-assert! "hokorobi" "contagion" "stresses" "banking-system")
  (kqe-assert! "hokorobi" "climate" "stresses" "insurer")
  ;; derive — systemic-risk concentration → resilience priority (Datalog).
  (kqe-query "resilience(?b) :- stresses(?b)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "resilience(?b) :- stresses(?b)."))
