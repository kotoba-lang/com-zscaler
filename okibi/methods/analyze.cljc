#!/usr/bin/env bb
;; 燠 okibi — analyze → datoms → coverage (clj-native, pure stdlib).
(ns okibi.methods.analyze
  "燠 okibi — the Thermal Matching Market core (Energy Order Protocol).

  OBSERVATION ONLY. Heat is LOCAL, so this matches waste-heat SOURCES to heat-demand
  SINKS under two hard physical gates:
    * the temperature cascade — a source must be hotter than the sink's required
      temperature by at least the approach temperature (it can only serve a sink it
      is hot enough for), and
    * distance — heat is not deliverable beyond max-distance-m.
  A feasible pair gets a match-quality (closer + more available = better); a greedy
  allocation by quality fills demand, leaving unmatched source SURPLUS and unmatched
  DEMAND visible. A realized match becomes a 澪 mio flow-improvement claim (R1).

  Hard invariants (proven by tests):
    G1  a matching MAP, never a dispatch order — no :okibi/dispatch attribute exists.
    G2  a match must pass the cascade + distance gates — an infeasible pair can NEVER
        become a match (no fabrication). A cooling LOAD is not a heat sink (the §1
        anti-pattern is unrepresentable — sinks are heat demands by construction)."
  (:require [clojure.string :as str]
            #?(:clj [okibi.methods.okibi-edn :as oe])))

;; ── match params + physics ───────────────────────────────────────────────────

(def approach-temp-c 5.0)   ;; source must exceed sink required temp by ≥ this
(def max-distance-m 5000.0) ;; heat is local — beyond this, undeliverable

(defn distance-m
  "Equirectangular approximation (good for local distances) between two nodes
  carrying :lat :lon (degrees)."
  [a b]
  (let [R 6371000.0
        lat1 (Math/toRadians (double (:lat a)))
        lat2 (Math/toRadians (double (:lat b)))
        dlat (- lat2 lat1)
        dlon (Math/toRadians (- (double (:lon b)) (double (:lon a))))
        x (* dlon (Math/cos (/ (+ lat1 lat2) 2.0)))]
    (* R (Math/sqrt (+ (* x x) (* dlat dlat))))))

(defn feasible?
  "True iff source can serve sink: temperature cascade AND within distance."
  [src sink]
  (and (>= (double (:temp-c src)) (+ (double (:temp-req-c sink)) approach-temp-c))
       (<= (distance-m src sink) max-distance-m)))

(defn match-quality
  "0..1 — closer + jointly more available = better."
  [src sink]
  (let [d (distance-m src sink)
        dist-factor (max 0.0 (- 1.0 (/ d max-distance-m)))
        avail (min (double (:availability src)) (double (:availability sink)))]
    (* dist-factor avail)))

(defn- eff-kw [x kw-key] (* (double (or (kw-key x) 0)) (double (or (:availability x) 0))))

(defn match-heat
  "Greedy allocation by match-quality. Returns
   {:matches [{:src :sink :matched-kw :quality :dist} ...]
    :src-cap {id remaining-kw} :sink-cap {id remaining-kw}} — caps are EFFECTIVE
   (kW × availability); remaining = surplus (sources) / unmet (sinks)."
  [sources sinks]
  (let [pairs (->> (for [s sources k sinks :when (feasible? s k)]
                     {:src (:id s) :sink (:id k)
                      :quality (match-quality s k) :dist (distance-m s k)})
                   (sort-by (juxt #(- (:quality %)) :src :sink)))
        init {:src-cap (into {} (map (fn [s] [(:id s) (eff-kw s :kw)]) sources))
              :sink-cap (into {} (map (fn [k] [(:id k) (eff-kw k :kw-demand)]) sinks))
              :matches []}]
    (reduce
     (fn [st p]
       (let [rs (get-in st [:src-cap (:src p)])
             rk (get-in st [:sink-cap (:sink p)])
             alloc (min rs rk)]
         (if (> alloc 1e-9)
           (-> st
               (update-in [:src-cap (:src p)] - alloc)
               (update-in [:sink-cap (:sink p)] - alloc)
               (update :matches conj (assoc p :matched-kw alloc)))
           st)))
     init pairs)))

;; ── analysis ─────────────────────────────────────────────────────────────────

(defn analyze
  [sources sinks]
  (let [{:keys [matches src-cap sink-cap]} (match-heat sources sinks)
        src-rows (mapv (fn [s]
                         (let [eff (eff-kw s :kw)
                               rem (get src-cap (:id s) eff)]
                           {"id" (:id s) "name" (:name s) "kind" :source
                            "source_class" (:source-class s) "temp_c" (:temp-c s)
                            "matched_kw" (- eff rem) "surplus_kw" rem
                            "sourcing" (or (:sourcing s) :representative) "source" (:source s)}))
                       sources)
        sink-rows (mapv (fn [k]
                          (let [eff (eff-kw k :kw-demand)
                                rem (get sink-cap (:id k) eff)
                                met (- eff rem)]
                            {"id" (:id k) "name" (:name k) "kind" :sink
                             "sink_class" (:sink-class k) "temp_req_c" (:temp-req-c k)
                             "met_kw" met "unmet_kw" rem
                             "coverage" (if (pos? eff) (/ met eff) 0.0)
                             "sourcing" (or (:sourcing k) :representative) "source" (:source k)}))
                        sinks)
        matched-kw (reduce + 0.0 (map :matched-kw matches))]
    {"sources" src-rows
     "sinks" sink-rows
     "matches" (mapv (fn [m] {"src" (:src m) "sink" (:sink m)
                              "matched_kw" (:matched-kw m) "quality" (:quality m)
                              "distance_m" (:dist m)}) matches)
     "totals" {"source_count" (count sources) "sink_count" (count sinks)
               "match_count" (count matches)
               ;; the org's matched thermal power — the ORDERED heat flow
               "matched_kw" matched-kw
               "unmatched_source_kw" (reduce + 0.0 (vals src-cap))
               "unmatched_demand_kw" (reduce + 0.0 (vals sink-cap))}}))

;; ── datom emission (append-only EAVT; every derived datom flagged) ───────────

(defn- add [e a v] [":db/add" e a v])
(defn- round3 [x] (/ (Math/round (* (double x) 1000.0)) 1000.0))

(defn datoms
  "Append-only EAVT datom VECTORS. Every datom carries :okibi/derived + :okibi/sourcing;
  :authoritative rows add the cited :okibi/source. No :okibi/dispatch / :trade / :signal
  attribute is ever emitted (G1/G2)."
  [{:strs [sources sinks matches totals]}]
  (let [sdatoms (mapcat
                 (fn [r]
                   (let [e (str "okibi-source:" (get r "id")) src (get r "source")]
                     (cond-> [(add e ":okibi.source/name" (get r "name"))
                              (add e ":okibi.source/class" (str (get r "source_class")))
                              (add e ":okibi.source/temp-c" (get r "temp_c"))
                              (add e ":okibi.obs/matched-kw" (round3 (get r "matched_kw")))
                              (add e ":okibi.obs/surplus-kw" (round3 (get r "surplus_kw")))
                              (add e ":okibi/sourcing" (str (get r "sourcing")))
                              (add e ":okibi/derived" true)]
                       src (conj (add e ":okibi/source" src)))))
                 sources)
        kdatoms (mapcat
                 (fn [r]
                   (let [e (str "okibi-sink:" (get r "id")) src (get r "source")]
                     (cond-> [(add e ":okibi.sink/name" (get r "name"))
                              (add e ":okibi.sink/class" (str (get r "sink_class")))
                              (add e ":okibi.sink/temp-req-c" (get r "temp_req_c"))
                              (add e ":okibi.obs/met-kw" (round3 (get r "met_kw")))
                              (add e ":okibi.obs/unmet-kw" (round3 (get r "unmet_kw")))
                              (add e ":okibi.obs/coverage" (round3 (get r "coverage")))
                              (add e ":okibi/sourcing" (str (get r "sourcing")))
                              (add e ":okibi/derived" true)]
                       src (conj (add e ":okibi/source" src)))))
                 sinks)
        mdatoms (mapcat
                 (fn [m]
                   (let [e (str "okibi-match:" (get m "src") "~" (get m "sink"))]
                     [(add e ":okibi.match/source" (get m "src"))
                      (add e ":okibi.match/sink" (get m "sink"))
                      (add e ":okibi.match/matched-kw" (round3 (get m "matched_kw")))
                      (add e ":okibi.match/quality" (round3 (get m "quality")))
                      (add e ":okibi.match/distance-m" (round3 (get m "distance_m")))
                      (add e ":okibi/derived" true)]))
                 matches)
        e "okibi-ledger:thermal"
        ldatoms [(add e ":okibi.ledger/matched-kw" (round3 (get totals "matched_kw")))
                 (add e ":okibi.ledger/match-count" (get totals "match_count"))
                 (add e ":okibi.ledger/unmatched-source-kw" (round3 (get totals "unmatched_source_kw")))
                 (add e ":okibi.ledger/unmatched-demand-kw" (round3 (get totals "unmatched_demand_kw")))
                 (add e ":okibi/derived" true)]]
    (vec (concat sdatoms kdatoms mdatoms ldatoms))))

(defn render-datoms
  [assessment]
  (str "[\n " (str/join "\n " (map pr-str (datoms assessment))) "\n]\n"))

;; ── coverage (source classes mapped) ─────────────────────────────────────────

(def ^:private universe
  {:datacenter 2 :food-plant 2 :geothermal 2 :sewage 2 :refrigeration 2 :industrial 2})

(defn coverage
  [sources]
  (let [by-class (group-by :source-class sources)
        rows (for [[cls target] (sort-by (comp name key) universe)
                   :let [have (count (get by-class cls []))]]
               {"source_class" cls "have" have "target" target
                "gap" (max 0 (- target have))})]
    {"by_class" (vec rows)
     "total_have" (count sources)
     "total_target" (reduce + (vals universe))
     "total_gap" (reduce + (map #(get % "gap") rows))}))

;; ── markdown thermal-matching map (NOT a dispatch order) ─────────────────────

(defn render-report
  [analysis coverage-map]
  (let [matches (->> (get analysis "matches") (sort-by #(- (get % "matched_kw"))))
        sinks (get analysis "sinks")
        totals (get analysis "totals")
        cov (get coverage-map "by_class")
        unmet (->> sinks (filter #(> (get % "unmet_kw") 1.0)) (sort-by #(- (get % "unmet_kw"))))]
    (str
     "# 燠 okibi — THERMAL MATCHING MAP\n\n"
     "OBSERVATION ONLY. This is a **matching map, NEVER a dispatch order** (G1). A "
     "match must pass the temperature cascade (source ≥ sink-req + " approach-temp-c
     "°C) and distance (≤ " (int max-distance-m) " m) gates — infeasible pairs can never "
     "match (G2). A realized match becomes a 澪 mio flow-improvement claim (R1). Heat "
     "is local.\n\n"
     "## Org matched thermal flow\n\n"
     "- **matched = " (round3 (get totals "matched_kw")) " kW** across "
     (get totals "match_count") " matches\n"
     "- unmatched source surplus: " (round3 (get totals "unmatched_source_kw")) " kW · "
     "unmatched demand: " (round3 (get totals "unmatched_demand_kw")) " kW\n\n"
     "## Matches (matched kW, highest first)\n\n"
     "| source | sink | matched kW | quality | distance m |\n|---|---|---|---|---|\n"
     (str/join "\n"
               (for [m matches]
                 (str "| " (get m "src") " | " (get m "sink")
                      " | " (round3 (get m "matched_kw"))
                      " | " (round3 (get m "quality"))
                      " | " (round3 (get m "distance_m")) " |")))
     "\n\n## Unmet demand (matching gaps → ingest more local sources)\n\n"
     "| sink | required °C | unmet kW |\n|---|---|---|\n"
     (str/join "\n"
               (for [s unmet]
                 (str "| " (get s "name") " | " (get s "temp_req_c")
                      " | " (round3 (get s "unmet_kw")) " |")))
     "\n\n## Coverage (source classes mapped)\n\n"
     "| source class | have | target | gap |\n|---|---|---|---|\n"
     (str/join "\n"
               (for [c cov]
                 (str "| " (name (get c "source_class")) " | " (get c "have")
                      " | " (get c "target") " | " (get c "gap") " |")))
     "\n\n_unmet demand is a gap, never a target-list. okibi matches, never dispatches; "
     "hikari actuates under Council gate._\n")))

;; ── CLI (bb) ────────────────────────────────────────────────────────────────

#?(:clj
   (defn -main [& args]
     (let [seed (or (first args) "20-actors/okibi/kotoba/seed.edn")
           ;; oe/sources+oe/sinks tolerate both the legacy bare-map seed.edn
           ;; shape and the datomized tx-data shape (single reconstitution
           ;; point — see okibi.methods.okibi-edn/classify).
           sources (oe/sources seed)
           sinks (oe/sinks seed)
           a (analyze sources sinks)
           cov (coverage sources)]
       (println (render-report a cov))
       (println (str "-- " (count sources) " sources, " (count sinks) " sinks, "
                     (get-in a ["totals" "match_count"]) " matches, matched "
                     (round3 (get-in a ["totals" "matched_kw"])) " kW, "
                     (get cov "total_gap") " source-gap --")))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (apply -main *command-line-args*)))
