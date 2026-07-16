;; mesh.clj — hoshimori 星守 KOTOBA Mesh entry component (Clojure / kotoba-clj).
;;
;; The mesh-hosting face of actor:hoshimori (off-Earth / orbital stewardship mirror).
;; Compiled by kotoba-clj into a kotoba:kais WASM component, placed by the KOTOBA
;; Mesh lattice. Kotoba-native slice: observe operator→regime occupancy edges as
;; Datom assertions, derive orbital-congestion concentration via Datalog, routed to
;; STEWARDSHIP. The full analysis stays in the actor's .cljc methods.
;;
;; Posture: G1 = stewardship map, NEVER a targeting/interception aid — shell/regime
;; AGGREGATE only, no precise predictive ephemeris.
;; host-imports: kqe-assert! / kqe-query → kotoba:kais/kqe (needs cap/kqe)
(ns hoshimori)

(defn run [ctx]
  ;; observe — operator presence by orbital REGIME (aggregate; no ephemeris, G1).
  (kqe-assert! "hoshimori" "starlink" "occupies" "leo")
  (kqe-assert! "hoshimori" "oneweb" "occupies" "leo")
  (kqe-assert! "hoshimori" "gps" "occupies" "meo")
  ;; derive — orbital-congestion concentration → stewardship priority (Datalog).
  (kqe-query "stewardship(?r) :- occupies(?r)."))

(defn on-kse [topic payload]
  ;; KSE-topic trigger (observatory on-kse pattern, ADR-2606230001 §4).
  (kqe-query "stewardship(?r) :- occupies(?r)."))
