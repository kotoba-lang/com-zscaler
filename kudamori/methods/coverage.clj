;; kudamori 管守 — HONEST occupation sub-task coverage map.
;;
;; The asobi/shiori coverage_report pattern (G5 sourcing-honesty): coverage of the
;; full real sewer / confined-space cleaning job is PARTIAL by design. This module
;; does not pretend otherwise — it MEASURES which cleaning sub-tasks an existing
;; method actually implements, and NAMES the gaps (uncovered sub-tasks) explicitly.
;;
;; A sub-task is :covered? true ONLY when a real existing method implements it
;; (named in :method); otherwise :covered? false :method nil (a GAP).
;; Existing methods today: atmosphere, pipe_nav, jetting, handoff, inspection,
;; campaign, rootcut, relining.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142030 (kudamori R0).
(ns kudamori.methods.coverage
  (:require [clojure.string :as str]))

(def sub-tasks
  "The sewer / confined-space in-pipe cleaning occupation decomposed into sub-tasks.
   Methods that exist today: atmosphere, pipe_nav, jetting, handoff, inspection,
   campaign, rootcut, relining."
  [{:id :atmosphere-entry  :desc "confined-space gas entry gate + purge-to-entry"            :covered? true  :method "atmosphere"}
   {:id :navigation        :desc "in-pipe crawler navigation + diameter-fit"                 :covered? true  :method "pipe_nav"}
   {:id :jetting           :desc "hydro-jetting at pipe-material-safe pressure"              :covered? true  :method "jetting"}
   {:id :debris-removal    :desc "debris removal volume estimate"                            :covered? true  :method "jetting"}
   {:id :effluent-handoff  :desc "effluent handoff to mizuho treatment"                      :covered? true  :method "handoff"}
   {:id :cctv-inspection   :desc "in-pipe condition inspection survey"                       :covered? true  :method "inspection"}
   {:id :root-cutting      :desc "root/obstruction cutting"                                  :covered? true  :method "rootcut"}
   {:id :network-campaign  :desc "network-wide multi-segment cleaning campaign planning"     :covered? true  :method "campaign"}
   {:id :relining          :desc "trenchless relining / spot-repair"                         :covered? true  :method "relining"}])

(defn report
  "Honest coverage report over `sub-tasks`."
  []
  (let [total   (count sub-tasks)
        covered (count (filter :covered? sub-tasks))
        gaps    (vec (remove :covered? sub-tasks))]
    {:total    total
     :covered  covered
     :coverage (/ (double covered) total)
     :gaps     gaps}))

(defn report-str
  "Human-readable honest coverage readout."
  []
  (let [{:keys [total covered coverage gaps]} (report)]
    (str/join
     "\n"
     (concat
      [(str "kudamori 管守 — occupation sub-task coverage (HONEST; gaps named, G5)")
       (format "coverage: %.1f%%  (%d/%d sub-tasks covered)"
               (* 100.0 coverage) covered total)
       ""
       (if (seq gaps)
         (format "GAPS (%d uncovered — no method implements these yet):" (count gaps))
         "GAPS: none — every cleaning sub-task has an implementing method (9/9).")]
      (map #(str "  - " (name (:id %)) ": " (:desc %)) gaps)))))

(defn -main [& _args]
  (println (report-str)))
