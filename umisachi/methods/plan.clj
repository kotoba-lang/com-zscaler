#!/usr/bin/env bb
;; umisachi 海幸 — depletion → RESTORATION mission planner (composes the analyze core).
(ns umisachi.methods.plan
  "plan.clj — umisachi restoration mission planner (ADR-2606074200).

  The actionable form of umisachi's 'routed to RESTORATION' mandate (the watatsuna plan pattern,
  adapted to marine bounty). Reads the marine-bounty graph, computes the edge-primary
  nourishment/depletion surfaces (via analyze), and emits a RESTORATION mission plan:

    :protect-rebuild        for STOCK nodes bearing the most depletion → route to inochi/ossekai
                            (rebuild biomass / protect the bounty)
    :open-access            for ENCLOSED/PROPRIETARY MARKET nodes bearing depletion → route to
                            okaimono/ossekai (widen equitable provisioning access)
    :route-to-accountability for the top depletion-OUT holders (the 取-holders gating the bounty,
                            pressures) → route to danjo/tsumugi/kabuto (the power-mirror lineage)

  CONSTITUTIONAL (G1 + mitsuho N1): every recommendation is PROTECT / REBUILD / OPEN / ROUTE-TO-
  ACCOUNTABILITY. There is NO catch / harvest / interdiction output by construction — the plan can
  only ADD restoration & equitable access; it is NEVER a catch-target list.

  Run:  bb --classpath 20-actors 20-actors/umisachi/methods/plan.clj"
  (:require [umisachi.methods.analyze :as a]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private this-file *file*)
(defn- actor-root [] (-> this-file io/file .getAbsoluteFile .getParentFile .getParentFile))

;; restoration sibling routes (the 'umisachi knows → siblings act' link; recommend-only)
(def restore-routes ["inochi" "ossekai"])
(def open-routes ["okaimono" "ossekai"])
(def accountability-routes ["danjo" "tsumugi" "kabuto"])
(def enclosed-access #{:proprietary :enclosed})
(def TOP-HOLDERS 5)

(defn- r2 [x] (/ (Math/round (* (double x) 100.0)) 100.0))
(defn- last-seg [s] (last (str/split (str s) #"\.")))

(defn build-plan
  "Returns the vector of :plan/* recommendation maps (restoration-only by construction).
  `nodes` = id→node map, `res` = analyze/analyze {:nourishment :depletion :depletion-out}."
  [nodes res]
  (let [dep (sort-by (juxt (comp - val) (comp str key)) (:depletion res))
        out (sort-by (juxt (comp - val) (comp str key)) (:depletion-out res))
        label (fn [nid] (get-in nodes [nid :organism/label] (str nid)))
        protect (for [[nid load] dep :when (= :stock (get-in nodes [nid :organism/kind]))]
                  {:plan/id (str "plan.protect." (last-seg nid))
                   :plan/kind :protect-rebuild
                   :plan/target nid
                   :plan/priority (r2 load)
                   :plan/rationale (str (label nid) " bears disclosed depletion load " (r2 load)
                                        " — protect + rebuild the stock; never a catch-target.")
                   :plan/routes restore-routes
                   :plan/g1-note "PROTECT/REBUILD only; never a catch-target or harvest instruction (G1)."})
        open (for [[nid load] dep
                   :when (and (= :market (get-in nodes [nid :organism/kind]))
                              (enclosed-access (get-in nodes [nid :market/access])))]
               {:plan/id (str "plan.open." (last-seg nid))
                :plan/kind :open-access
                :plan/target nid
                :plan/priority (r2 load)
                :plan/rationale (str (label nid) " is an enclosed/proprietary channel bearing load "
                                     (r2 load) " — widen equitable provisioning access.")
                :plan/routes open-routes
                :plan/g1-note "OPEN access only; widen the commons, never enclose or target (G1)."})
        account (for [[nid load] (take TOP-HOLDERS out)]
                  {:plan/id (str "plan.account." (last-seg nid))
                   :plan/kind :route-to-accountability
                   :plan/target nid
                   :plan/priority (r2 load)
                   :plan/rationale (str (label nid) " gates the bounty (outbound depletion " (r2 load)
                                        ") — route to power-mirror accountability, not interdiction.")
                   :plan/routes accountability-routes
                   :plan/g1-note "ROUTE-TO-ACCOUNTABILITY only (danjo/tsumugi/kabuto); never interdiction (G1)."})]
    (vec (concat protect open account))))

(defn render-edn [recs]
  (str/join
   "\n"
   (concat
    [";; umisachi 海幸 — restoration mission plan (GENERATED). :plan/* recommendations."
     ";; G1/N1: protect + rebuild + open + route-to-accountability ONLY. Never a catch-target."
     ";; ADR-2606074200. DO NOT hand-edit."
     "["]
    (map #(str " " (pr-str %)) recs)
    ["]" ""])))

(defn render-md [recs]
  (str/join
   "\n"
   (concat
    ["# umisachi 海幸 — marine-bounty RESTORATION plan" ""
     (str "> ADR-2606074200 · **protect + rebuild + open + route-to-accountability ONLY** (G1/N1). "
          "No catch / harvest / interdiction output by construction. A restoration map, never a "
          "catch-target list.") ""
     (str "- recommendations: **" (count recs) "**") ""]
    (mapcat
     (fn [[kind title]]
       (let [group (filter #(= (:plan/kind %) kind) recs)]
         (when (seq group)
           (concat
            [(str "## " title) ""]
            (mapcat
             (fn [r]
               [(str "- **" (:plan/target r) "** _(priority " (:plan/priority r) ")_ — " (:plan/rationale r))
                (str "  - routes: `" (str/join ", " (:plan/routes r)) "` · " (:plan/g1-note r))])
             (sort-by (juxt #(- (:plan/priority %)) :plan/id) group))
            [""]))))
     [[:protect-rebuild "Protect & rebuild depleted stocks"]
      [:open-access "Open enclosed provisioning channels"]
      [:route-to-accountability "Route 取-holders to accountability"]])
    ["---"
     "*Generated by `umisachi/methods/plan.clj`. HONEST: R1 design-only — recommendations over a "
     "bounded `:representative` seed; no live tasking; real action is sibling-actor + operator gated.*"
     ""])))

(defn main [& argv]
  (let [args (vec argv)
        out-idx (.indexOf args "--out")
        out-val (when (>= out-idx 0) (nth args (inc out-idx)))
        out (if out-val (io/file out-val) (io/file (actor-root) "out"))
        graph (or (first (remove #(or (str/starts-with? % "--") (= % out-val)) args))
                  (str (io/file (actor-root) "data" "seed-umisachi-graph.kotoba.edn")))
        [nodes edges] (a/load-file graph)
        res (a/analyze nodes edges)
        recs (build-plan nodes res)
        by (fn [k] (count (filter #(= (:plan/kind %) k) recs)))]
    (.mkdirs out)
    (spit (io/file out "restoration-plan.md") (render-md recs))
    (spit (io/file out "restoration-plan.kotoba.edn") (render-edn recs))
    (println (format "umisachi plan: %d recommendations (%d protect-rebuild · %d open-access · %d route-to-accountability)"
                     (count recs) (by :protect-rebuild) (by :open-access) (by :route-to-accountability)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply main *command-line-args*))
