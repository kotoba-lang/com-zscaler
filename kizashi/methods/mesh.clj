;; mesh.clj — kizashi 兆 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kizashi (non-invasive multimodal body-scan /
;; sign-sensing instrument layer). Observatory on-kse pattern (ADR-2606230001 §4):
;; observes sensor→sign sensing edges as Datom assertions and derives a sign map via
;; Datalog (kizashi senses → mitate diagnoses → iyashi treats). The full sensing
;; pipeline stays in the actor's existing methods.
;;
;; Posture: NON-DIAGNOSTIC instrument layer (medical-device boundary, anti-
;; pseudoscience); senses signs, never diagnoses or treats.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kizashi)

(defn observe []
  ;; observe — non-invasive sensed signs (sensor → sign), never a diagnosis.
  (kqe-assert! "kizashi" "thermal" "senses" "sign-a")
  (kqe-assert! "kizashi" "acoustic" "senses" "sign-b")
  (kqe-assert! "kizashi" "optical" "senses" "sign-c")
  ;; derive — sensed-sign map handed to mitate (Datalog).
  (kqe-query "sign-map(?s) :- senses(?s)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
