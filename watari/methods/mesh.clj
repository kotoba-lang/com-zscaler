;; mesh.clj — watari 渡り KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:watari (live ship + aircraft position KG).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes craft→lane transit
;; edges as Datom assertions and derives lane/chokepoint concentration via Datalog,
;; routed to SAFETY. The full AIS/ADS-B as-of trajectory stays in the actor's methods.
;;
;; Posture: a resilience/safety map, never a target-list; NO person-tracking;
;; shares chokepoint keys with watatsuna/tatara.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns watari)

(defn observe []
  ;; observe — craft transiting public lanes/chokepoints (no person-tracking).
  (kqe-assert! "watari" "bulk-carrier" "transits" "malacca")
  (kqe-assert! "watari" "container-ship" "transits" "suez-red-sea")
  (kqe-assert! "watari" "wide-body" "transits" "north-atlantic-track")
  ;; derive — lane/chokepoint concentration → safety priority (Datalog).
  (kqe-query "safety(?l) :- transits(?l)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
