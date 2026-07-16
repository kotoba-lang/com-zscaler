;; mesh.clj — tsumugi 紡ぎ KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:tsumugi (Engi Knowledge Graph intel weaver).
;; Compiled by kotoba-clj into a kotoba:kais WASM component, placed by the KOTOBA
;; Mesh lattice. Kotoba-native slice: observe power-entity 縁 (grasping) edges as
;; Datom assertions, derive 取-concentration via Datalog, routed to RELEASE. The
;; full diachronic/旗 analysis stays in the actor's existing methods.
;;
;; Posture: power-only, edge-primary karma; a release map, NEVER a target-list;
;; non-adjudicating, person-excluded (no-doxxing).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns tsumugi)

(defn run [ctx]
  ;; observe — power-entity grasping edges (public power only; person-excluded).
  (kqe-assert! "tsumugi" "conglomerate" "grasps" "supply-node")
  (kqe-assert! "tsumugi" "ministry" "grasps" "procurement-channel")
  (kqe-assert! "tsumugi" "platform" "grasps" "data-flow")
  ;; derive — 取-concentration → release priority (Datalog).
  (kqe-query "release(?d) :- grasps(?d)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "release(?d) :- grasps(?d)."))
