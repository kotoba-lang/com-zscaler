;; mesh.clj — himotoki 繙き KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:himotoki (active disclosure-request filer). Service
;; mesh pattern (SERVICE-MESH-PATTERN.md): request-driven, :on-http trigger. The
;; on-http handler records the member's disclosure target and returns an own-data
;; DSAR/FOIA draft via Datalog. The full coded target registry stays in methods.
;;
;; Posture: own-data-only, consent-bound; returns a DRAFT (unsent) — the mesh
;; component files nothing itself (no-server-key, member sends).
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns himotoki)

(defn handle []
  ;; record the disclosure target's coded request type; return the unsent draft.
  (kqe-assert! "himotoki" "data-broker" "files" "dsar-appi")
  (kqe-assert! "himotoki" "platform" "files" "dsar-gdpr")
  (kqe-query "draft(?d) :- files(?d)."))

(defn run [ctx] (handle))
(defn on-http [req] (handle))
