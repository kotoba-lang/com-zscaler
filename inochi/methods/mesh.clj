;; mesh.clj — inochi 命 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:inochi (living-world / 生命圏 KG mirror). Compiled
;; by kotoba-clj into a real kotoba:kais WASM component and placed by the KOTOBA
;; Mesh lattice. Kotoba-native slice: observe pressure→ecosystem edges as Datom
;; assertions, derive ecological 取-concentration via Datalog, routed to RESTORATION.
;; The full analyze logic stays in the actor's .cljc methods.
;;
;; Posture: G1 = restoration map, NEVER a target-list (no occurrence coordinates).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns inochi)

(defn run [ctx]
  ;; observe — pressures borne by living systems (aggregate; no coordinates, G1).
  (kqe-assert! "inochi" "habitat-loss" "pressures" "coral-reef")
  (kqe-assert! "inochi" "overfishing" "pressures" "tuna-stock")
  (kqe-assert! "inochi" "deforestation" "pressures" "rainforest")
  ;; derive — ecological pressure concentration → restoration priority (Datalog).
  (kqe-query "restoration(?e) :- pressures(?e)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "restoration(?e) :- pressures(?e)."))
