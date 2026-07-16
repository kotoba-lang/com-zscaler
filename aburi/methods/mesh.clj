;; mesh.clj — aburi 炙り KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:aburi (personal-tracking-exposure observatory,
;; member-side own-data). Observatory on-kse pattern (ADR-2606230001 §4): observes
;; permission→broker exposure edges as Datom assertions and derives exposure
;; concentration via Datalog. The full ToS/permission analysis stays in methods.
;;
;; Posture: member-side OWN-DATA exposure MAP (when I accept a ToS, which ad
;; networks / brokers get my data); aggregate, never a tracking tool itself.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns aburi)

(defn observe []
  ;; observe — permissions exposing data to brokers (member-side own-data).
  (kqe-assert! "aburi" "location-permission" "exposes" "ad-network")
  (kqe-assert! "aburi" "contacts-permission" "exposes" "data-broker")
  (kqe-assert! "aburi" "tos-accept" "exposes" "ad-network")
  ;; derive — exposure concentration → exposure map (Datalog).
  (kqe-query "exposure(?b) :- exposes(?b)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
