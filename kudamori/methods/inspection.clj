;; kudamori 管守 — in-pipe CONDITION inspection survey + defect grading (PACP-like).
;;
;; Before a crawler jets (jetting.clj) or a campaign sequences (campaign.clj), the pipe is
;; SURVEYED: a CCTV/sonde pass logs defects (cracks, deposits, root intrusion, fractures,
;; deformation, infiltration…) at positions along each segment. This module grades that
;; survey:
;;
;;   * grade-segment — a single segment's observations → a structural condition GRADE
;;     (1..5, max observed severity, PACP-like worst-first scoring) plus a derived
;;     :blockage-risk in [0,1] (weighted toward FLOW-OBSTRUCTING defect kinds —
;;     deposits / roots / blockage — at high severity);
;;   * survey — grade-segment over many segments, sorted WORST-FIRST;
;;   * to-campaign-input — adapt the survey to the exact shape campaign/prioritize
;;     consumes ({:segment-id :blockage-risk :last-cleaned-days :access}), so an
;;     inspection survey can feed the cleaning campaign directly.
;;
;; PIPES / INFRASTRUCTURE ONLY — observations are about pipe condition (defect kind,
;; position, severity); there is NO person or biometric datum here (G3 no-surveillance:
;; kudamori watches the pipe, never the worker).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable. Pure compute;
;; it inspects no real pipe (G1) — it grades a survey log handed to it.
;; Per ADR-2606142030 (kudamori R0). Clojure-first (the GAP-actor wave).
(ns kudamori.methods.inspection)

;; ── defect taxonomy → blockage contribution ──────────────────────────────────
;; Each defect kind carries a flow-obstruction weight in [0,1]: how much that defect,
;; at full severity, contributes to a blockage. Flow-obstructing defects (deposits,
;; roots, an outright blockage) dominate; structural-only defects (a hairline crack,
;; infiltration) barely move blockage-risk even though they may drive the GRADE high.
(def defect-blockage-weight
  {:blockage     1.00   ; an actual obstruction
   :roots        0.90   ; root intrusion mats the bore
   :deposits     0.80   ; settled grease/grit/scale
   :encrustation 0.70   ; mineral build-up narrowing the bore
   :debris       0.65   ; loose debris
   :deformation  0.45   ; bore deformed/ovalised → reduced section
   :collapse     0.85   ; partial collapse intrudes into the bore
   :fracture     0.25   ; structural, mildly flow-affecting
   :crack        0.10   ; structural, negligible flow effect
   :infiltration 0.15   ; water in, little obstruction
   :joint        0.10}) ; displaced joint, mostly structural

(def ^:const default-blockage-weight 0.30)  ; unknown defect kind: conservative-ish middle

(defn- clamp01 [x] (max 0.0 (min 1.0 (double x))))

(defn- defect-blockage
  "Blockage contribution of a single observation in [0,1]: the defect kind's
   flow-obstruction weight scaled by its normalised severity (severity/5)."
  [{:keys [defect-kind severity]}]
  (let [w (double (get defect-blockage-weight defect-kind default-blockage-weight))
        s (/ (double (or severity 0)) 5.0)]
    (clamp01 (* w s))))

(defn grade-segment
  "Grade one segment from its in-pipe observations.
   `observations` = [{:position-m :defect-kind :severity 1..5} …] (may be empty → a
   clean segment). Returns {:segment-id :grade :blockage-risk :defect-count}:
     :grade         — PACP-like structural condition grade 1..5 = MAX observed severity
                      (a clean segment with no observations grades 1, sound);
     :blockage-risk — [0,1], the WORST single defect's blockage contribution
                      (flow-obstructing kinds × severity dominate); a clean/structural-only
                      segment stays low even when the grade is high;
     :defect-count  — number of observations logged."
  [segment-id observations]
  (let [obs   (vec observations)
        sevs  (keep :severity obs)
        grade (if (seq sevs) (long (apply max sevs)) 1)
        risk  (if (seq obs)
                (clamp01 (apply max (map defect-blockage obs)))
                0.0)]
    {:segment-id    segment-id
     :grade         grade
     :blockage-risk risk
     :defect-count  (count obs)}))

