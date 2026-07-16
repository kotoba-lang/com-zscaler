;; madomori 窓守 — occupation sub-task coverage map (HONEST, partial by design).
;;
;; G5 sourcing-honesty (asobi/shiori coverage_report pattern): a sub-task is
;; `:covered? true` ONLY if a real existing method implements it. Everything
;; else is a GAP we MEASURE and NAME — we never inflate the coverage fraction.
;;
;; Existing methods that back coverage claims: facade_path, wind_envelope,
;; adhesion, handoff, multi_face, water_recovery, anchor. Any sub-task NOT yet
;; backed by a real method would be surfaced as an honest gap, not silently
;; omitted; at present all 8 sub-tasks are covered.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.coverage
  (:require [clojure.string :as str]))

(def sub-tasks
  "The façade / high-rise window-cleaning occupation taxonomy.
   `:covered? true` ONLY where a real existing method implements the sub-task."
  [{:id :coverage-path      :desc "boustrophedon façade coverage path over pane grid"        :covered? true  :method "facade_path"}
   {:id :washing            :desc "wash/clean with per-pane water+agent budget"               :covered? true  :method "facade_path"}
   {:id :wind-safety        :desc "wind work-stop + fall-arrest redundancy"                   :covered? true  :method "wind_envelope"}
   {:id :adhesion-safety    :desc "suction/adhesion factor-of-safety"                         :covered? true  :method "adhesion"}
   {:id :defect-inspection  :desc "façade defect detection → tatekata repair order"           :covered? true  :method "handoff"}
   {:id :anchor-rigging     :desc "rope/anchor rigging + descent setup"                       :covered? true  :method "anchor"}
   {:id :multi-face-routing :desc "routing across multiple building faces/elevations"         :covered? true  :method "multi_face"}
   {:id :water-recovery     :desc "runoff/detergent capture + water recovery"                 :covered? true  :method "water_recovery"}])

(defn report
  "Honest coverage summary: total / covered count / coverage fraction / gap list."
  []
  (let [total   (count sub-tasks)
        covered (filterv :covered? sub-tasks)
        gaps    (filterv (complement :covered?) sub-tasks)]
    {:total    total
     :covered  (count covered)
     :coverage (/ (double (count covered)) total)
     :gaps     gaps}))

(defn report-str
  "Human-readable coverage report with an honest gap bullet list."
  []
  (let [{:keys [total covered coverage gaps]} (report)]
    (str/join
     "\n"
     (concat
      ["madomori 窓守 — occupation sub-task coverage (HONEST, partial by design)"
       (format "  coverage: %.1f%%  (%d/%d sub-tasks backed by a real method)"
               (* 100.0 coverage) covered total)
       ""
       "  GAPS (no method yet — named, not hidden):"]
      (if (seq gaps)
        (mapv #(format "    - %s : %s" (name (:id %)) (:desc %)) gaps)
        ["    (none)"])
      [""]))))

(defn -main [& _]
  (print (report-str))
  (flush))
