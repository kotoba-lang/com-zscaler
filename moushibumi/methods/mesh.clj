;; mesh.clj — moushibumi 申文 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:moushibumi (citizen democratic-participation
;; concierge). Service mesh pattern (SERVICE-MESH-PATTERN.md): request-driven,
;; :on-http trigger. The on-http handler records the participation request and
;; returns coded action steps (選挙情報 / 請願・陳情 / パブコメ) via Datalog. The full
;; coded registry stays in the actor's existing methods.
;;
;; Posture: DEFAULT self-submit; 公職選挙法 + 政治中立 — provides procedure steps,
;; NO campaigning, never acts/advocates on the member's behalf.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns moushibumi)

(defn handle []
  ;; record the participation action's coded steps; return as neutral self-submit guidance.
  (kqe-assert! "moushibumi" "public-comment" "action" "draft-comment")
  (kqe-assert! "moushibumi" "public-comment" "action" "submit-self")
  (kqe-query "guidance(?a) :- action(?a)."))

(defn run [ctx] (handle))
(defn on-http [req] (handle))
