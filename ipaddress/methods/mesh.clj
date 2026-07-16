;; mesh.clj — ipaddress KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:ipaddress (authorized network attribution; tadori
;; sibling). Observatory on-kse pattern (ADR-2606230001 §4): observes address→actor
;; attribution edges as Datom assertions and derives the attribution map via Datalog.
;; The full merged-graph tracing stays in the actor's existing methods.
;;
;; Posture: AUTHORIZED attribution only; aggregate; never a private surveillance
;; dragnet; non-adjudicating.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns ipaddress)

(defn observe []
  ;; observe — authorized network attributions (address → actor/cluster).
  (kqe-assert! "ipaddress" "prefix-a" "attributed" "asn-x")
  (kqe-assert! "ipaddress" "prefix-b" "attributed" "asn-x")
  (kqe-assert! "ipaddress" "prefix-c" "attributed" "asn-y")
  ;; derive — attribution concentration → trace map (Datalog).
  (kqe-query "trace(?a) :- attributed(?a)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
