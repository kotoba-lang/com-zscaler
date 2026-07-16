;; kudamori 管守 — cross-actor handoff edges in the kotoba Datom log.
;;
;; The in-pipe crawler is a SEAM in the sanitation chain, not an island:
;;     kudamori 管守 (in-pipe cleaning) → mizuho 水穂 (wastewater TREATMENT)
;; This module makes that chain queryable as append-only `:handoff/*` 縁 — the R0→R1
;; integration step that lets one actor's output become the next actor's input on the
;; shared Datom log, with source provenance on every edge.
;;
;;   * outbound — a cleaned pipe segment (debris removed + residual effluent) becomes a
;;                mizuho treatment intent; the effluent is NEVER discharged untreated (G2);
;;   * a provenance gate (G9) — every handoff must name both a source and a destination
;;     actor; an orphan handoff RAISES (no unattributed chain edge).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142030 (kudamori R0→R1 integration).
(ns kudamori.methods.handoff
  (:require [clojure.string :as str]))

(defn assert-handoff!
  "RAISE if a handoff lacks a source or destination actor (no unattributed chain
   edge — provenance is structural, G9)."
  [{:keys [from-actor to-actor] :as h}]
  (when (or (str/blank? (str from-actor)) (str/blank? (str to-actor)))
    (throw (ex-info "handoff missing source/destination actor" {:handoff h})))
  h)

(defn effluent-handoff
  "Map a cleaned pipe segment → a mizuho 水穂 wastewater-TREATMENT intent.
   `seg` = {:segment-id :debris-m3 :effluent-l}. Source actor = kudamori,
   destination actor = mizuho. The residual effluent is never discharged untreated (G2)."
  [seg]
  (assert-handoff!
   {:id (str "out." (:segment-id seg))
    :from-actor "kudamori" :to-actor "mizuho" :kind :effluent
    :payload {:segment-id (:segment-id seg)
              :debris-m3 (:debris-m3 seg)
              :effluent-l (:effluent-l seg)}}))

(defn outbound-handoff
  "Map cleaned pipe segments → mizuho treatment-intent handoffs.
   Each segment: {:segment-id :debris-m3 :effluent-l}."
  [segments]
  (mapv effluent-handoff segments))

(defn- fmt [v]
  (cond (keyword? v) (str v)
        (string? v) (if (str/starts-with? v ":") v (str \" v \"))
        :else (str v)))

(defn emit
  "EAVT handoff datoms (GROUND :add). Each handoff is an edge entity
   en.handoff.<from>.<to>.<id> carrying actor provenance + payload kind."
  [handoffs tx]
  (let [lines
        (mapcat
         (fn [h]
           (let [eid (str "en.handoff." (:from-actor h) "." (:to-actor h) "." (:id h))]
             [(str "[" (fmt eid) " :handoff/from-actor " (fmt (:from-actor h)) " " tx " :add]")
              (str "[" (fmt eid) " :handoff/to-actor " (fmt (:to-actor h)) " " tx " :add]")
              (str "[" (fmt eid) " :handoff/kind " (fmt (:kind h)) " " tx " :add]")]))
         handoffs)]
    (str ";; kudamori 管守 — GENERATED handoff 縁 (chain: kudamori→mizuho effluent). ADR-2606142030.\n"
         "[\n" (str/join "\n" lines) "\n]\n")))

(defn -main [& _]
  (let [segments [{:segment-id "seg-1-2" :debris-m3 0.42 :effluent-l 540.0}
                  {:segment-id "seg-2-3" :debris-m3 0.18 :effluent-l 360.0}]
        out (outbound-handoff segments)]
    (print (emit out 1))
    (flush)))
