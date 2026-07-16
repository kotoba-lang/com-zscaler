(ns shionome.methods.grounding
  "grounding.cljc — 潮目 (shionome) stock-layer ENTITY-GROUNDING bridge (R1).
  Clojure port of methods/grounding.py (1:1). ADR-2606072200.

  The stock pyramid sizes each money-and-markets LAYER as an aggregate; this bridge answers
  *who is inside each layer?* — decomposes a layer into the NAMED real entities sibling actors
  already mirror (equities ← kabuto listed-company ledger; a systemic-institutions overlay ←
  hokorobi), and reports the COVERAGE gap honestly.

  DISCIPLINE: G2 トレードはしない — a market-cap / institution count is a SIZE, never a
  rating/signal/target/solvency-verdict (every report carries no_trade_notice true); G1 — the
  grounded entities are PUBLIC companies / systemic institutions already mirrored (person-
  excluded); HONESTY — value_coverage is a stated LOWER BOUND, the count denominator is an
  explicit :representative universe figure; FAIL-OPEN — a missing/unreadable sibling ledger
  yields [] (the layer is reported ungrounded), never a crash.

  Pure functions take already-loaded record lists; only load-ledger touches disk. The
  sibling actors are read as DATA (fail-open EDN ledgers), never imported as code. Depends
  only on the same-actor edn reader; weave's `_kw` normalizer is replicated locally (behaviour,
  not the private symbol). stdlib only."
  (:require [clojure.string :as str]
            [shionome.methods.edn :as sedn]
            #?(:clj [clojure.java.io :as io])))

(def LISTED-UNIVERSE 55000)

(defn- kw*
  "Normalize an edn keyword/string to a bare lowercase token (':flow/kind' → 'kind').
  Mirror of weave's private kw* (grounding imports _kw; behaviour is what the oracle pins)."
  [v]
  (when (and v (not= "" v))
    (let [s (-> (str v) (str/replace #"^:+" ""))]
      (-> (last (str/split s #"/" -1)) (str/lower-case)))))

;; per-layer grounding ROADMAP — the honest map of what can / cannot be entity-grounded.
(def LAYER-GROUNDING
  {"equities"    {"source_actor" "kabuto" "status" "grounded"
                  "reason" "listed-company ledger (org.corp.*) decomposes the layer by named issuer; kanjō adds disclosure depth"}
   "debt"        {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "bond-market SIZE is a SIFMA/BIS aggregate; no per-issuer debt-outstanding ledger (kanjō discloses corporate BS, NOT tradable debt-securities outstanding). Candidate issuers: ooyake (sovereign) + kabuto (corporate)"}
   "broad-money" {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "M2 is a central-bank / banking-system aggregate (BIS / World Bank); not entity-decomposable"}
   "cash"        {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "physical currency outstanding is a central-bank aggregate; no holder ledger (G1 bars holder-tracking)"}
   "real-estate" {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "no real-estate-entity / parcel ledger in the repo; total value is a Savills aggregate"}
   "gold"        {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "above-ground stock value is a World Gold Council aggregate; no holder ledger (G1 bars holder-tracking)"}
   "crypto"      {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "total market-cap aggregate; no issuer ledger in the repo"}
   "derivatives" {"source_actor" nil "status" "ungroundable-at-r0"
                  "reason" "OTC gross-notional is a BIS aggregate; not entity-decomposable from any repo ledger"}})

(defn- pyround [x n]
  (let [f (Math/pow 10.0 n)] (/ (Math/rint (* (double x) f)) f)))

#?(:clj
   (defn load-ledger
     "Load a sibling actor's EDN ledger (a top-level vector of records). FAIL-OPEN: a missing or
     unreadable ledger returns [] — never crashes shionome."
     [path]
     (if-not (.exists (io/file (str path)))
       []
       (try
         (let [data (sedn/load-edn path)] (if (vector? data) data []))
         (catch Exception _ [])))))

(defn kabuto-equity-constituents
  "Named listed-company constituents of the global EQUITIES layer, from a kabuto ledger. Only
  records carrying :company/name are companies; :company/market-cap-busd is a FACTUAL size (G2),
  may be absent (named-but-unsized)."
  [records]
  (->> records
       (filter (fn [r] (and (map? r) (get r ":company/name"))))
       (mapv (fn [r]
               (let [mc (get r ":company/market-cap-busd")]
                 {"id" (get r ":company/id")
                  "name" (get r ":company/name")
                  "ticker" (get r ":company/ticker")
                  "country" (get r ":company/country")
                  "sector" (kw* (get r ":company/sector"))
                  "market_cap_busd" (when (some? mc) (double mc))
                  "sourcing" (kw* (get r ":company/sourcing"))})))))

(defn hokorobi-institutions
  "Systemic financial INSTITUTIONS from a hokorobi ledger (:organism/kind :institution). A
  cross-cutting overlay; resilience map, never a solvency verdict (G2)."
  [records]
  (->> records
       (filter (fn [r] (and (map? r) (= "institution" (kw* (get r ":organism/kind"))))))
       (mapv (fn [r]
               {"id" (get r ":organism/id")
                "label" (get r ":organism/label")
                "sector" (kw* (get r ":inst/sector"))
                "sii" (kw* (get r ":inst/sii"))
                "jurisdiction" (get r ":inst/jurisdiction")
                "sourcing" (kw* (get r ":organism/sourcing"))}))))

(defn kanjo-disclosed-companies
  "The set of org.corp.* company ids with ≥1 DISCLOSED filing in a kanjō ledger
  (:fin.filing/company). A presence count, never a fundamental rating (G2)."
  [records]
  (into #{} (keep (fn [r] (when (map? r) (get r ":fin.filing/company"))) records)))

(defn ground-equities
  "Ground the EQUITIES stock layer in named listed companies, reporting honestly."
  [layer-usd-tn constituents & {:keys [universe disclosed-ids]
                                :or {universe LISTED-UNIVERSE disclosed-ids #{}}}]
  (let [n      (count constituents)
        sized  (filterv #(some? (get % "market_cap_busd")) constituents)
        mcap-tn (/ (reduce + 0.0 (map #(get % "market_cap_busd") sized)) 1000.0)
        top    (take 10 (sort-by #(- (get % "market_cap_busd")) sized))
        disclosed (filterv #(contains? disclosed-ids (get % "id")) constituents)]
    {"layer" "equities"
     "layer_usd_tn" (pyround layer-usd-tn 4)
     "grounded_entities" n
     "entities_with_size" (count sized)
     "grounded_market_cap_usd_tn" (pyround mcap-tn 4)
     "value_coverage_of_layer" (if (and layer-usd-tn (not (zero? layer-usd-tn)))
                                 (pyround (/ mcap-tn layer-usd-tn) 4) 0.0)
     "value_coverage_is_lower_bound" (< (count sized) n)
     "count_coverage_of_universe" (if (and universe (not (zero? universe)))
                                    (pyround (/ n (double universe)) 5) 0.0)
     "universe_denominator" universe
     "universe_sourcing" "representative"
     "with_disclosed_financials" (count disclosed)
     "disclosed_sample" (vec (take 10 (map #(get % "name") disclosed)))
     "top_constituents" (mapv (fn [c] {"name" (get c "name") "ticker" (get c "ticker")
                                       "market_cap_busd" (get c "market_cap_busd")}) top)
     "no_trade_notice" true}))

(defn grounding-roadmap
  "Per-layer grounding STATUS for every money-pyramid layer (from LAYER-GROUNDING)."
  [pyramid]
  (mapv (fn [l]
          (let [ac (get l "asset_class")
                spec (get LAYER-GROUNDING ac {"source_actor" nil "status" "unmapped"
                                              "reason" "no grounding spec for this asset class"})]
            {"asset_class" ac
             "layer_usd_tn" (get l "usd_tn")
             "source_actor" (get spec "source_actor")
             "status" (get spec "status")
             "reason" (get spec "reason")}))
        (get pyramid "layers" [])))

(defn systemic-overlay
  "The cross-cutting systemic-institution overlay (from hokorobi). Counts by sector + sourcing
  honesty; sizes nothing against a single layer. Resilience map, never a solvency verdict (G2)."
  [institutions]
  (let [by-sector (into (sorted-map) (frequencies (map #(or (get % "sector") "(unknown)") institutions)))
        auth (count (filter #(= "authoritative" (get % "sourcing")) institutions))]
    {"institutions" (count institutions)
     "by_sector" by-sector
     "authoritative" auth
     "representative" (- (count institutions) auth)
     "note" (str "cross-cutting systemic overlay — G-SIB banks / insurers / pensions / CCPs span "
                 "equities+debt+pensions, not one pyramid layer; resilience map, never a solvency verdict")
     "no_trade_notice" true}))

(defn ground
  "The full stock-layer grounding report: decompose pyramid layers into named real entities WHERE
  a sibling ledger exists, add disclosure DEPTH (kanjō) to equities, and stay HONEST about the
  rest via the per-layer roadmap. Every figure is an aggregate / size / coverage fraction (G2/G4)."
  ([pyramid kabuto-records hokorobi-records] (ground pyramid kabuto-records hokorobi-records []))
  ([pyramid kabuto-records hokorobi-records kanjo-records]
   (let [layers (into {} (map (fn [l] [(get l "asset_class") l]) (get pyramid "layers" [])))
         eq-layer (double (get-in layers ["equities" "usd_tn"] 0.0))
         disclosed (kanjo-disclosed-companies (or kanjo-records []))
         equities (ground-equities eq-layer (kabuto-equity-constituents kabuto-records)
                                   :disclosed-ids disclosed)
         overlay (systemic-overlay (hokorobi-institutions hokorobi-records))
         grounded-classes (if (pos? (get equities "grounded_entities")) #{"equities"} #{})
         ungrounded (vec (remove grounded-classes
                                 (map #(get % "asset_class") (get pyramid "layers" []))))]
     {"equities" equities
      "systemic_institutions_overlay" overlay
      "roadmap" (grounding-roadmap pyramid)
      "ungrounded_layers" ungrounded
      "summary" {"pyramid_layers" (count (get pyramid "layers" []))
                 "layers_with_entity_grounding" (count grounded-classes)
                 "total_named_entities" (+ (get equities "grounded_entities") (get overlay "institutions"))
                 "no_trade_notice" true}})))
