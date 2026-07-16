;; mesh.clj — meyasu 目安 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:meyasu (統合 arbitrage yardstick). Observatory
;; on-kse pattern (ADR-2606230001 §4): observes market→product spread edges as Datom
;; assertions and derives arbitrage-intel concentration via Datalog, routed to
;; planners (okaimono/danjo). The full kakaku/mitooshi fusion stays in methods.
;;
;; Posture: a yardstick, NOT a trade; aggregate-first, speculation structurally
;; unrepresentable (a band, never a point; `:trade` is not a member).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns meyasu)

(defn observe []
  ;; observe — disclosed price spreads per product across venues (aggregate).
  (kqe-assert! "meyasu" "venue-a" "spreads" "commodity-x")
  (kqe-assert! "meyasu" "venue-b" "spreads" "commodity-x")
  (kqe-assert! "meyasu" "venue-c" "spreads" "commodity-y")
  ;; derive — spread concentration → arbitrage-intel for planners (Datalog).
  (kqe-query "arbitrage-intel(?p) :- spreads(?p)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
