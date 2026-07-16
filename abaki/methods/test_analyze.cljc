(ns abaki.methods.test-analyze
  "Tests for 暴 (abaki) analyze.cljc — Chokepoint Index + report/routing parity (clojure.test).
  analyze.py has no test_analyze.py; these assertions pin the analyzer's behaviour to the
  Python reference output captured on the committed seed (byte-parity gate)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [abaki.methods.analyze :as a]))

;; ── calculate-ci ─────────────────────────────────────────────────────────────
(deftest test-ci-compute-domain
  (is (= 100 (a/calculate-ci {"closed_source_models" true
                              "proprietary_hardware_lockin" true
                              "pricing_power_abuse" true}))))

(deftest test-ci-biology-domain
  (is (= 100 (a/calculate-ci {"f1_hybrid_lockin" true
                              "gene_patents" true
                              "lawsuits_against_farmers" true}))))

(deftest test-ci-capped-at-100
  ;; 40+30+30 = 100 exactly; logistics warehouse 50 + 20 + 30 = 100
  (is (= 100 (a/calculate-ci {"warehouse_labor_exploitation" true
                              "market_share_dominance" true
                              "anti_union_tactics" true}))))

(deftest test-ci-all-false-is-zero
  (is (= 0 (a/calculate-ci {"closed_source_models" false
                            "proprietary_hardware_lockin" false
                            "pricing_power_abuse" false}))))

(deftest test-ci-unknown-trait-ignored
  (is (= 30 (a/calculate-ci {"closed_source_models" true "unknown_trait" true})))
  (is (= 0 (a/calculate-ci {}))))

(deftest test-ci-single-weights
  (is (= 40 (a/calculate-ci {"proprietary_hardware_lockin" true})))
  (is (= 50 (a/calculate-ci {"warehouse_labor_exploitation" true})))
  (is (= 20 (a/calculate-ci {"market_share_dominance" true}))))

;; ── analyze (report + routing policy) on the seed ─────────────────────────────
(def seed-entities
  [{"id" "entity:compute:megacorp_a" "name" "MegaCorp AI Compute" "domain" "compute"
    "traits" {"closed_source_models" true "proprietary_hardware_lockin" true "pricing_power_abuse" true}
    "beneficial_owners" ["individual:tech_baron_x"]}
   {"id" "entity:biology:agri_monopoly_b" "name" "GlobalSeeds Inc." "domain" "biology"
    "traits" {"f1_hybrid_lockin" true "gene_patents" true "lawsuits_against_farmers" true}
    "beneficial_owners" ["individual:agri_baron_y" "vc:fund_z"]}
   {"id" "entity:infrastructure:logistics_c" "name" "Prime Delivery Network" "domain" "logistics"
    "traits" {"warehouse_labor_exploitation" true "market_share_dominance" true "anti_union_tactics" true}
    "beneficial_owners" ["individual:retail_baron_w"]}
   {"id" "entity:compute:murakumo_friendly" "name" "Open Source Compute Coop" "domain" "compute"
    "traits" {"closed_source_models" false "proprietary_hardware_lockin" false "pricing_power_abuse" false}
    "beneficial_owners" ["collective:community"]}])

(deftest test-routing-blocks-monopolists-keeps-coop
  (let [{:keys [routing-policy]} (a/analyze seed-entities)]
    (is (= 3 (count (get routing-policy "blocked_entities"))))
    (is (= 1 (count (get routing-policy "safe_entities"))))
    (is (= ["entity:compute:megacorp_a" "entity:biology:agri_monopoly_b" "entity:infrastructure:logistics_c"]
           (mapv #(get % "id") (get routing-policy "blocked_entities"))))
    ;; the open-source coop is the only safe entity (route-AROUND only the chokepoints)
    (is (= "entity:compute:murakumo_friendly" (get (first (get routing-policy "safe_entities")) "id")))))

(deftest test-report-byte-parity
  (let [{:keys [report-lines]} (a/analyze seed-entities)
        expected (str/join "\n"
                           ["# abaki: Chokepoint & Monopoly Visualization Report\n"
                            "> **Objective**: Visualize monopolies and generate structural reactions (Route Around) to prevent dependency.\n\n"
                            "## Identified Entities & Chokepoint Index (CI)\n"
                            "| Entity | Domain | CI Score | Status | Primary Owners |"
                            "|---|---|---|---|---|"
                            "| MegaCorp AI Compute | compute | 100 | 🚫 BLOCKED (Non-Aligned) | individual:tech_baron_x |"
                            "| GlobalSeeds Inc. | biology | 100 | 🚫 BLOCKED (Non-Aligned) | individual:agri_baron_y, vc:fund_z |"
                            "| Prime Delivery Network | logistics | 100 | 🚫 BLOCKED (Non-Aligned) | individual:retail_baron_w |"
                            "| Open Source Compute Coop | compute | 0 | ✅ SAFE | collective:community |"])]
    (is (= expected (a/report-md report-lines)))))

(deftest test-routing-json-byte-parity
  (let [{:keys [routing-policy]} (a/analyze seed-entities)
        expected (str "{\n"
                      "  \"blocked_entities\": [\n"
                      "    {\n"
                      "      \"id\": \"entity:compute:megacorp_a\",\n"
                      "      \"name\": \"MegaCorp AI Compute\",\n"
                      "      \"domain\": \"compute\",\n"
                      "      \"reason_ci\": 100\n"
                      "    },\n"
                      "    {\n"
                      "      \"id\": \"entity:biology:agri_monopoly_b\",\n"
                      "      \"name\": \"GlobalSeeds Inc.\",\n"
                      "      \"domain\": \"biology\",\n"
                      "      \"reason_ci\": 100\n"
                      "    },\n"
                      "    {\n"
                      "      \"id\": \"entity:infrastructure:logistics_c\",\n"
                      "      \"name\": \"Prime Delivery Network\",\n"
                      "      \"domain\": \"logistics\",\n"
                      "      \"reason_ci\": 100\n"
                      "    }\n"
                      "  ],\n"
                      "  \"safe_entities\": [\n"
                      "    {\n"
                      "      \"id\": \"entity:compute:murakumo_friendly\",\n"
                      "      \"name\": \"Open Source Compute Coop\",\n"
                      "      \"domain\": \"compute\"\n"
                      "    }\n"
                      "  ]\n"
                      "}")]
    (is (= expected (a/to-json routing-policy)))))
