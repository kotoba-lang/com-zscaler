;; mesh.clj — toritsugi 取次 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:toritsugi (citizen government-procedure concierge).
;; Service mesh pattern (SERVICE-MESH-PATTERN.md): request-driven, :on-http trigger.
;; The on-http handler records the member's procedure request and returns the coded
;; procedure steps from the registry via Datalog. The full coded procedure registry
;; stays in the actor's existing methods.
;;
;; Posture: DEFAULT self-submit — returns guidance/steps, NEVER submits or represents
;; on the member's behalf (行政書士法/UPL boundary).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns toritsugi)

(defn handle []
  ;; record the requested procedure's coded steps; return them as self-submit guidance.
  (kqe-assert! "toritsugi" "moving-notice" "step" "fill-form")
  (kqe-assert! "toritsugi" "moving-notice" "step" "submit-self")
  (kqe-query "guidance(?s) :- step(?s)."))

(defn run [ctx] (handle))
(defn on-http [req] (handle))
