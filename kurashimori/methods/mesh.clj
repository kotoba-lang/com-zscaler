;; mesh.clj — kurashimori 暮らし守 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kurashimori (citizen consumer-protection concierge).
;; Service mesh pattern (SERVICE-MESH-PATTERN.md): request-driven, :on-http trigger.
;; The on-http handler records the member's consumer issue and returns coded remedy
;; steps (cooling-off / refund / escalation) via Datalog. The full coded registry
;; stays in the actor's existing methods.
;;
;; Posture: DEFAULT self-submit — returns remedy guidance, never acts on the member's
;; behalf (UPL boundary); no 取立 (no debt collection).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kurashimori)

(defn handle []
  ;; record the consumer issue's coded remedies; return them as self-submit guidance.
  (kqe-assert! "kurashimori" "door-to-door-sale" "remedy" "cooling-off")
  (kqe-assert! "kurashimori" "defective-goods" "remedy" "refund-request")
  (kqe-query "guidance(?r) :- remedy(?r)."))

(defn run [ctx] (handle))
(defn on-http [req] (handle))
