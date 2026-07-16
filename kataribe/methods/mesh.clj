;; mesh.clj — kataribe 語部 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:kataribe (press + publishing + translation
;; medium). Observatory on-kse pattern (ADR-2606230001 §4): observes outlet→story
;; reporting edges as Datom assertions and derives a section/co-mention map via
;; Datalog. The full translation/medium rendering stays in the actor's methods.
;;
;; Posture: a news MEDIUM — headline + link-out, NEVER the body/verdict; no ads,
;; no personalization, no surveillance.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns kataribe)

(defn observe []
  ;; observe — outlets reporting stories (headline + link-out only, never body).
  (kqe-assert! "kataribe" "outlet-a" "reports" "story-x")
  (kqe-assert! "kataribe" "outlet-b" "reports" "story-x")
  (kqe-assert! "kataribe" "outlet-c" "reports" "story-y")
  ;; derive — co-mention / section concentration → medium map (Datalog).
  (kqe-query "medium(?s) :- reports(?s)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
