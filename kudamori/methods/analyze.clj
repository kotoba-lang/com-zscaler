;; kudamori 管守 — end-to-end sewer-cleaning analyzer (orchestrator).
;;
;; Loads the sewer-network seed and runs the R0 sim pipeline:
;;   1. confined-space ENTRY GATE (★ G5) — check the entry-manhole gas reading; if
;;      unsafe, model purge-to-entry (forced ventilation) and only then admit entry;
;;      an atmosphere that never passes leaves entry refused (no human, no robot).
;;   2. in-pipe NAVIGATION — diameter-fit-checked shortest route from the access
;;      manhole to the target segment, routing around other blocked segments.
;;   3. JETTING — pressure-safe (★ G7) hydro-jet of the target, with debris-removal
;;      estimate + water reuse balance (G2; residual effluent → mizuho).
;;
;; Pure Clojure, no deps → babashka-runnable AND kotoba-pywasm-portable.
;; Per ADR-2606142030 (kudamori R0).
(ns kudamori.methods.analyze
  (:require [clojure.edn :as edn]
            [kudamori.methods.atmosphere :as atm]
            [kudamori.methods.pipe-nav :as nav]
            [kudamori.methods.jetting :as jet]
            [kudamori.methods.inspection :as insp]
            [kudamori.methods.campaign :as camp]
            [kudamori.methods.rootcut :as rc]
            [kudamori.methods.relining :as rl]
            [kudamori.methods.handoff :as ho]))

(defn- already-tx-data?
  "True if `content` is already the datomic/datascript tx-data shape ([{...:db/id ...}]),
   e.g. after the edn-datomize wave transforms data/network.edn (Phase 4)."
  [content]
  (and (vector? content) (seq content) (map? (first content)) (contains? (first content) :db/id)))

(defn- unblob
  "Non-scalar attrs (nested maps / vectors-of-maps) are stored pr-str'd; parse them back."
  [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch Exception _ v))
    v))

