;; kudamori 管守 — network-wide multi-segment cleaning CAMPAIGN planning.
;;
;; A municipal foul-sewer reach is not one pipe but a NETWORK of segments, each with
;; its own blockage risk and time-since-last-cleaned. A campaign decides WHICH segments
;; to clean now, and in WHAT ORDER, to minimise crawler travel between access manholes:
;;
;;   * prioritize     — rank segments high-first by a priority score (blockage-risk
;;                      weighted + cleaning staleness);
;;   * campaign-tour  — sequence the selected segments by nearest-neighbour over their
;;                      access manholes from the depot [0 0]; returns ordered ids + total
;;                      travel distance;
;;   * plan-campaign  — select the top-N (or all above a risk threshold) prioritized
;;                      segments, build the tour, and stamp EVERY stop with
;;                      :atmosphere-recheck-required true. The campaign is a planning
;;                      layer ABOVE the per-entry atmosphere gate (★ G5,
;;                      kudamori.methods.atmosphere) — it NEVER batches away the gas
;;                      gate: every confined-space entry re-checks atmosphere on arrival.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure compute;
;; it moves no real robot (G1) and admits no entry on its own — atmosphere/assert-entry!
;; remains the only thing that admits a human/crawler to a confined space.
;; Per ADR-2606142030 (kudamori R0). Clojure-first (the GAP-actor wave).
(ns kudamori.methods.campaign
  (:require [kudamori.methods.atmosphere :as atm]))

;; ── priority scoring ─────────────────────────────────────────────────────────
(def ^:const risk-weight     0.7)   ; blockage-risk dominates
(def ^:const staleness-weight 0.3)  ; time-since-cleaned contributes
(def ^:const staleness-cap-days 365.0) ; a year of neglect = full staleness term

(defn priority-score
  "Priority of a single pipe segment in [0,1]: blockage-risk (0..1) weighted plus a
   normalised cleaning-staleness term (:last-cleaned-days capped at a year). A segment
   never cleaned (no :last-cleaned-days) is treated as maximally stale."
  [{:keys [blockage-risk last-cleaned-days]}]
  (let [risk      (double (or blockage-risk 0.0))
        days      (double (or last-cleaned-days staleness-cap-days))
        staleness (min 1.0 (/ days staleness-cap-days))]
    (+ (* risk-weight risk)
       (* staleness-weight staleness))))

(defn prioritize
  "Rank `segments` high-priority-first by `priority-score`. Each returned segment is
   annotated with its :priority. Stable for ties (sorts by descending score)."
  [segments]
  (->> segments
       (map #(assoc % :priority (priority-score %)))
       (sort-by :priority >)
       vec))

;; ── nearest-neighbour tour over access manholes ──────────────────────────────
(defn- dist [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- (double x2) (double x1)) 2)
                (Math/pow (- (double y2) (double y1)) 2))))

(def ^:const depot [0.0 0.0])

(defn campaign-tour
  "Sequence `selected` segments (each {:segment-id :access [x y] …}) by nearest-neighbour
   from the depot [0 0], visiting each segment's access manhole exactly once. Returns
   {:order [segment-id…] :travel-m total} where travel is the depot→…→last-stop path
   length (manhole-to-manhole). An empty selection is a zero-travel empty tour."
  ([selected] (campaign-tour selected depot))
  ([selected start]
   (loop [here       start
          remaining  (vec selected)
          order      []
          travel     0.0]
     (if (empty? remaining)
       {:order order :travel-m travel}
       (let [nearest (apply min-key #(dist here (:access %)) remaining)
             d       (dist here (:access nearest))]
         (recur (:access nearest)
                (vec (remove #(= (:segment-id %) (:segment-id nearest)) remaining))
                (conj order (:segment-id nearest))
                (+ travel d)))))))

;; ── full campaign plan ────────────────────────────────────────────────────────
(defn- select-segments
  "Select prioritized segments: keep all at/above :risk-threshold, then cap at :top-n
   (if given). Defaults: no threshold (0.0), no cap."
  [prioritized {:keys [risk-threshold top-n]}]
  (let [thr   (double (or risk-threshold 0.0))
        kept  (filterv #(>= (double (or (:blockage-risk %) 0.0)) thr) prioritized)]
    (if top-n (vec (take top-n kept)) kept)))

(defn plan-campaign
  "Plan a network-wide cleaning campaign over `segments`.
   `opts` selects the work set:
     :risk-threshold — keep only segments with :blockage-risk ≥ threshold (default 0.0)
     :top-n          — cap the work set to the N highest-priority segments (optional)
   Segments are prioritized, selected, then sequenced by nearest-neighbour from the
   depot. EVERY stop carries :atmosphere-recheck-required true — the campaign is a
   layer above the per-entry atmosphere gate (★ G5) and NEVER skips the gas gate on a
   confined-space entry. Returns {:stops [{:segment-id :priority :access
   :atmosphere-recheck-required true}…] :travel-m n}."
  ([segments] (plan-campaign segments {}))
  ([segments opts]
   (let [prioritized (prioritize segments)
         selected    (select-segments prioritized opts)
         {:keys [order travel-m]} (campaign-tour selected)
         by-id       (into {} (map (juxt :segment-id identity) selected))
         stops       (mapv (fn [sid]
                             (let [seg (get by-id sid)]
                               {:segment-id sid
                                :priority   (:priority seg)
                                :access     (:access seg)
                                ;; ★ G5 — every confined-space entry re-checks atmosphere
                                ;; on arrival via kudamori.methods.atmosphere/assert-entry!;
                                ;; the campaign batches the ROUTE, never the gas gate.
                                :atmosphere-recheck-required true
                                :atmosphere-gate `atm/assert-entry!}))
                           order)]
     {:stops    stops
      :travel-m travel-m})))

;; ── demo ───────────────────────────────────────────────────────────────────────
(defn -main [& _args]
  (let [network
        [{:segment-id "seg-1-2" :blockage-risk 0.85 :last-cleaned-days 400 :access [10.0 0.0]}
         {:segment-id "seg-2-3" :blockage-risk 0.40 :last-cleaned-days 120 :access [10.0 12.0]}
         {:segment-id "seg-3-4" :blockage-risk 0.92 :last-cleaned-days 30  :access [25.0 12.0]}
         {:segment-id "seg-4-5" :blockage-risk 0.10 :last-cleaned-days 15  :access [25.0 0.0]}
         {:segment-id "seg-5-6" :blockage-risk 0.55 :last-cleaned-days 300 :access [40.0 5.0]}]
        plan (plan-campaign network {:risk-threshold 0.5})]
    (println "kudamori 管守 — network cleaning campaign plan")
    (println (format "network: %d segments  |  selected (risk ≥ 0.5): %d stops"
                     (count network) (count (:stops plan))))
    (println (format "crawler travel (depot→…): %.1f m" (:travel-m plan)))
    (println "tour (high-priority, nearest-neighbour sequenced):")
    (doseq [s (:stops plan)]
      (println (format "  - %-9s  priority %.3f  access %s  atmosphere-recheck-required %s"
                       (:segment-id s) (:priority s) (:access s)
                       (:atmosphere-recheck-required s))))
    (println "★ G5: every stop RE-CHECKS atmosphere on entry — the campaign never skips the gas gate.")))
