#!/usr/bin/env bb
;; 撓 tawami — analyze → datoms → coverage (clj-native, pure stdlib).
(ns tawami.methods.analyze
  "撓 tawami — the Proof-of-Flexibility analytical core (Energy Order Protocol).

  OBSERVATION ONLY. Given a set of flexibility ASSETS, compute the value of each
  asset's ABILITY TO BEND a future energy flow in time:
    * responsiveness   (how fast it can respond — faster is worth more),
    * energy-capacity   (shiftable-kw × duration),
    * shiftability     (how wide its time-shift window is), and
    * flex-value = energy-capacity × availability × responsiveness × (0.5 + 0.5·shiftability).
  Each asset is tiered (:fast-flex / :mid-flex / :slow-flex) and assigned its
  best-use mio flow-class. Emits append-only EAVT datoms + a markdown FLEXIBILITY
  MAP and the org-wide dispatchable-flexibility total.

  Hard invariants (proven by tests):
    G1  a flexibility MAP, never a dispatch order — no :dispatch / :curtail-order
        attribute is computed or emitted (hikari actuates under Council gate).
    G2  aggregate-first — no :tawami.person/* load profile exists.
    G3  no :trade / :signal — flexibility is observed, never traded."
  (:require [clojure.string :as str]
            [tawami.methods.tawami-edn :as te]))

;; ── pure analytics ───────────────────────────────────────────────────────────

(defn responsiveness
  "0.3..1.0 from the response time (minutes). Faster response = more valuable."
  [response-time-min]
  (let [rt (double (or response-time-min 9999))]
    (cond (<= rt 1) 1.0 (<= rt 10) 0.9 (<= rt 30) 0.7 (<= rt 60) 0.5 :else 0.3)))

(defn energy-capacity-kwh
  "Shiftable energy = shiftable-kw × duration (h)."
  [a]
  (* (double (or (:shiftable-kw a) 0)) (/ (double (or (:duration-min a) 0)) 60.0)))

(defn shiftability
  "0..1 — how wide the time-shift window is (advance + defer), capped at 24 h."
  [a]
  (min 1.0 (/ (+ (double (or (:advance-window-h a) 0))
                 (double (or (:defer-window-h a) 0)))
              24.0)))

(defn flex-value
  "The value of the asset's flexibility (kWh-equiv, time-shift-weighted)."
  [a]
  (* (energy-capacity-kwh a)
     (double (or (:availability a) 0))
     (responsiveness (:response-time-min a))
     (+ 0.5 (* 0.5 (shiftability a)))))

(defn tier
  [a]
  (let [r (responsiveness (:response-time-min a))]
    (cond (>= r 0.9) :fast-flex (>= r 0.5) :mid-flex :else :slow-flex)))

(def best-use
  "Recommended mio flow-class per resource class (where this flexibility helps)."
  {:battery :peak-shave
   :heat-pump :peak-shave
   :ev-fleet :renewable-absorb
   :cold-store :renewable-absorb
   :datacenter :compute-routing
   :industrial-process :flexibility})

(defn analyze-asset
  "Per-asset derived observation map (string-keyed, agent-facing)."
  [a]
  {"id" (:id a)
   "name" (:name a)
   "resource_class" (:resource-class a)
   "responsiveness" (responsiveness (:response-time-min a))
   "energy_capacity_kwh" (energy-capacity-kwh a)
   "shiftability" (shiftability a)
   "flex_value" (flex-value a)
   "tier" (tier a)
   "best_use" (get best-use (:resource-class a) :flexibility)
   "sourcing" (or (:sourcing a) :representative)
   "source" (:source a)})

(defn analyze
  "Full analysis: per-asset rows + per-resource-class aggregates + org totals."
  [assets]
  (let [rows (mapv analyze-asset assets)
        by-class (group-by #(get % "resource_class") rows)
        classes (vec (for [[cls crows] (sort-by (comp name key) by-class)]
                       {"resource_class" cls
                        "count" (count crows)
                        "total_flex_value" (reduce + 0.0 (map #(get % "flex_value") crows))
                        "mean_responsiveness" (/ (reduce + 0.0 (map #(get % "responsiveness") crows))
                                                 (count crows))}))
        fast (filter #(= (get % "tier") :fast-flex) rows)]
    {"assets" rows
     "classes" classes
     "totals" {"asset_count" (count rows)
               ;; the org's dispatchable flexibility (kWh-equiv, time-shift-weighted)
               "total_flex_value" (reduce + 0.0 (map #(get % "flex_value") rows))
               ;; the grid-balancing-grade subtotal (sub-10-minute response)
               "fast_flex_value" (reduce + 0.0 (map #(get % "flex_value") fast))
               "fast_flex_count" (count fast)}}))

;; ── datom emission (append-only EAVT; every derived datom flagged) ───────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the derived observations. Every datom carries
  :tawami/derived + :tawami/sourcing; :authoritative rows add the cited :tawami/source.
  No :dispatch / :curtail-order / :trade / :signal attribute is ever emitted (G1/G3)."
  [{:strs [assets classes totals]}]
  (let [adatoms (mapcat
                 (fn [r]
                   (let [e (str "tawami-asset:" (get r "id"))
                         src (get r "source")]
                     (cond-> [(add e ":tawami.asset/name" (get r "name"))
                              (add e ":tawami.asset/resource-class" (str (get r "resource_class")))
                              (add e ":tawami.obs/responsiveness" (round3 (get r "responsiveness")))
                              (add e ":tawami.obs/energy-capacity-kwh" (round3 (get r "energy_capacity_kwh")))
                              (add e ":tawami.obs/shiftability" (round3 (get r "shiftability")))
                              (add e ":tawami.obs/flex-value" (round3 (get r "flex_value")))
                              (add e ":tawami.obs/tier" (str (get r "tier")))
                              (add e ":tawami.obs/best-use" (str (get r "best_use")))
                              (add e ":tawami/sourcing" (str (get r "sourcing")))
                              (add e ":tawami/derived" true)]
                       src (conj (add e ":tawami/source" src)))))
                 assets)
        kdatoms (mapcat
                 (fn [k]
                   (let [e (str "tawami-class:" (name (get k "resource_class")))]
                     [(add e ":tawami.class/asset-count" (get k "count"))
                      (add e ":tawami.class/total-flex-value" (round3 (get k "total_flex_value")))
                      (add e ":tawami.class/mean-responsiveness" (round3 (get k "mean_responsiveness")))
                      (add e ":tawami/derived" true)]))
                 classes)
        e "tawami-ledger:flex"
        ldatoms [(add e ":tawami.ledger/asset-count" (get totals "asset_count"))
                 (add e ":tawami.ledger/total-flex-value" (round3 (get totals "total_flex_value")))
                 (add e ":tawami.ledger/fast-flex-value" (round3 (get totals "fast_flex_value")))
                 (add e ":tawami.ledger/fast-flex-count" (get totals "fast_flex_count"))
                 (add e ":tawami/derived" true)]]
    (vec (concat adatoms kdatoms ldatoms))))

(defn render-datoms
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── coverage (which resource classes are mapped) ─────────────────────────────

(def ^:private universe
  {:battery 3 :ev-fleet 3 :cold-store 3 :datacenter 3 :heat-pump 3 :industrial-process 3})

(defn coverage
  [assets]
  (let [by-class (group-by :resource-class assets)
        rows (for [[cls target] (sort-by (comp name key) universe)
                   :let [have (count (get by-class cls []))]]
               {"resource_class" cls "have" have "target" target
                "gap" (max 0 (- target have))})]
    {"by_class" (vec rows)
     "total_have" (count assets)
     "total_target" (reduce + (vals universe))
     "total_gap" (reduce + (map #(get % "gap") rows))}))

;; ── markdown flexibility map (NOT a dispatch order) ──────────────────────────

(defn render-report
  [analysis coverage-map]
  (let [rows (->> (get analysis "assets") (sort-by #(- (get % "flex_value"))))
        cov (get coverage-map "by_class")
        totals (get analysis "totals")
        auth (count (filter #(= (get % "sourcing") :authoritative) rows))
        total (count rows)]
    (str
     "# 撓 tawami — FLEXIBILITY MAP (Proof of Flexibility)\n\n"
     "OBSERVATION ONLY. This is a **flexibility map, NEVER a dispatch order** and "
     "NEVER a per-person load profile (G1/G2). The value here is the ABILITY TO "
     "BEND a future energy flow in time — fast, sustained, time-shiftable, available "
     "capacity. A used flexibility becomes a 澪 mio flow-improvement claim (R1). "
     "Provenance: **" auth "/" total " assets :authoritative** (cited telemetry via "
     "operator-triggered G7 ingest); the remainder are :representative.\n\n"
     "## Org dispatchable flexibility\n\n"
     "- **total flex-value = " (round3 (get totals "total_flex_value")) " kWh-equiv**\n"
     "- fast-flex (grid-balancing grade, sub-10-min): " (round3 (get totals "fast_flex_value"))
     " kWh-equiv across " (get totals "fast_flex_count") " assets\n\n"
     "## Flexibility ledger (flex-value, highest first)\n\n"
     "| asset | class | responsiveness | capacity kWh | shiftability | flex-value | tier | best use |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r rows]
                 (str "| " (get r "name")
                      " | " (name (get r "resource_class"))
                      " | " (round3 (get r "responsiveness"))
                      " | " (round3 (get r "energy_capacity_kwh"))
                      " | " (round3 (get r "shiftability"))
                      " | " (round3 (get r "flex_value"))
                      " | " (name (get r "tier"))
                      " | " (name (get r "best_use")) " |")))
     "\n\n## Coverage (resource classes mapped)\n\n"
     "| resource class | have | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "resource_class")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_best-use → the 澪 mio flow-class this flexibility serves; hikari actuates "
     "under Council gate. tawami maps, never dispatches._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tawami/kotoba/seed.edn")
           rows (te/parse-edn (slurp seed))
           as (vec (filter #(= (:type %) :asset) rows))
           a (analyze as)
           cov (coverage as)]
       (println (render-report a cov))
       (println (str "-- " (count as) " assets, flex-value "
                     (round3 (get-in a ["totals" "total_flex_value"])) " kWh-equiv ("
                     (get-in a ["totals" "fast_flex_count"]) " fast), "
                     (get cov "total_gap") " gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
