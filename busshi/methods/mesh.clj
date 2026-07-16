;; mesh.clj — busshi 物資 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:busshi (ADR-2606161730). Compiled by
;; `kotoba-clj::component::compile_kais_mesh_component_str` into a real
;; `kotoba:kais` WASM component and placed by the KOTOBA Mesh lattice
;; (kotoba-lattice). One language family — manifest = EDN · component = Clojure
;; (kotoba-clj) · data = Datomic/Datalog.
;;
;; Scope (honest): the kotoba-NATIVE slice the current kotoba-clj subset can
;; express — observe a representative slice of the commodity producer graph as
;; Datom assertions, then DERIVE producer concentration via Datalog over the
;; same datoms. The full §2(l) multi-gen risk analysis (HHI, carbon/irreversible
;; footprint weighting) stays in the richer `methods/analyze.cljc` port until
;; kotoba-clj grows maps/sort/decimals.
;;
;; Constitutional posture (ADR-2606161730 / §2(l) ADR-2606161700):
;;   G1 observation only — never a trade. G2 resilience / de-monopolization map,
;;   NEVER a target-list or market signal. G3 producer share is a disclosed fact,
;;   never a verdict/forecast. G5 aggregate-first, no coordinates.
;;
;; host-imports used:  kqe-assert! / kqe-query  → kotoba:kais/kqe  (needs cap/kqe)
(ns busshi)

(defn run [ctx]
  ;; observe — assert a representative slice of the producer→commodity graph
  ;; (country produces commodity) into the append-only kotoba Datom log
  ;; (graph "busshi"). :representative, public-knowledge, never coordinates (G5).
  (kqe-assert! "busshi" "za" "produces" "platinum")
  (kqe-assert! "busshi" "ru" "produces" "palladium")
  (kqe-assert! "busshi" "cn" "produces" "gold")
  ;; derive — producer concentration over the same datoms. Datalog is the query
  ;; language (a resilience / de-monopolization map; never a market signal, G2).
  (kqe-query "concentration(?p) :- produces(?p)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4). surface the producer-concentration query.
  (kqe-query "concentration(?p) :- produces(?p)."))
