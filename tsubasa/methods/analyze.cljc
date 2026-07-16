#!/usr/bin/env bb
;; tsubasa 翼 — analyze → datoms → coverage (clj-native, pure stdlib).
(ns tsubasa.methods.analyze
  "tsubasa 翼 — the flight-commons analytical core (ADR-2606072800).

  DISCOVERY ONLY. Given the route / fare / airport / carrier seed, compute the
  honest meta-search readout per O–D route:
    * TRUE total cost (fare + baggage, G4 — never the headline fare),
    * the cheapest / greenest / fastest option (emissions is FIRST-CLASS, G4),
    * carrier CONCENTRATION on the route (named-HHI over carrier presence) and a
      competition reading {:competitive :concentrated :monopoly}, and
    * the OPENING route: a concentrated/monopoly O–D is flagged :opening (surface
      alternatives), never punished and never a target-list.
  Emits append-only EAVT datoms (every derived datom :tsubasa/derived true +
  :tsubasa/sourcing) and a markdown COMPETITION / FARE map.

  Hard invariants (proven by tests):
    G1  no-affiliate-no-inflow — NO :commission / :affiliate / :merchant datom is
        ever computed or emitted (tsubasa is never merchant-of-record; member self-books).
    G3  anti-dark — NO :urgency / :scarcity / :price-will-rise datom exists.
    G4  emissions-honest — co2-kg is surfaced on every route (greenest is first-class).
    G5  no-person-tracking — analysis takes fares only; NO :searcher / :person datom
        is ever emitted (a search is stateless w.r.t. the searcher)."
  (:require [clojure.string :as str]))

;; ── pure helpers ──────────────────────────────────────────────────────────────

(defn total-minor
  "TRUE total cost a traveller pays: base fare + checked-bag fee (G4 honesty)."
  [f]
  (+ (long (or (:fare/fare-minor f) 0))
     (long (or (:fare/baggage-minor f) 0))))

(defn- co2 [f] (double (or (:fare/co2-kg f) 0.0)))
(defn- dur [f] (long (or (:fare/duration-min f) 0)))

(defn route-key
  "Canonical O–D key string, e.g. \"JFK-NRT\"."
  [f]
  (str (:fare/origin f) "-" (:fare/destination f)))

