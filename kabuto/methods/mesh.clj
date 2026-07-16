;; mesh.clj — kabuto 兜 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kabuto (ADR-2606022000). Compiled by
;; `kotoba-clj::component::compile_kais_mesh_component_str` into a real
;; `kotoba:kais` WASM component and placed by the KOTOBA Mesh lattice
;; (kotoba-lattice) — same language family as the manifest (EDN) and the data
;; (Datomic/Datalog).
;;
;; Scope (honest): this is the kotoba-NATIVE slice of kabuto's pipeline that the
;; current kotoba-clj subset can express — observe a representative slice of the
;; public-company supply graph as Datom assertions, then DERIVE single-source
;; supply concentration via Datalog over the same datoms. The full analyze math
;; (in/out degree, sector×commodity HHI, BigDecimal float-parity) stays in the
;; richer `methods/analyze.cljc` port until kotoba-clj grows maps/sort/decimals.
;;
;; Constitutional posture holds by construction (ADR-2606022000):
;;   G2 RESILIENCE + accountability map, NEVER a 'who to hit' target-list.
;;   G1 public listed-company public-record data only.
;;   G7 offline/seed observation — no live GLEIF/EDGAR universe fetch here.
;;
;; host-imports used:  kqe-assert! / kqe-query  → kotoba:kais/kqe  (needs cap/kqe)
(ns kabuto)

(defn run [ctx]
  ;; observe — assert a representative slice of the supplier→customer supply
  ;; graph into the append-only kotoba Datom log (graph "kabuto"). G7: offline
  ;; seed slice, never the live universe.
  (kqe-assert! "kabuto" "tsmc" "supplies" "apple")
  (kqe-assert! "kabuto" "tsmc" "supplies" "nvidia")
  (kqe-assert! "kabuto" "asml" "supplies" "tsmc")
  ;; derive — single-source supply concentration over the same datoms. Datalog
  ;; is the query language (resilience map so buyers DIVERSIFY; never a verdict).
  (kqe-query "concentration(?c) :- supplies(?c)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4). surface the supply-concentration query.
  (kqe-query "concentration(?c) :- supplies(?c)."))
