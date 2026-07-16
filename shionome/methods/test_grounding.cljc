(ns shionome.methods.test-grounding
  "Cross-language oracle tests for shionome.methods.grounding — the Clojure port of
  methods/grounding.py.

  Ported 1:1 from the REAL Python test_grounding.py (24 tests). The core functions are
  pure and fed inline fixtures (hermetic), so the suite never depends on a sibling
  actor's file contents; one conditional check exercises the real kabuto ledger IF
  present (and the fail-open path otherwise), green either way."
  (:require [clojure.test :refer [deftest is testing]]
            [shionome.methods.grounding :as gr]))

(def root "20-actors")

;; banker's round to n places — mirrors the Python round() the grounding figures use,
;; so the expected value matches grounding's internal pyround exactly.
(defn- rnd [x n] (let [f (Math/pow 10.0 n)] (/ (Math/rint (* (double x) f)) f)))

(defn- kab-fixture []
  [{":company/id" "org.corp.us.apple" ":company/name" "Apple" ":company/ticker" "AAPL"
    ":company/country" "US" ":company/sector" ":electronics" ":company/market-cap-busd" 3300.0
    ":company/sourcing" ":representative"}
   {":company/id" "org.corp.tw.tsmc" ":company/name" "TSMC" ":company/ticker" "2330.TW"
    ":company/market-cap-busd" 950.0 ":company/sourcing" ":representative"}
   {":company/id" "org.corp.jp.x" ":company/name" "Unsized Co" ":company/ticker" "9999.T"}
   {":address/of" "org.corp.us.apple" ":address/city" "Cupertino"}
   {":supply.edge/from" "a" ":supply.edge/to" "b"}])

(defn- hok-fixture []
  [{":organism/id" "fin.inst.jpmorgan" ":organism/kind" ":institution" ":organism/label" "JPMorgan Chase"
    ":inst/sector" ":bank" ":inst/sii" ":g-sib" ":inst/jurisdiction" "US" ":organism/sourcing" ":authoritative"}
   {":organism/id" "fin.inst.allianz" ":organism/kind" ":institution" ":organism/label" "Allianz"
    ":inst/sector" ":insurer" ":inst/sii" ":large" ":inst/jurisdiction" "DE" ":organism/sourcing" ":authoritative"}
   {":organism/id" "fin.inst.regional" ":organism/kind" ":institution" ":organism/label" "Regional bank"
    ":inst/sector" ":bank" ":inst/sii" ":mid" ":inst/jurisdiction" "US" ":organism/sourcing" ":representative"}
   {":organism/id" "risk.leverage" ":organism/kind" ":risk-source"}
   {":en/from" "a" ":en/to" "b" ":en/risk-load" 0.7}])

(defn- kanjo-fixture []
  [{":fin.filing/id" "fil.a" ":fin.filing/company" "org.corp.us.apple" ":fin.filing/fiscal-year" 2024}
   {":fin.filing/id" "fil.t" ":fin.filing/company" "org.corp.tw.tsmc" ":fin.filing/fiscal-year" 2024}
   {":fin.fact/id" "fact.x" ":fin.fact/company" "org.corp.us.apple"}])

(defn- pyramid-fixture []
  {"layers" [{"asset_class" "equities" "usd_tn" 115.0}
             {"asset_class" "gold" "usd_tn" 16.0}
             {"asset_class" "derivatives" "usd_tn" 600.0}]})

