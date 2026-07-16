;; madomori 窓守 — end-to-end façade window-cleaning analyzer (orchestrator).
;;
;; Loads the building seed and runs the R0 sim pipeline:
;;   1. plan the façade coverage path (boustrophedon) over the pane grid + budget;
;;   2. assess the wind/sway safety envelope + fall-arrest redundancy (★ G5);
;;   3. assess the suction adhesion factor-of-safety on the surface (★ G7).
;;
;; The report carries a top-level :go? = (envelope permitted? AND adhesion safe?):
;; a descent is planned only if BOTH safety gates pass.
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142020 (madomori R0).
(ns madomori.methods.analyze
  (:require [clojure.edn :as edn]
            [madomori.methods.facade-path :as fp]
            [madomori.methods.wind-envelope :as we]
            [madomori.methods.adhesion :as ad]
            [madomori.methods.multi-face :as mf]
            [madomori.methods.anchor :as anc]
            [madomori.methods.water-recovery :as wr]
            [madomori.methods.handoff :as ho]))

(defn- tx-data?
  "True if content is a datomic/datascript tx-data vector (one entity map with
   :db/id), i.e. data/facade.edn's post-datomize shape."
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn- unblob
  "pr-str'd non-scalar attribute values (nested maps / vectors-of-maps) come back
   as strings from the tx-data shape; parse them back to data. Leaves live scalars
   (and anything that fails to parse as a collection) untouched."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Un-namespace + un-blob a tx-data entity back into the original bare seed map
   (:facade/face -> :face etc.) so every downstream `(:keys [...])` destructure
   in this ns and in facade-path/wind-envelope/adhesion/multi-face/anchor/
   water-recovery/handoff keeps working unchanged."
  [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(defn load-seed
  "Read the building façade EDN seed into a Clojure map. Accepts either the
   legacy bare map or the datomic/datascript tx-data vector (post-datomize)
   shape."
  [path]
  (let [content (edn/read-string (slurp path))]
    (if (tx-data? content)
      (reconstitute-entity content)
      content)))

(defn run
  "Run the full R0 analysis over a loaded seed map. Returns a report map."
  [seed]
  (let [face (:face seed)
        robot (:robot seed)
        wind (:wind seed)
        anchors (:anchors seed)
        ;; 1. coverage path + budget
        coverage (fp/plan face robot)
        ;; 2. wind/sway safety envelope (★ G5)
        envelope (we/assess face wind robot anchors)
        ;; 3. adhesion factor-of-safety (★ G7)
        adhesion (ad/assess robot (:surface face))]
    {:facility (:id (:facility seed))
     :face {:id (:id face) :rows (:rows face) :cols (:cols face)
            :panes (* (:rows face) (:cols face)) :surface (:surface face)}
     :coverage coverage
     :envelope envelope
     :adhesion adhesion
     ;; a descent is planned only if BOTH safety gates pass
     :go? (boolean (and (:permitted? envelope) (:safe? adhesion)))}))

(defn run-day
  "Full façade-CAMPAIGN-DAY pipeline — threads a realistic façade job through EVERY
   domain method module so they actually compose (R1 integration), not just coexist:
     multi-face route (multi_face) → per-face coverage (facade_path) →
     wind/fall safety (wind_envelope) → adhesion check (adhesion) →
     anchor-rig check (anchor) → water-recovery balance (water_recovery) →
     defect→repair handoff (handoff).
   Returns the base `run` report plus `:pipeline` (per-stage summary), `:methods`
   (the set of method modules exercised), and `:day` (the artifacts). Stages with no
   seed fixture are recorded `:skipped` rather than failing.

   ★ G3 — every artifact in `:day` is geometric + structural ONLY (face grids,
   distances, litres, kN, a structural defect descriptor); NO imagery / person /
   interior / biometric / camera data is representable here. Asserted in tests."
  [seed]
  (let [base    (run seed)
        face    (:face seed)
        robot   (:robot seed)
        wind    (:wind seed)
        anchors (:anchors seed)
        stage   (fn [m summary] {:method m :summary summary})
        ;; 1. multi_face — sequence the building's elevations (reposition-minimal)
        campaign (when (and (seq (:faces seed)) (:pane seed))
                   (mf/campaign-coverage (:faces seed) (:pane seed) [0 0]))
        ;; 2. facade_path — per-face coverage of the lead (south) elevation
        coverage (fp/plan face robot)
        ;; 3. wind_envelope — wind/sway + fall-arrest redundancy (★ G5, non-raising)
        envelope (we/assess face wind robot anchors)
        ;; 4. adhesion — suction factor-of-safety on the surface (★ G7, non-raising)
        adhesion (ad/assess robot (:surface face))
        ;; 5. anchor — per-anchor descent-load rig check (★ G5, non-raising)
        working-load (:working-load-kn seed)
        rig (when (and (seq (:rig-anchors seed)) working-load)
              (anc/assess (:rig-anchors seed) working-load anc/default-fos))
        ;; 6. water_recovery — runoff/detergent capture balance (★ G2, non-raising)
        water (when (:water seed) (wr/water-balance (:water seed)))
        ;; 7. handoff — a detected structural defect → tatekata repair-order intent
        defect (:defect seed)
        repair (when defect (ho/repair-handoff defect))
        pipeline (cond-> []
                   campaign (conj (stage "multi_face"
                                         (format "%d face(s), reposition %.1fm"
                                                 (count (:order campaign))
                                                 (:reposition-distance campaign))))
                   true     (conj (stage "facade_path"
                                         (str (get-in coverage [:coverage :total]) " panes, "
                                              (if (get-in coverage [:coverage :complete?])
                                                "complete" "INCOMPLETE"))))
                   true     (conj (stage "wind_envelope"
                                         (format "peak %.1fm/s permitted? %s"
                                                 (:peak-wind-mps envelope)
                                                 (:permitted? envelope))))
                   true     (conj (stage "adhesion"
                                         (format "FoS %.2f safe? %s"
                                                 (:factor-of-safety adhesion)
                                                 (:safe? adhesion))))
                   rig      (conj (stage "anchor"
                                         (format "%d/%d adequate rig-ok? %s"
                                                 (:adequate-anchors rig)
                                                 (:total-anchors rig)
                                                 (:rig-ok? rig))))
                   water    (conj (stage "water_recovery"
                                         (format "recovery %.0f%% compliant? %s"
                                                 (* 100.0 (:recovery-fraction water))
                                                 (:compliant? water))))
                   repair   (conj (stage "handoff"
                                         (str "1 repair-order → " (:to-actor repair)))))]
    (assoc base
           :pipeline pipeline
           :methods (set (map :method pipeline))
           :day {:campaign campaign
                 :coverage coverage
                 :envelope envelope
                 :adhesion adhesion
                 :rig rig
                 :water water
                 :handoff repair})))

(defn report-day-str
  "Human-readable full-campaign-day pipeline report."
  [res]
  (str ";; madomori 窓守 — full façade-campaign-DAY pipeline (R1 integration)\n"
       "methods exercised: " (pr-str (sort (:methods res))) "\n"
       (apply str (map (fn [s] (str "  • " (:method s) " — " (:summary s) "\n"))
                       (:pipeline res)))))

(defn report-str
  "Human-readable report (for out/ and Murakumo narration input, G6)."
  [res]
  (str ";; madomori 窓守 — façade window-cleaning R0 analysis\n"
       "face: " (get-in res [:face :id]) " "
       (get-in res [:face :rows]) "×" (get-in res [:face :cols]) " panes ("
       (get-in res [:face :panes]) " total, " (name (get-in res [:face :surface])) ")\n"
       "coverage complete?: " (get-in res [:coverage :coverage :complete?]) "\n"
       "path length (m): " (format "%.1f" (get-in res [:coverage :length-m])) "\n"
       "water (L): " (format "%.1f" (get-in res [:coverage :budget :water-l]))
       "  agent (mL): " (format "%.1f" (get-in res [:coverage :budget :agent-ml])) "\n"
       "peak wind (m/s): " (format "%.1f" (get-in res [:envelope :peak-wind-mps]))
       " / stop " (format "%.1f" (get-in res [:envelope :stop-threshold-mps])) "\n"
       "sway amplitude (m): " (format "%.3f" (get-in res [:envelope :sway-amplitude-m])) "\n"
       "fall-arrest redundant?: " (get-in res [:envelope :fall-arrest-redundant?])
       " (" (get-in res [:envelope :independent-anchors]) " anchors)\n"
       "wind work-permitted?: " (get-in res [:envelope :permitted?]) "\n"
       "adhesion FoS: " (format "%.2f" (get-in res [:adhesion :factor-of-safety]))
       " / required " (format "%.2f" (get-in res [:adhesion :required-fos]))
       " → safe?: " (get-in res [:adhesion :safe?]) "\n"
       "GO (both gates pass)?: " (:go? res) "\n"))

(defn -main [& args]
  (let [path (or (first args) "20-actors/madomori/data/facade.edn")
        seed (load-seed path)
        res (run-day seed)]
    (print (report-str res))
    (print (report-day-str res))
    (flush)))