(defn survey
  "Grade every segment in a survey and return the seq sorted WORST-FIRST.
   `segments` = [{:segment-id :observations [...]} …]. Worst-first = highest
   :blockage-risk first, then highest :grade (a high-risk segment is the cleaning
   priority; ties break toward the worse structural grade)."
  [segments]
  (->> segments
       (map (fn [{:keys [segment-id observations]}]
              (grade-segment segment-id observations)))
       (sort-by (juxt :blockage-risk :grade) (fn [a b] (compare b a)))
       vec))

(defn to-campaign-input
  "Adapt a `survey` result into the shape `kudamori.methods.campaign/prioritize`
   consumes: {:segment-id :blockage-risk :last-cleaned-days :access}. The inspection
   supplies :segment-id + :blockage-risk; per-segment :last-cleaned-days and :access
   (manhole [x y]) come from `meta-by-id` (a map segment-id → {:last-cleaned-days :access}),
   defaulting to a never-cleaned segment with an unknown access at the depot. This is the
   seam: survey → to-campaign-input → campaign/prioritize / plan-campaign."
  ([graded] (to-campaign-input graded {}))
  ([graded meta-by-id]
   (mapv (fn [{:keys [segment-id blockage-risk]}]
           (let [m (get meta-by-id segment-id)]
             {:segment-id        segment-id
              :blockage-risk     blockage-risk
              :last-cleaned-days (:last-cleaned-days m)   ; nil ⇒ campaign treats as maximally stale
              :access            (or (:access m) [0.0 0.0])}))
         graded)))

;; ── demo ───────────────────────────────────────────────────────────────────────
(defn -main [& _args]
  (let [survey-in
        [{:segment-id "seg-1-2"
          :observations [{:position-m 2.0  :defect-kind :roots     :severity 5}
                         {:position-m 7.5  :defect-kind :crack     :severity 2}]}
         {:segment-id "seg-2-3"
          :observations [{:position-m 4.0  :defect-kind :deposits  :severity 3}]}
         {:segment-id "seg-3-4"
          :observations [{:position-m 1.0  :defect-kind :blockage  :severity 5}
                         {:position-m 6.0  :defect-kind :fracture  :severity 4}]}
         {:segment-id "seg-4-5"
          :observations [{:position-m 3.0  :defect-kind :crack     :severity 1}]}
         {:segment-id "seg-5-6"
          :observations []}]   ; a clean segment
        graded   (survey survey-in)
        meta-by  {"seg-1-2" {:last-cleaned-days 400 :access [10.0 0.0]}
                  "seg-2-3" {:last-cleaned-days 120 :access [10.0 12.0]}
                  "seg-3-4" {:last-cleaned-days 30  :access [25.0 12.0]}
                  "seg-4-5" {:last-cleaned-days 15  :access [25.0 0.0]}
                  "seg-5-6" {:last-cleaned-days 10  :access [40.0 5.0]}}
        camp-in  (to-campaign-input graded meta-by)]
    (println "kudamori 管守 — in-pipe condition inspection survey (PACP-like grading)")
    (println (format "surveyed: %d segments  (pipes/infrastructure only — no person data, G3)"
                     (count graded)))
    (println "worst-first survey:")
    (doseq [g graded]
      (println (format "  - %-9s  grade %d  blockage-risk %.3f  defects %d"
                       (:segment-id g) (:grade g) (:blockage-risk g) (:defect-count g))))
    (println "\ncampaign input (survey → campaign/prioritize seam):")
    (doseq [c camp-in]
      (println (format "  - %-9s  blockage-risk %.3f  last-cleaned %s  access %s"
                       (:segment-id c) (:blockage-risk c)
                       (:last-cleaned-days c) (:access c))))))