(defn carrier-hhi
  "Herfindahl–Hirschman index over carrier PRESENCE on a route, normalized 0..1.
  Shares = each carrier's fare-count / total fares on the route. 1.0 = one carrier
  (a monopoly route); → 0 as carriers proliferate evenly. A competition reading, not
  a verdict on any airline."
  [route-fares]
  (let [n (count route-fares)]
    (if (zero? n)
      0.0
      (let [by-carrier (frequencies (map :fare/carrier route-fares))
            shares (map #(/ (double %) n) (vals by-carrier))]
        (reduce + 0.0 (map #(* % %) shares))))))

(defn concentration
  "Competition reading from carrier count + HHI on the route."
  [carrier-count hhi]
  (cond
    (<= carrier-count 1) :monopoly
    (>= hhi 0.5)         :concentrated
    :else                :competitive))

(defn- route-of
  "Where the observation is routed: a concentrated/monopoly O–D → :opening (surface
  alternatives / encourage competition); otherwise :served. Never punishment."
  [conc]
  (if (= conc :competitive) :served :opening))

(defn analyze-route
  "Per-route derived observation map (string-keyed, agent-facing)."
  [[rk route-fares]]
  (let [carriers (sort (distinct (map :fare/carrier route-fares)))
        cc (count carriers)
        hhi (carrier-hhi route-fares)
        conc (concentration cc hhi)
        cheapest (apply min-key total-minor route-fares)
        greenest (apply min-key co2 route-fares)
        fastest (apply min-key dur route-fares)
        sourcing (or (some :fare/sourcing route-fares) :representative)]
    {"route" rk
     "origin" (:fare/origin (first route-fares))
     "destination" (:fare/destination (first route-fares))
     "carriers" (vec carriers)
     "carrier_count" cc
     "fare_count" (count route-fares)
     "carrier_hhi" hhi
     "concentration" conc
     "opening" (route-of conc)
     "cheapest_total_minor" (total-minor cheapest)
     "cheapest_carrier" (:fare/carrier cheapest)
     "greenest_co2_kg" (co2 greenest)
     "greenest_carrier" (:fare/carrier greenest)
     "fastest_min" (dur fastest)
     "fastest_carrier" (:fare/carrier fastest)
     "sourcing" sourcing}))

(defn green-premium
  "The explicit cost↔emissions tradeoff on a route: the GREEN PREMIUM a traveller pays to take the
  lowest-CO₂ fare instead of the cheapest, and the CO₂ it saves. `analyze-route` surfaces the
  cheapest and the greenest fares separately; this quantifies the gap BETWEEN them so the member can
  judge the trade for themselves (emissions-honest, G4 — neither the cost nor the emissions of going
  green is hidden; transparent, never a dark pattern, G3). premium-minor = greenest's TRUE total cost
  − cheapest's true total cost (≤ 0 when the greenest fare is also the cheapest — a win-win);
  co2-saved-kg = cheapest's CO₂ − greenest's CO₂. Takes a route's fares; returns
  {:cheapest-total-minor :greenest-total-minor :premium-minor :co2-saved-kg :green-is-cheapest?}."
  [route-fares]
  (let [cheapest (apply min-key total-minor route-fares)
        greenest (apply min-key co2 route-fares)
        premium  (- (total-minor greenest) (total-minor cheapest))]
    {:cheapest-total-minor (total-minor cheapest)
     :greenest-total-minor (total-minor greenest)
     :premium-minor premium
     :co2-saved-kg (- (co2 cheapest) (co2 greenest))
     :green-is-cheapest? (<= premium 0)}))

(defn analyze-carrier
  "Per-carrier coverage observation."
  [[carrier carrier-fares]]
  (let [routes (distinct (map route-key carrier-fares))]
    {"carrier" carrier
     "route_count" (count routes)
     "fare_count" (count carrier-fares)
     "mean_co2_kg" (/ (reduce + 0.0 (map co2 carrier-fares)) (count carrier-fares))}))

(defn analyze
  "Full analysis: per-route rows + per-carrier rows. `rows` is the raw seed; only
  :fare rows feed the route/carrier analytics."
  [rows]
  (let [fares (filter #(= (:type %) :fare) rows)
        by-route (->> fares (group-by route-key) (sort-by key))
        by-carrier (->> fares (group-by :fare/carrier) (sort-by key))]
    {"routes" (mapv analyze-route by-route)
     "carriers" (mapv analyze-carrier by-carrier)}))

;; ── datom emission (append-only EAVT; every derived datom flagged) ───────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS for the derived observations (autorun/kotoba append
  these to the ledger). Every datom carries :tsubasa/derived true + :tsubasa/sourcing.
  By CONSTRUCTION no :commission / :affiliate / :merchant (G1), no :urgency / :scarcity
  (G3), and no :searcher / :person (G5) attribute is ever emitted — the analysis has
  no such input and no such field."
  [{:strs [routes carriers]}]
  (let [rdatoms (mapcat
                 (fn [r]
                   (let [e (str "tsubasa-route:" (get r "route"))]
                     [(add e ":tsubasa.route/origin" (get r "origin"))
                      (add e ":tsubasa.route/destination" (get r "destination"))
                      (add e ":tsubasa.route/carrier-count" (get r "carrier_count"))
                      (add e ":tsubasa.obs/carrier-hhi" (round3 (get r "carrier_hhi")))
                      (add e ":tsubasa.obs/concentration" (str (get r "concentration")))
                      (add e ":tsubasa.obs/cheapest-total-minor" (get r "cheapest_total_minor"))
                      (add e ":tsubasa.obs/greenest-co2-kg" (round3 (get r "greenest_co2_kg")))
                      (add e ":tsubasa.obs/fastest-min" (get r "fastest_min"))
                      (add e ":tsubasa.obs/route" (str (get r "opening")))
                      (add e ":tsubasa/sourcing" (str (get r "sourcing")))
                      (add e ":tsubasa/derived" true)]))
                 routes)
        cdatoms (mapcat
                 (fn [c]
                   (let [e (str "tsubasa-carrier:" (get c "carrier"))]
                     [(add e ":tsubasa.carrier/route-count" (get c "route_count"))
                      (add e ":tsubasa.carrier/fare-count" (get c "fare_count"))
                      (add e ":tsubasa.carrier/mean-co2-kg" (round3 (get c "mean_co2_kg")))
                      (add e ":tsubasa/derived" true)]))
                 carriers)]
    (vec (concat rdatoms cdatoms))))

(defn render-datoms
  "EDN string of the derived-observation datoms (see `datoms`)."
  [analysis]
  (str "[\n " (str/join "\n " (map pr-str (datoms analysis))) "\n]\n"))

;; ── coverage (gap worklist) ──────────────────────────────────────────────────

(def ^:private region-universe
  "Representative count of well-served airports per world region (a coverage
  yardstick, NOT exhaustive). Drives the gap worklist each run."
  {:north-america 6 :south-america 4 :europe 8 :middle-east 3
   :east-asia 6 :south-asia 4 :southeast-asia 4 :oceania 3 :africa 4})

(def ^:private carrier-target
  "Representative count of major international carriers worth covering (yardstick)."
  40)

(defn coverage
  "Coverage of the airport region-universe + carrier universe by the seed, with a
  prioritized gap count per region. `rows` is the raw seed."
  [rows]
  (let [airports (filter #(= (:type %) :airport) rows)
        carriers (filter #(= (:type %) :carrier) rows)
        fares (filter #(= (:type %) :fare) rows)
        by-region (group-by :airport/region airports)
        region-rows (for [[region target] (sort-by (comp name key) region-universe)
                          :let [have (count (get by-region region []))]]
                      {"region" region "have" have "target" target
                       "gap" (max 0 (- target have))})]
    {"by_region" (vec region-rows)
     "airports_have" (count airports)
     "airports_target" (reduce + (vals region-universe))
     "airports_gap" (reduce + (map #(get % "gap") region-rows))
     "carriers_have" (count carriers)
     "carriers_target" carrier-target
     "carriers_gap" (max 0 (- carrier-target (count carriers)))
     "routes_have" (count (distinct (map route-key fares)))
     "fares_have" (count fares)}))

;; ── markdown competition / fare map (NOT a target-list, NOT a paid rank) ──────

(defn render-report
  [analysis coverage-map]
  (let [routes (->> (get analysis "routes")
                    (sort-by #(- (get % "carrier_hhi"))))
        cov (get coverage-map "by_region")
        opening (count (filter #(= (get % "opening") :opening) routes))
        total (count routes)]
    (str
     "# tsubasa 翼 — flight-route COMPETITION & FARE map\n\n"
     "DISCOVERY ONLY. This is an **honest fare map + competition reading, NEVER a "
     "paid ranking and NEVER a target-list** (the Skyscanner inversion, ADR-2606072800). "
     "Every onward link is affiliate-stripped and the member SELF-BOOKS on the airline's "
     "own site — tsubasa takes no commission (G1). Fares + emissions are DISCLOSED facts; "
     "CO₂ is surfaced on every route as a first-class axis (G4). A search is stateless "
     "w.r.t. the searcher (G5). **" opening "/" total " routes flagged :opening** "
     "(concentrated/monopoly O–D where alternatives should be surfaced).\n\n"
     "## Routes by carrier concentration (most concentrated first)\n\n"
     "| route | carriers | HHI | competition | cheapest (total¢) | greenest (kg CO₂) | fastest (min) | route |\n"
     "|---|---|---|---|---|---|---|---|\n"
     (str/join "\n"
               (for [r routes]
                 (str "| " (get r "route")
                      " | " (get r "carrier_count")
                      " | " (round3 (get r "carrier_hhi"))
                      " | " (name (get r "concentration"))
                      " | " (get r "cheapest_total_minor") " (" (get r "cheapest_carrier") ")"
                      " | " (round3 (get r "greenest_co2_kg")) " (" (get r "greenest_carrier") ")"
                      " | " (get r "fastest_min") " (" (get r "fastest_carrier") ")"
                      " | " (name (get r "opening")) " |")))
     "\n\n## Coverage (Wave 1, all-regions-thin)\n\n"
     "| region | airports | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "region")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_route → served (competitive) · opening (surface alternatives / encourage competition — never punishment, never a target-list)._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/tsubasa/data/seed-fares.kotoba.edn")
           rows (clojure.edn/read-string (slurp seed))
           a (analyze rows)
           cov (coverage rows)]
       (println (render-report a cov))
       (println (str "-- " (get cov "routes_have") " routes, "
                     (get cov "fares_have") " fares, "
                     (get cov "carriers_have") " carriers, "
                     (get cov "airports_have") " airports, "
                     (get cov "airports_gap") " airport-gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
