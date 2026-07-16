(ns shionome.methods.test-weave
  "test_weave.cljc — 潮目 (shionome) weave + concentration + gates. ADR-2606072201.
  1:1 Clojure port of `methods/test_weave.py` (clojure.test). Every Python assertion ported,
  including the トレードはしない trade-token-refusal gate, plus a byte/numeric-parity check on the
  seed concentration output."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clojure.set]
            [shionome.methods.weave :as w]
            #?(:clj [shionome.methods.edn :as e])))

(def seed-path "20-actors/shionome/data/seed-capital-flow-graph.kotoba.edn")

#?(:clj (defn- g [] (w/weave (e/load-edn seed-path))))

;; expect_raises(fn, contains=…) → assert an ex-info whose message contains `frag`.
(defn- raises? [f frag]
  (try (f) false
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) ex
         (str/includes? (#?(:clj #(.getMessage %) :cljs ex-message) ex) frag))
       (catch #?(:clj Exception :cljs js/Error) ex
         (str/includes? (str (#?(:clj #(.getMessage %) :cljs ex-message) ex)) frag))))

(defn- bucket [& {:as over}]
  (merge {":bucket/id" "x" ":bucket/scope" ":asset-class" ":bucket/sourcing" ":representative"} over))

(defn- flow [& {:as over}]
  (merge {":flow/id" "f" ":flow/source" "a" ":flow/target" "b" ":flow/kind" ":rotation"
          ":flow/magnitude" 1.0 ":flow/no-trade-notice" true ":flow/sourcing" ":representative"
          ":flow/sources" ["s1" "s2"]} over))

(defn- snap [& {:as over}]
  (merge {":snap/id" "s" ":snap/bucket" "b" ":snap/metric" ":return-pct" ":snap/value" 1.0
          ":snap/sourcing" ":representative" ":snap/sources" ["s1"]} over))

;; ── bucket gates (G1/G4/G9/G11) ─────────────────────────────────────────────────
(deftest test-bucket-ok
  (is (nil? (w/validate-bucket (bucket)))))

(deftest test-bucket-bad-scope-g1
  (is (raises? #(w/validate-bucket (bucket ":bucket/scope" ":individual")) "G1")))

(deftest test-bucket-person-scope-g1
  (is (raises? #(w/validate-bucket (bucket ":bucket/scope" ":portfolio")) "G1")))

(deftest test-bucket-rating-unrepresentable-g2g4
  (is (raises? #(w/validate-bucket (bucket ":bucket/rating" "A")) "trade instruction")))

(deftest test-bucket-signal-unrepresentable
  (is (raises? #(w/validate-bucket (bucket ":bucket/signal" "x")) "trade instruction")))

(deftest test-bucket-target-unrepresentable
  (is (raises? #(w/validate-bucket (bucket ":bucket/target" 100)) "trade instruction")))

(deftest test-bucket-pii-account-g9
  (is (raises? #(w/validate-bucket (bucket ":bucket/account" "1234")) "no-doxxing")))

(deftest test-bucket-pii-investor-g9
  (is (raises? #(w/validate-bucket (bucket ":bucket/investor" "x")) "no-doxxing")))

(deftest test-bucket-missing-sourcing-g11
  (is (raises? #(w/validate-bucket (dissoc (bucket) ":bucket/sourcing")) "G11")))

;; ── flow gates (G2/G3/G11) ──────────────────────────────────────────────────────
(deftest test-flow-ok
  (is (nil? (w/validate-flow (flow)))))

(deftest test-flow-bad-kind-g2
  (is (raises? #(w/validate-flow (flow ":flow/kind" ":teleport")) "G2")))

(deftest test-flow-trade-token-buy-g2
  (is (raises? #(w/validate-flow (flow ":flow/kind" ":buy")) "トレードはしない")))

(deftest test-flow-trade-token-sell-g2
  (is (raises? #(w/validate-flow (flow ":flow/kind" ":sell")) "トレードはしない")))

(deftest test-flow-no-trade-notice-required
  (is (raises? #(w/validate-flow (flow ":flow/no-trade-notice" false)) "G2")))

(deftest test-flow-undersourced-g3
  (is (raises? #(w/validate-flow (flow ":flow/sources" ["only-one"])) "G3")))

(deftest test-flow-denied-source
  (is (raises? #(w/validate-flow (flow ":flow/sources" ["bloomberg terminal feed" "x"])) "Rider")))

(deftest test-flow-negative-magnitude
  (is (raises? #(w/validate-flow (flow ":flow/magnitude" -3.0)) "finite")))

(deftest test-flow-nan-magnitude
  (is (raises? #(w/validate-flow (flow ":flow/magnitude" #?(:clj Double/NaN :cljs js/NaN))) "finite")))

(deftest test-flow-missing-sourcing-g11
  (is (raises? #(w/validate-flow (dissoc (flow) ":flow/sourcing")) "G11")))

;; ── snapshot gates ──────────────────────────────────────────────────────────────
(deftest test-snapshot-ok
  (is (nil? (w/validate-snapshot (snap)))))

(deftest test-snapshot-bad-metric
  (is (raises? #(w/validate-snapshot (snap ":snap/metric" ":alpha")) "G2")))

(deftest test-snapshot-undersourced
  (is (raises? #(w/validate-snapshot (snap ":snap/sources" [])) "G3")))

(deftest test-snapshot-nonfinite-value
  (is (raises? #(w/validate-snapshot (snap ":snap/value" #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))) "finite")))

;; ── helpers ─────────────────────────────────────────────────────────────────────
(deftest test-trade-token-in-detects
  (is (contains? #{"recommend" "buy"} (w/trade-token-in "recommend a buy")))
  (is (= "目標株価" (w/trade-token-in "目標株価 5000")))
  (is (= "" (w/trade-token-in "clean observation"))))

(deftest test-source-denied-detects
  (is (= "refinitiv" (w/source-denied ["refinitiv eikon"])))
  (is (= "" (w/source-denied ["https://fred.stlouisfed.org/"]))))

(deftest test-trade-tokens-have-core-set
  (doseq [t ["buy" "sell" "long" "short" "target price" "推奨" "買い" "売り"]]
    (is (#'w/in-vec? w/TRADE-TOKENS t))))

;; ── seed weave + metrics ─────────────────────────────────────────────────────────
#?(:clj
   (deftest test-seed-weaves
     (let [gg (g)]
       (is (= 21 (count (#'w/omap-items (get gg "buckets")))))
       (is (= 11 (count (get gg "flows"))))
       (is (= 13 (count (get gg "snapshots")))))))

#?(:clj
   (deftest test-net-flow-top-is-us-equities
     (let [rows (w/net-flow-by-bucket (g))]
       (is (= "us-equities" (get (first rows) "bucket")))
       (is (> (get (first rows) "net") 0)))))

#?(:clj
   (deftest test-net-flow-excludes-external
     (is (every? #(not= "external" (get % "bucket")) (w/net-flow-by-bucket (g))))))

#?(:clj
   (deftest test-net-flow-only-capital-movement
     ;; sector-energy receives only a price-move (not capital) → not in net-flow rows
     (let [rows (set (map #(get % "bucket") (w/net-flow-by-bucket (g))))]
       (is (not (contains? rows "sector-energy"))))))

#?(:clj
   (deftest test-rotation-pairs-top
     (let [pairs (w/rotation-pairs (g))]
       (is (and (= "us-govt-bonds" (get (first pairs) "from"))
                (= "us-equities" (get (first pairs) "to")))))))

#?(:clj
   (deftest test-rotation-excludes-correlation
     (let [pairs (w/rotation-pairs (g))]
       (is (every? #(not (and (= "sector-tech" (get % "from")) (= "theme-ai" (get % "to")))) pairs)))))

#?(:clj
   (deftest test-inflow-hhi-in-range
     (let [ic (w/inflow-concentration (g))]
       (is (and (< 0.0 (get ic "hhi")) (<= (get ic "hhi") 1.0)))
       (is (> (get ic "total") 0)))))

#?(:clj
   (deftest test-by-asset-class-has-equities
     (is (contains? (set (map #(get % "asset_class") (w/by-asset-class (g)))) "equities"))))

#?(:clj
   (deftest test-by-region-has-us
     (is (contains? (set (map #(get % "region") (w/by-region (g)))) "us"))))

#?(:clj
   (deftest test-regime-is-risk-on
     (let [r (w/regime (g))]
       (is (= "risk-on" (get r "regime")))
       (is (true? (get r "no_trade_notice"))))))

#?(:clj
   (deftest test-correlation-cluster-present
     (let [cl (w/correlation-clusters (g))]
       (is (some #(contains? (set (get % "members")) "sector-tech") cl)))))

(deftest test-capital-movement-kinds-subset
  (is (clojure.set/subset? (set w/CAPITAL-MOVEMENT-KINDS) (set w/FLOW-KINDS)))
  (is (not (#'w/in-vec? w/CAPITAL-MOVEMENT-KINDS "cross-correlation"))))

#?(:clj
   (deftest test-concentration-full-report
     (let [c (w/concentration (g))]
       (doseq [k ["net_flow_by_bucket" "rotation_pairs" "inflow_concentration"
                  "by_asset_class" "by_region" "stock_pyramid" "regime"
                  "correlation_clusters" "integrity"]]
         (is (contains? c k))))))

;; ── stock layer / money-and-markets pyramid ──────────────────────────────────────
#?(:clj
   (deftest test-latest-stock-picks-eight-buckets
     (let [latest (w/latest-stock-by-bucket (g))]
       (is (= 8 (count (#'w/omap-items latest))))
       (is (and (contains? latest "global-derivatives") (contains? latest "global-equities")))
       (is (not (contains? latest "us-equities"))))))

#?(:clj
   (deftest test-stock-pyramid-layers-and-total
     (let [sp (w/stock-pyramid (g))]
       (is (= "usd-tn" (get sp "unit")))
       (is (= 8 (get sp "bucket_count")))
       (let [classes (set (map #(get % "asset_class") (get sp "layers")))]
         (is (clojure.set/subset? #{"cash" "broad-money" "equities" "debt" "real-estate" "gold"
                                    "crypto" "derivatives"} classes)))
       (is (< (Math/abs (- (get sp "grand_total_usd_tn") 1383.0)) 1e-6)))))

#?(:clj
   (deftest test-stock-pyramid-sorted-descending-derivatives-top
     (let [layers (get (w/stock-pyramid (g)) "layers")
           vals (mapv #(get % "usd_tn") layers)]
       (is (= vals (vec (reverse (sort vals)))))
       (is (= "derivatives" (get (first layers) "asset_class")))
       (let [s (reduce + 0.0 (map #(get % "share") layers))]
         (is (and (< 0.99 s) (<= s 1.0001)))))))

#?(:clj
   (deftest test-stock-pyramid-carries-no-trade-notice
     (is (true? (get (w/stock-pyramid (g)) "no_trade_notice")))))

#?(:clj
   (deftest test-stock-metric-not-summed-with-flows
     (let [nf (set (map #(get % "bucket") (w/net-flow-by-bucket (g))))]
       (is (and (not (contains? nf "global-equities")) (not (contains? nf "global-derivatives")))))))

#?(:clj
   (deftest test-integrity-clean-on-seed
     (is (= 0 (get-in (w/concentration (g)) ["integrity" "dangling_count"])))))

#?(:clj
   (deftest test-active-as-of-grows
     (let [gg (g)
           early (w/active-as-of gg 20260601)
           late (w/active-as-of gg 20260605)]
       (is (>= (get late "active_flows") (get early "active_flows"))))))

#?(:clj
   (deftest test-assert-integrity-raises-on-dangling
     (let [gg (g)
           gg (update gg "flows" conj (flow ":flow/id" "bad" ":flow/source" "nonesuch" ":flow/target" "us-equities"))]
       (is (raises? #(w/assert-integrity gg) "dangling")))))

;; ── byte/numeric parity on the seed concentration output ──────────────────────────
;; This exact string is `python3 -c "json.dumps(concentration(weave(load_edn(SEED))),
;; ensure_ascii=False)"` over the committed seed. The cljc to-json must reproduce it byte-for-byte.
(def expected-seed-json
  (str "{\"bucket_count\": 21, \"flow_count\": 11, \"snapshot_count\": 13, \"net_flow_by_bucket\": "
       "[{\"bucket\": \"us-equities\", \"label\": \"US equities\", \"inflow\": 23.2, \"outflow\": 10.1, "
       "\"net\": 13.1}, {\"bucket\": \"theme-ai\", \"label\": \"AI theme\", \"inflow\": 6.9, \"outflow\": "
       "0.0, \"net\": 6.9}, {\"bucket\": \"crypto-btc\", \"label\": \"Bitcoin / crypto\", \"inflow\": 5.6, "
       "\"outflow\": 0.0, \"net\": 5.6}, {\"bucket\": \"jp-equities\", \"label\": \"Japan equities\", "
       "\"inflow\": 4.4, \"outflow\": 0.0, \"net\": 4.4}, {\"bucket\": \"gold\", \"label\": \"Gold\", "
       "\"inflow\": 3.2, \"outflow\": 0.0, \"net\": 3.2}, {\"bucket\": \"em-equities\", \"label\": "
       "\"Emerging-market equities\", \"inflow\": 0.0, \"outflow\": 2.7, \"net\": -2.7}, {\"bucket\": "
       "\"cash-usd\", \"label\": \"USD cash / money-market\", \"inflow\": 0.0, \"outflow\": 8.1, \"net\": "
       "-8.1}, {\"bucket\": \"us-govt-bonds\", \"label\": \"US Treasuries\", \"inflow\": 0.0, \"outflow\": "
       "21.7, \"net\": -21.7}], \"rotation_pairs\": [{\"from\": \"us-govt-bonds\", \"from_label\": "
       "\"US Treasuries\", \"to\": \"us-equities\", \"to_label\": \"US equities\", \"magnitude\": 12.4}, "
       "{\"from\": \"cash-usd\", \"from_label\": \"USD cash / money-market\", \"to\": \"us-equities\", "
       "\"to_label\": \"US equities\", \"magnitude\": 8.1}, {\"from\": \"us-equities\", \"from_label\": "
       "\"US equities\", \"to\": \"theme-ai\", \"to_label\": \"AI theme\", \"magnitude\": 6.9}, {\"from\": "
       "\"us-equities\", \"from_label\": \"US equities\", \"to\": \"gold\", \"to_label\": \"Gold\", "
       "\"magnitude\": 3.2}, {\"from\": \"em-equities\", \"from_label\": \"Emerging-market equities\", "
       "\"to\": \"us-equities\", \"to_label\": \"US equities\", \"magnitude\": 2.7}], "
       "\"inflow_concentration\": {\"total\": 43.3, \"hhi\": 0.345, \"shares\": [[\"us-equities\", "
       "0.5357967667436488], [\"theme-ai\", 0.15935334872979215], [\"crypto-btc\", 0.12933025404157042], "
       "[\"jp-equities\", 0.10161662817551963], [\"gold\", 0.07390300230946882]], \"by_bucket\": "
       "{\"us-equities\": 23.2, \"crypto-btc\": 5.6, \"theme-ai\": 6.9, \"gold\": 3.2, \"jp-equities\": "
       "4.4}}, \"by_asset_class\": [{\"asset_class\": \"equities\", \"net\": 21.7, \"buckets\": 4}, "
       "{\"asset_class\": \"crypto\", \"net\": 5.6, \"buckets\": 1}, {\"asset_class\": \"commodities\", "
       "\"net\": 3.2, \"buckets\": 1}, {\"asset_class\": \"cash\", \"net\": -8.1, \"buckets\": 1}, "
       "{\"asset_class\": \"govt-bonds\", \"net\": -21.7, \"buckets\": 1}], \"by_region\": [{\"region\": "
       "\"global\", \"net\": 15.7, \"buckets\": 3}, {\"region\": \"jp\", \"net\": 4.4, \"buckets\": 1}, "
       "{\"region\": \"em\", \"net\": -2.7, \"buckets\": 1}, {\"region\": \"us\", \"net\": -16.7, "
       "\"buckets\": 3}], \"stock_pyramid\": {\"layers\": [{\"asset_class\": \"derivatives\", \"usd_tn\": "
       "600.0, \"share\": 0.4338, \"buckets\": 1}, {\"asset_class\": \"real-estate\", \"usd_tn\": 380.0, "
       "\"share\": 0.2748, \"buckets\": 1}, {\"asset_class\": \"debt\", \"usd_tn\": 140.0, \"share\": "
       "0.1012, \"buckets\": 1}, {\"asset_class\": \"broad-money\", \"usd_tn\": 121.0, \"share\": 0.0875, "
       "\"buckets\": 1}, {\"asset_class\": \"equities\", \"usd_tn\": 115.0, \"share\": 0.0832, \"buckets\": "
       "1}, {\"asset_class\": \"gold\", \"usd_tn\": 16.0, \"share\": 0.0116, \"buckets\": 1}, "
       "{\"asset_class\": \"cash\", \"usd_tn\": 8.0, \"share\": 0.0058, \"buckets\": 1}, {\"asset_class\": "
       "\"crypto\", \"usd_tn\": 3.0, \"share\": 0.0022, \"buckets\": 1}], \"grand_total_usd_tn\": 1383.0, "
       "\"bucket_count\": 8, \"unit\": \"usd-tn\", \"no_trade_notice\": true}, \"regime\": {\"regime\": "
       "\"risk-on\", \"risk_net\": 27.3, \"safe_net\": -26.6, \"no_trade_notice\": true}, "
       "\"correlation_clusters\": [{\"members\": [\"sector-tech\", \"theme-ai\", \"us-equities\"], "
       "\"size\": 3}], \"integrity\": {\"dangling_count\": 0, \"dangling\": []}}"))

#?(:clj
   (deftest test-seed-concentration-byte-parity
     ;; the canonical JSON of the seed concentration must match Python's json.dumps byte-for-byte.
     (is (= expected-seed-json (w/to-json (w/concentration (g)))))))

#?(:clj (defn -main [& _] (run-tests 'shionome.methods.test-weave)))