(defn- reconstitute-entity
  "Undo the tx-data wrap: strip :db/id, strip the :kudamori.network/ namespace off every
   attr key, unblob pr-str'd values — recovers the original bare sewer-network-seed map
   value-equal to the pre-transform data/network.edn, so every downstream `(:segments
   seed)` / `(:robot seed)` / etc. lookup below (and in datom_emit.clj / test_kudamori.clj)
   is unchanged regardless of which shape is on disk."
  [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(defn load-seed
  "Read the sewer-network EDN seed into a Clojure map. Tolerates both the original
   bare-map shape and the datomic/datascript tx-data shape (see `already-tx-data?` /
   `reconstitute-entity`) — always returns the bare map."
  [path]
  (let [content (edn/read-string (slurp path))]
    (if (already-tx-data? content)
      (reconstitute-entity content)
      content)))

(defn- seg-by-id [segments sid] (first (filter #(= (:id %) sid) segments)))

(defn run
  "Run the full R0 analysis over a loaded seed map. Returns a report map.
   The entry gate comes FIRST: an atmosphere that cannot be made safe leaves
   :entry {:permitted? false …} and the downstream legs report :gated."
  [seed]
  (let [segments (:segments seed)
        robot (:robot seed)
        job (:job seed)
        reading (:gas-reading seed)
        blower (:blower seed)
        ;; 1. confined-space entry gate (★ G5)
        raw-ok (atm/entry-permitted? reading)
        purge (when-not raw-ok
                (atm/purge-to-entry reading (:air-changes-per-min blower) 60))
        permitted (or raw-ok (boolean (:entry-permitted? purge)))
        entry {:permitted? permitted
               :raw-safe? raw-ok
               :raw-hazards (atm/hazards reading)
               :purge (when purge (select-keys purge [:entry-permitted? :minutes :hazards]))}]
    (if-not permitted
      ;; entry refused — no navigation, no jetting (the human/robot stays out)
      {:entry entry :navigation :gated :jetting :gated}
      (let [nav-plan (nav/plan-nav robot segments (:access job) (:target-segment job))
            tseg (seg-by-id segments (:target-segment job))
            clean (jet/clean-segment tseg (:jet robot) (:debris-frac job) 30)]
        {:entry entry
         :navigation nav-plan
         :jetting clean}))))

(defn run-day
  "Full sewer-cleaning DAY pipeline — threads a realistic campaign day through EVERY
   domain method module so they actually compose end-to-end (R1 integration), not just
   coexist:
     inspect(inspection) → prioritize+tour(campaign, fed by inspection/to-campaign-input)
     → ★ atmosphere ENTRY GATE(atmosphere) → navigate(pipe_nav) → jet(jetting)
     → root-cut(rootcut) → reline(relining) → effluent handoff(handoff).
   Returns the base `run` report plus `:pipeline` (per-stage {:method :summary} vector),
   `:methods` (the set of domain modules exercised), and `:day` (the per-stage artifacts).
   Stages with no seed fixture are recorded `:skipped` rather than failing.

   ★ G5 invariant: the atmosphere entry gate stays a REAL gate in the pipeline — every
   confined-space entry re-checks atmosphere via atmosphere/assert-entry! (the campaign
   batches the route, never the gas gate). The seed's :entry-air fixture is a verified-safe
   reading so the happy path runs, but the gate call always fires; an unsafe reading would
   RAISE and refuse entry, never proceed."
  [seed]
  (let [base       (run seed)
        segments   (:segments seed)
        job        (:job seed)
        robot      (:robot seed)
        stage      (fn [m summary] {:method m :summary summary})
        ;; 1. INSPECT — grade the CCTV/sonde survey worst-first (inspection)
        survey-in  (:inspection-survey seed)
        graded     (when (seq survey-in) (insp/survey survey-in))
        ;; the seam: survey → campaign input shape (inspection/to-campaign-input)
        camp-in    (when graded (insp/to-campaign-input graded (:survey-meta seed {})))
        ;; 2. PRIORITIZE + TOUR — campaign fed by the inspection survey (real composition)
        campaign   (when (seq camp-in)
                     (camp/plan-campaign camp-in (:campaign-opts seed {})))
        ;; 3. ★ ATMOSPHERE ENTRY GATE — every confined-space entry re-checks (G5).
        ;;    assert-entry! RAISES on an unsafe reading; the safe :entry-air fixture passes.
        entry-air  (:entry-air seed)
        entry-gate (when entry-air
                     {:permitted? (atm/entry-permitted? entry-air)
                      :hazards    (atm/hazards entry-air)
                      :checked    (boolean (atm/assert-entry! entry-air))})
        ;; 4. NAVIGATE — diameter-fit-checked route to the target segment (pipe_nav)
        nav-plan   (when (and entry-gate (:permitted? entry-gate) job)
                     (nav/plan-nav robot segments (:access job) (:target-segment job)))
        ;; 5. JET — pressure-safe hydro-jet of the target (jetting)
        tseg       (when (and nav-plan job) (seg-by-id segments (:target-segment job)))
        clean      (when tseg
                     (jet/clean-segment tseg (:jet robot) (:debris-frac job) 30))
        ;; 6. ROOT-CUT — mechanical root cut at safe torque (rootcut, ★ G7)
        rootcut    (when (:root-intrusion seed)
                     (rc/plan-cut (:root-intrusion seed) {:id "cut-head-01"}))
        ;; 7. RELINE — trenchless CIPP reline, honest-refusal on a collapse-grade host (relining)
        reline     (when (:reline-defect seed)
                     (rl/plan-reline (:reline-defect seed)))
        ;; 8. EFFLUENT HANDOFF — cleaned segment → mizuho treatment intent (handoff, G9)
        handoff    (when clean
                     (ho/outbound-handoff
                      [{:segment-id (:segment clean)
                        :debris-m3  (:debris-removed-m3 clean)
                        :effluent-l (get-in clean [:water :effluent-l])}]))
        pipeline (cond-> []
                   graded     (conj (stage "inspection" (str (count graded) " segments graded worst-first")))
                   campaign   (conj (stage "campaign"   (str (count (:stops campaign)) " stops / "
                                                             (format "%.1fm tour" (:travel-m campaign)))))
                   entry-gate (conj (stage "atmosphere" (str "entry gate "
                                                             (if (:permitted? entry-gate) "PASS" "REFUSED")
                                                             " (re-checked, G5)")))
                   nav-plan   (conj (stage "pipe_nav"   (str (:hops nav-plan) " hops to " (:target nav-plan))))
                   clean      (conj (stage "jetting"    (format "%.3f m³ debris @ %.0f bar"
                                                               (:debris-removed-m3 clean) (:pressure-bar clean))))
                   rootcut    (conj (stage "rootcut"    (str (:passes-needed rootcut) " pass(es) @ "
                                                             (format "%.0f N·m" (:required-torque-nm rootcut)))))
                   reline     (conj (stage "relining"   (format "%.2fmm CIPP liner / %.0f min cure"
                                                               (:liner-thickness-mm reline) (:cure-time-min reline))))
                   handoff    (conj (stage "handoff"    (str (count handoff) " effluent handoff→mizuho"))))]
    (assoc base
           :pipeline pipeline
           :methods  (set (map :method pipeline))
           :day {:inspection graded :campaign campaign :atmosphere entry-gate
                 :navigation nav-plan :jetting clean :rootcut rootcut
                 :relining reline :handoff handoff})))

(defn report-day-str
  "Human-readable full-day pipeline report."
  [res]
  (str ";; kudamori 管守 — full sewer-cleaning DAY pipeline (R1 integration)\n"
       "methods exercised: " (pr-str (sort (:methods res))) "\n"
       (apply str (map (fn [s] (str "  • " (:method s) " — " (:summary s) "\n"))
                       (:pipeline res)))))

(defn report-str
  "Human-readable report (for out/ and Murakumo narration input, G6)."
  [res]
  (let [e (:entry res)]
    (str ";; kudamori 管守 — sewer-cleaning R0 analysis\n"
         "entry permitted: " (:permitted? e)
         (when-not (:raw-safe? e)
           (str " (after purge "
                (get-in e [:purge :minutes]) " min)"))
         "\n"
         (if (= :gated (:navigation res))
           "navigation: GATED (unsafe atmosphere — entry refused, G5)\n"
           (str "route hops: " (get-in res [:navigation :hops])
                "  target-blocked: " (get-in res [:navigation :target-blocked?]) "\n"))
         (if (= :gated (:jetting res))
           "jetting: GATED\n"
           (str "jet pressure (bar): " (format "%.0f" (get-in res [:jetting :pressure-bar]))
                " / rating " (format "%.0f" (get-in res [:jetting :rating-bar])) "\n"
                "debris removed (m³): " (format "%.3f" (get-in res [:jetting :debris-removed-m3])) "\n"
                "water reuse frac: " (format "%.2f" (get-in res [:jetting :water :reuse-frac]))
                "  effluent→mizuho (L): " (format "%.1f" (get-in res [:jetting :water :effluent-l])) "\n")))))

(defn -main [& args]
  (let [path (or (first args) "20-actors/kudamori/data/network.edn")
        seed (load-seed path)
        res  (run-day seed)]
    (print (report-str res))
    (print (report-day-str res))
    (flush)))
