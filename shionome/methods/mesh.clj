;; mesh.clj — shionome 潮目 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:shionome (cross-asset capital-flow observatory).
;; Compiled by kotoba-clj into a kotoba:kais WASM component, placed by the KOTOBA
;; Mesh lattice. Kotoba-native slice: observe bucket→bucket flow edges as Datom
;; assertions, derive net rotation via Datalog. The full flow-graph / regime
;; analysis stays in the actor's existing methods.
;;
;; Posture: トレードはしない — no buy/sell/signal/target/position (structurally
;; unrepresentable); observational mirror, edge-primary, no-doxxing.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns shionome)

(defn run [ctx]
  ;; observe — capital flow between asset buckets (rotation, never a signal).
  (kqe-assert! "shionome" "equities" "flows-to" "bonds")
  (kqe-assert! "shionome" "fx" "flows-to" "commodities")
  (kqe-assert! "shionome" "crypto" "flows-to" "real-estate")
  ;; derive — net rotation (どこからどこへ) as observation (Datalog).
  (kqe-query "rotation(?to) :- flows-to(?to)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "rotation(?to) :- flows-to(?to)."))
