;; mesh.clj — maps KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:maps (kotoba-native geospatial substrate).
;; Observatory on-kse pattern (ADR-2606230001 §4): observes feature→cell location
;; edges as Datom assertions and derives the H3-cell spatial index via Datalog.
;; The full :feature/* ontology + getChunk per-cell probe stays in methods.
;;
;; Posture: an observational geospatial substrate (kotoba Datom log, H3-cell-as-Datom
;; AVET index); a map, not a tracker.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns maps)

(defn observe []
  ;; observe — features located in H3 cells (spatial index).
  (kqe-assert! "maps" "feature-a" "located" "cell-h3-1")
  (kqe-assert! "maps" "feature-b" "located" "cell-h3-1")
  (kqe-assert! "maps" "feature-c" "located" "cell-h3-2")
  ;; derive — per-cell feature index (Datalog).
  (kqe-query "spatial-index(?c) :- located(?c)."))

(defn run [ctx] (observe))
(defn on-kse [topic payload] (observe))
