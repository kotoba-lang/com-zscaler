(ns itonami.methods.inspect
  "itonami 営み — R2 vision-inspection hand-off (ADR-2606082300).
  1:1 Clojure port of `methods/inspect.py` (which imports `from analyze import load, analyze,
  read_edn` and `from collections import defaultdict`).

  Closes the loop from the R0 quality finding to in-line vision inspection — the charter-clean
  form of FOX's \"vision-AI quality inspection in the production line\". Two directions:

    1. REQUEST  — from itonami's quality_target (the highest-scrap station) build a vision
       inspection request routed to a manako 眼 on-device detector (ADR-2606034800).
    2. RECONCILE — ingest manako's detection log and reduce it to a defect-class Pareto +
       scrap/rework reconciliation + scan-cycle scrap cross-check.

  CONSTITUTIONAL:
    G1 — inspection INFORMS; never auto-rejects a part or actuates. :detect/verdict is advisory.
    G2 — OBJECT detection only. The inspected entity is a LINE PART (:detect/unit), never a
      person; no face / biometric / worker dimension exists or is permitted.
    G3 — non-adjudicating. Detection classes/verdicts are DISCLOSED detector outputs.

  defaultdict insertion order → ::order pattern. Requires the merged itonami.methods.analyze ns."
  (:require [clojure.string :as str]
            [itonami.methods.analyze :as analyze]
            #?(:clj [clojure.java.io :as io])))

(def PASS ":pass")
(def REWORK ":rework")
(def SCRAP ":scrap")
(def NON-DEFECT-CLASS ":ok")
;; manako on-device detector invariants surfaced into the request (ADR-2606034800)
(def MANAKO-CONSTRAINTS
  ["on-device (no cloud imagery)" "object-only (no biometric / no person)"
   "AGPL-isolated weights" "advisory verdict (no auto-reject, G1)"])

;; ── float formatting (Python f-string parity: HALF_EVEN over the exact double) ──

(defn- fmt-f
  [x n]
  (-> (java.math.BigDecimal. (double x))
      (.setScale (int n) java.math.RoundingMode/HALF_EVEN)
      (.toPlainString)))

(defn- fmt-pct [x] (str (fmt-f (* 100.0 (double x)) 1) "%"))   ; {x:.1%}
(defn- fmt-pct0 [x] (str (fmt-f (* 100.0 (double x)) 0) "%")) ; {x:.0%}

(defn- fmt-g
  [v]
  (let [d (double v)]
    (if (and (== d (Math/rint d)) (< (Math/abs d) 1e15))
      (str (long d))
      (let [s (format "%.6g" d)]
        (if (str/includes? s ".")
          (-> s (str/replace #"0+$" "") (str/replace #"\.$" ""))
          s)))))

(defn- lstrip-colon [s] (if (and (string? s) (str/starts-with? s ":")) (subs s 1) s))

(defn load-detections
  "[f for f in forms if isinstance(f, dict) and ':detect/unit' in f]."
  [text]
  (->> (analyze/read-edn text)
       (filter #(and (map? %) (contains? % ":detect/unit")))))

(defn inspection-request
  "Build a vision-inspection request for the highest-scrap station (the quality_target)."
  ([stations res] (inspection-request stations res nil))
  ([stations res detections]
   (let [target (get-in res ["_recommend" "quality_target" "station"])
         watch0 (when (seq detections)
                  (sort (distinct (->> detections
                                       (filter #(and (= (get % ":detect/station") target)
                                                     (not= (get % ":detect/class") NON-DEFECT-CLASS)))
                                       (map #(get % ":detect/class"))))))
         watch (if (seq watch0) (vec watch0) [":weld-porosity" ":spatter" ":misalignment"])
         scrap-rate (get-in res [target "scrap_rate"])]
     {"station" target
      "label" (get-in stations [target ":station/label"] target)
      "defect_classes" watch
      "sample_rate" (if (>= scrap-rate 0.05) 1.0 0.2)
      "routed_to" "actor:manako"
      "detector" "manako-yolo26-weld-defect-head"
      "constraints" MANAKO-CONSTRAINTS
      "reason_scrap_rate" scrap-rate})))

(defn reconcile
  "Reduce manako detections to a per-station defect-class Pareto + scan-cycle cross-check.
  by-station + defect_classes are insertion-ordered (defaultdict parity) via ::order metadata."
  ([detections] (reconcile detections nil))
  ([detections res]
   (let [;; fold detections into an insertion-ordered by-station accumulator
         by-station
         (reduce
          (fn [acc d]
            (let [st (get d ":detect/station")
                  a (get acc st {"inspected" 0 "defect_classes" ^{::dorder []} {}
                                 "scrap" 0 "rework" 0 "passed" 0})
                  v (get d ":detect/verdict")
                  cls (get d ":detect/class")
                  a (update a "inspected" inc)
                  a (cond (= v SCRAP) (update a "scrap" inc)
                          (= v REWORK) (update a "rework" inc)
                          (= v PASS) (update a "passed" inc)
                          :else a)
                  a (if (and cls (not= cls NON-DEFECT-CLASS))
                      (let [dc (get a "defect_classes")
                            had? (contains? dc cls)
                            dc' (update dc cls (fnil inc 0))
                            dc' (if had? (with-meta dc' (meta dc))
                                    (with-meta dc' (update (meta dc) ::dorder conj cls)))]
                        (assoc a "defect_classes" dc'))
                      a)]
              (-> acc
                  (assoc st a)
                  (vary-meta update ::sorder (fn [o] (if (contains? acc st) o (conj (or o []) st)))))))
          ^{::sorder []} {}
          detections)
         sorder (or (::sorder (meta by-station)) (keys by-station))]
     (reduce
      (fn [out st]
        (let [a (get by-station st)
              dc (get a "defect_classes")
              dorder (or (::dorder (meta dc)) (keys dc))
              ;; sorted(items, key=lambda kv: (-count, class))
              pareto (vec (sort-by (fn [[cls n]] [(- n) cls])
                                   (map (fn [k] [k (get dc k)]) dorder)))
              rec0 {"inspected" (get a "inspected") "scrap" (get a "scrap")
                    "rework" (get a "rework") "passed" (get a "passed")
                    "defect_pareto" pareto
                    "top_defect" (when (seq pareto) (first (first pareto)))}
              rec (if (and res (contains? res st))
                    (assoc rec0
                           "scancycle_scrap" (get-in res [st "scrap"])
                           ;; Python `a["scrap"] == res[st]["scrap"]` is numeric == (2 == 2.0
                           ;; is True); Clojure `=` is value-type-strict, so use `==`.
                           "scrap_agrees" (== (get a "scrap") (get-in res [st "scrap"])))
                    rec0)]
          (-> out
              (assoc st rec)
              (vary-meta update ::oorder (fnil conj []) st))))
      ^{::oorder []} {}
      sorder))))

(defn- rec-order [rec] (or (::oorder (meta rec)) (keys rec)))

(defn report-md
  [stations req rec]
  (let [L (transient [])
        P (fn [s] (conj! L s))]
    (P "# itonami 営み — R2 vision-inspection hand-off\n")
    (P (str "> **G1** inspection INFORMS, never auto-rejects/actuates. **G2** object-only — "
            "the inspected entity is a line PART, never a person (manako is on-device, "
            "no-biometric, no person-reID — ADR-2606034800). **G3** detector outputs are "
            "disclosed facts, not itonami verdicts.\n"))

    (P "\n## Inspection request → manako\n")
    (P (str "- **station**: " (get req "label") " (scrap-rate " (fmt-pct (get req "reason_scrap_rate")) ")"))
    (P (str "- **detector**: " (get req "detector") " · sample " (fmt-pct0 (get req "sample_rate"))))
    (P (str "- **watch classes**: " (str/join ", " (map lstrip-colon (get req "defect_classes")))))
    (P (str "- **constraints**: " (str/join "; " (get req "constraints"))))

    (P "\n## Detection reconciliation (root-cause hint)\n")
    (P "| station | inspected | scrap | rework | top defect | scan-cycle agrees |")
    (P "|---|---:|---:|---:|---|:--:|")
    (doseq [st (rec-order rec)]
      (let [r (get rec st)
            agree (if (get r "scrap_agrees") "✓" (if (contains? r "scrap_agrees") "✗" "n/a"))
            top (or (get r "top_defect") "—")]
        (P (str "| " (get-in stations [st ":station/label"] st) " | " (get r "inspected") " | "
                (get r "scrap") " | " (get r "rework") " | " (lstrip-colon (str top)) " | " agree " |"))))

    (P "\n### Defect Pareto (worst station)\n")
    (when (contains? rec (get req "station"))
      (doseq [[cls n] (get-in rec [(get req "station") "defect_pareto"])]
        (P (str "- " (lstrip-colon cls) ": " n))))

    (P (str "\n---\n_itonami 営み R2 · ADR-2606082300 · vision-informs-not-actuates · "
            "object-only (no person) · root-cause hint, not a worker verdict._\n"))
    (str/join "\n" (persistent! L))))

(defn emit
  "Transient EAVT inspection datoms (computed on read, never durable — G3)."
  ([req rec] (emit req rec 1))
  ([req rec tx]
   (let [L (transient [";; itonami R2 vision hand-off — TRANSIENT (:bond/is-transient true), G1/G3." "["])
         st (get req "station")]
     (conj! L (str "[" st " :ops/inspect-sample-rate " (fmt-g (get req "sample_rate"))
                   " " tx " :derived] ;; :bond/is-transient true"))
     (conj! L (str "[" st " :ops/inspect-routed-to :actor.manako " tx " :derived] ;; :bond/is-transient true"))
     (when (and (contains? rec st) (get-in rec [st "top_defect"]))
       (conj! L (str "[" st " :quality/top-defect " (get-in rec [st "top_defect"])
                     " " tx " :derived] ;; :bond/is-transient true"))
       (conj! L (str "[" st " :quality/scrap-agrees "
                     (if (get-in rec [st "scrap_agrees"]) "true" "false")
                     " " tx " :derived] ;; :bond/is-transient true")))
     (conj! L "]")
     (str (str/join "\n" (persistent! L)) "\n"))))

#?(:clj
   (defn -main
     [& argv]
     (let [argv (vec argv)
           here (-> *file* io/file .getParentFile .getParentFile)
           seed (if (and (seq argv) (not (str/starts-with? (first argv) "--")))
                  (io/file (first argv))
                  (io/file here "data" "seed-factory-ops.kotoba.edn"))
           det-path (if (some #{"--detections"} argv)
                      (io/file (nth argv (inc (.indexOf argv "--detections"))))
                      (io/file here "data" "seed-vision-detections.kotoba.edn"))
           outdir (if (some #{"--out"} argv)
                    (io/file (nth argv (inc (.indexOf argv "--out"))))
                    (io/file here "out"))
           tx (if (some #{"--tx"} argv)
                (Long/parseLong (nth argv (inc (.indexOf argv "--tx")))) 1)
           {:keys [stations ticks]} (analyze/load-file* seed)
           res (analyze/analyze stations ticks)
           detections (load-detections (slurp det-path))
           req (inspection-request stations res detections)
           rec (reconcile detections res)]
       (.mkdirs outdir)
       (spit (io/file outdir "vision-inspection.md") (report-md stations req rec))
       (spit (io/file outdir "itonami-inspect.kotoba.edn") (emit req rec tx))
       (println (str "itonami R2: inspect " (get req "station") " (sample " (fmt-pct0 (get req "sample_rate"))
                     ") → manako · top defect " (get-in rec [(get req "station") "top_defect"]) " → " outdir))
       0)))
