;; mesh.clj — tatara 鑪 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:tatara (manufacturing-plant + logistics geographic
;; KG). Compiled by kotoba-clj into a kotoba:kais WASM component, placed by the
;; KOTOBA Mesh lattice. Kotoba-native slice: observe plant→chokepoint flow edges as
;; Datom assertions, derive geographic concentration via Datalog, routed to
;; REDUNDANCY / reshoring. The full HHI analysis stays in the actor's .cljc methods.
;;
;; Posture: resilience map, NEVER a target-list (G2); G4 aggregate SIZE only, no
;; worker/person attribute representable.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns tatara)

(defn run [ctx]
  ;; observe — manufacturing flow via geographic chokepoints (aggregate, G4).
  (kqe-assert! "tatara" "tsmc-fab" "routes-via" "taiwan-strait")
  (kqe-assert! "tatara" "asml-plant" "routes-via" "eu-corridor")
  (kqe-assert! "tatara" "samsung-fab" "routes-via" "korea-strait")
  ;; derive — chokepoint concentration → redundancy priority (Datalog).
  (kqe-query "redundancy(?c) :- routes-via(?c)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "redundancy(?c) :- routes-via(?c)."))
