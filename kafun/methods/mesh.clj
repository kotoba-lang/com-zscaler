;; mesh.clj — kafun 花粉 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kafun (花粉撲滅 remediation gate). Observatory
;; on-kse pattern (ADR-2606230001 §4): observes source→exposed emission edges as
;; Datom assertions and derives pollen-source concentration via Datalog, routed to
;; RESTORATION (主伐再造林). The full per-stand remediation gate stays in methods.
;;
;; Posture: 撲滅 = ecological RESTORATION, NEVER deforestation-for-profit; a
;; restoration worklist, NEVER a cut-list (G1); assessment only, kafun never cuts.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kafun)

(defn observe []
  ;; observe — pollen sources and exposed populations (aggregate).
  (kqe-assert! "kafun" "cedar-stand" "emits" "exposed-region")
  (kqe-assert! "kafun" "cypress-stand" "emits" "exposed-region")
  ;; derive — pollen-source concentration → restoration priority (Datalog).
  (kqe-query "restoration(?r) :- emits(?r)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