;; ── kabuto constituent extraction ─────────────────────────────────────────────
(deftest kabuto-filters-non-company-records
  (let [cons (gr/kabuto-equity-constituents (kab-fixture))]
    (is (= 3 (count cons)))
    (is (= #{"Apple" "TSMC" "Unsized Co"} (set (map #(get % "name") cons))))))

(deftest kabuto-parses-size-and-sector
  (let [cons (into {} (map (fn [c] [(get c "name") c]) (gr/kabuto-equity-constituents (kab-fixture))))]
    (is (= 3300.0 (get-in cons ["Apple" "market_cap_busd"])))
    (is (= "electronics" (get-in cons ["Apple" "sector"])))
    (is (nil? (get-in cons ["Unsized Co" "market_cap_busd"])))))

;; ── hokorobi institution extraction ───────────────────────────────────────────
(deftest hokorobi-filters-to-institutions
  (let [insts (gr/hokorobi-institutions (hok-fixture))]
    (is (= 3 (count insts)))
    (is (= #{"JPMorgan Chase" "Allianz" "Regional bank"} (set (map #(get % "label") insts))))))

(deftest systemic-overlay-counts-and-sourcing
  (let [ov (gr/systemic-overlay (gr/hokorobi-institutions (hok-fixture)))]
    (is (= 3 (get ov "institutions")))
    (is (= {"bank" 2 "insurer" 1} (into {} (get ov "by_sector"))))
    (is (= 2 (get ov "authoritative")))
    (is (= 1 (get ov "representative")))
    (is (= true (get ov "no_trade_notice")))))

;; ── equities grounding math (the honesty contract) ─────────────────────────────
(deftest ground-equities-value-coverage-math
  (let [g (gr/ground-equities 115.0 (gr/kabuto-equity-constituents (kab-fixture)) :universe 55000)]
    (is (= 3 (get g "grounded_entities")))
    (is (= 2 (get g "entities_with_size")))
    (is (< (Math/abs (- (get g "grounded_market_cap_usd_tn") 4.25)) 1e-9))
    (is (< (Math/abs (- (get g "value_coverage_of_layer") (rnd (/ 4.25 115.0) 4))) 1e-9))))

(deftest ground-equities-value-coverage-is-lower-bound
  (is (= true (get (gr/ground-equities 115.0 (gr/kabuto-equity-constituents (kab-fixture)))
                   "value_coverage_is_lower_bound"))))

(deftest ground-equities-count-coverage-fraction
  (let [g (gr/ground-equities 115.0 (gr/kabuto-equity-constituents (kab-fixture)) :universe 55000)]
    (is (= "representative" (get g "universe_sourcing")))
    (is (< (Math/abs (- (get g "count_coverage_of_universe") (rnd (/ 3 55000.0) 5))) 1e-9))))

(deftest ground-equities-top-constituents-sorted
  (let [tops (get (gr/ground-equities 115.0 (gr/kabuto-equity-constituents (kab-fixture))) "top_constituents")]
    (is (= "Apple" (get (nth tops 0) "name")))
    (is (= "TSMC" (get (nth tops 1) "name")))))

(deftest ground-equities-no-trade-notice
  (is (= true (get (gr/ground-equities 115.0 []) "no_trade_notice"))))

(deftest ground-equities-empty-is-zero-not-crash
  (let [g (gr/ground-equities 115.0 [])]
    (is (= 0 (get g "grounded_entities")))
    (is (= 0.0 (get g "value_coverage_of_layer")))))

;; ── full report + ungrounded honesty ───────────────────────────────────────────
(deftest ground-full-report-shape
  (let [rep (gr/ground (pyramid-fixture) (kab-fixture) (hok-fixture))]
    (doseq [k ["equities" "systemic_institutions_overlay" "ungrounded_layers" "summary"]]
      (is (contains? rep k)))
    (is (= true (get-in rep ["summary" "no_trade_notice"])))))

(deftest ground-names-ungrounded-layers
  (let [rep (gr/ground (pyramid-fixture) (kab-fixture) (hok-fixture))]
    (is (not (some #(= "equities" %) (get rep "ungrounded_layers"))))
    (is (= #{"gold" "derivatives"} (set (get rep "ungrounded_layers"))))
    (is (= 1 (get-in rep ["summary" "layers_with_entity_grounding"])))
    (is (= 3 (get-in rep ["summary" "pyramid_layers"])))))

(deftest ground-total-named-entities
  (is (= (+ 3 3) (get-in (gr/ground (pyramid-fixture) (kab-fixture) (hok-fixture))
                         ["summary" "total_named_entities"]))))

(deftest no-kabuto-means-equities-ungrounded
  (let [rep (gr/ground (pyramid-fixture) [] (hok-fixture))]
    (is (some #(= "equities" %) (get rep "ungrounded_layers")))
    (is (= 0 (get-in rep ["summary" "layers_with_entity_grounding"])))))

;; ── kanjō disclosure depth ──────────────────────────────────────────────────────
(deftest kanjo-disclosed-companies-extracts-filing-companies
  (is (= #{"org.corp.us.apple" "org.corp.tw.tsmc"} (gr/kanjo-disclosed-companies (kanjo-fixture)))))

(deftest ground-equities-disclosure-depth
  (let [g (gr/ground-equities 115.0 (gr/kabuto-equity-constituents (kab-fixture))
                              :disclosed-ids #{"org.corp.us.apple" "org.corp.tw.tsmc"})]
    (is (= 2 (get g "with_disclosed_financials")))
    (is (= #{"Apple" "TSMC"} (set (get g "disclosed_sample"))))))

(deftest ground-equities-depth-defaults-zero
  (is (= 0 (get (gr/ground-equities 115.0 (gr/kabuto-equity-constituents (kab-fixture)))
                "with_disclosed_financials"))))

;; ── per-layer grounding roadmap ─────────────────────────────────────────────────
(deftest roadmap-covers-every-layer
  (is (= #{"equities" "gold" "derivatives"}
         (set (map #(get % "asset_class") (gr/grounding-roadmap (pyramid-fixture)))))))

(deftest roadmap-equities-grounded-rest-ungroundable
  (let [rm (into {} (map (fn [r] [(get r "asset_class") r]) (gr/grounding-roadmap (pyramid-fixture))))]
    (is (= "grounded" (get-in rm ["equities" "status"])))
    (is (= "kabuto" (get-in rm ["equities" "source_actor"])))
    (is (= "ungroundable-at-r0" (get-in rm ["gold" "status"])))
    (is (= "ungroundable-at-r0" (get-in rm ["derivatives" "status"])))
    (is (every? #(seq (get % "reason")) (vals rm)))))

(deftest layer-grounding-registry-well-formed
  (doseq [[_ spec] gr/LAYER-GROUNDING]
    (is (contains? #{"grounded" "ungroundable-at-r0"} (get spec "status")))
    (is (seq (get spec "reason")))
    (when (= "grounded" (get spec "status"))
      (is (get spec "source_actor")))))

(deftest ground-report-has-roadmap-and-depth
  (let [rep (gr/ground (pyramid-fixture) (kab-fixture) (hok-fixture) (kanjo-fixture))]
    (is (= 3 (count (get rep "roadmap"))))
    (is (>= (get-in rep ["equities" "with_disclosed_financials"]) 1))))

(deftest ground-without-kanjo-still-works
  (is (= 0 (get-in (gr/ground (pyramid-fixture) (kab-fixture) (hok-fixture))
                   ["equities" "with_disclosed_financials"]))))

;; ── fail-open loader ─────────────────────────────────────────────────────────────
(deftest load-ledger-missing-returns-empty
  (is (= [] (gr/load-ledger (str root "/nonesuch/missing.edn")))))

(deftest real-sibling-ledgers-present-or-fail-open
  (let [kab (gr/load-ledger (str root "/kabuto/data/seed-public-companies.kotoba.edn"))]
    (if (seq kab)
      (is (> (count (gr/kabuto-equity-constituents kab)) 100))
      (is (= [] (gr/kabuto-equity-constituents kab))))))
