;; madomori 窓守 — cross-actor handoff edges in the kotoba Datom log.
;;
;; The façade-cleaning sweep is a SEAM in the building-maintenance chain, not an island:
;;     madomori 窓守 (façade clean + DEFECT detection) → tatekata 建方 (construction/repair)
;; During a sweep madomori can DETECT a façade defect — a cracked pane, a sealant failure,
;; spalling — that is out of scope for a cleaning unit and belongs to a repair body. This
;; module makes that chain queryable as append-only `:handoff/*` 縁: a structural-defect
;; observation becomes a tatekata repair work-order intent, with source provenance on the edge.
;;
;;   * outbound — a detected façade defect {:pane-id :defect-kind :severity} becomes a
;;                tatekata repair-order intent;
;;   * a provenance gate (G9) — every handoff must name both a source and a destination
;;     actor; an orphan handoff RAISES (no unattributed chain edge).
;;
;; ★ G3 privacy — the handoff payload carries ONLY the structural defect descriptor
;; (pane id + defect kind + severity). NO imagery/photo, NO interior, NO person data is
;; representable on a handoff (madomori's on-device-only imagery never leaves the device).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0→R1 integration).
(ns madomori.methods.handoff
  (:require [clojure.string :as str]))

(defn assert-handoff!
  "RAISE if a handoff lacks a source or destination actor (no unattributed chain
   edge — provenance is structural, G9)."
  [{:keys [from-actor to-actor] :as h}]
  (when (or (str/blank? (str from-actor)) (str/blank? (str to-actor)))
    (throw (ex-info "handoff missing source/destination actor" {:handoff h})))
  h)

(defn repair-handoff
  "Map a detected façade defect → a tatekata repair work-order intent.
   `defect` = {:pane-id :defect-kind :severity}. Destination actor = tatekata.
   ★ G3 — the payload carries ONLY the structural defect descriptor (pane id +
   defect kind + severity); no imagery/interior/person data."
  [defect]
  (assert-handoff!
   {:id (str "rep." (:pane-id defect))
    :from-actor "madomori" :to-actor "tatekata" :kind :repair-order
    :payload {:pane-id (:pane-id defect)
              :defect-kind (:defect-kind defect)
              :severity (:severity defect)}}))

(defn outbound-handoffs
  "Map a sweep's detected façade defects → tatekata repair-order intents.
   Each defect: {:pane-id :defect-kind :severity}. Source actor = madomori."
  [defects]
  (mapv repair-handoff defects))

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
    (str ";; madomori 窓守 — GENERATED handoff 縁 (chain: madomori→tatekata, repair-order). ADR-2606142020.\n"
         "[\n" (str/join "\n" lines) "\n]\n")))

(defn -main [& _]
  (let [defects [{:pane-id "p-12-04" :defect-kind :cracked-pane :severity :high}
                 {:pane-id "p-07-11" :defect-kind :sealant-failure :severity :medium}]
        all (outbound-handoffs defects)]
    (print (emit all 1))
    (flush)))
