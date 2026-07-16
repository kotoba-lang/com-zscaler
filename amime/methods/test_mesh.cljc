#!/usr/bin/env bb
;; amime 網目 — mesh flow-solver tests (incl. the SoS resilience + commons-not-market invariants).
;; Run:  bb --classpath 20-actors 20-actors/amime/methods/test_mesh.cljc
(ns amime.methods.test-mesh
  (:require [amime.methods.mesh :as m]
            [amime.methods.emit :as emit]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

(def seed-path "20-actors/amime/kotoba/seed.edn")
(defn- seed [] (m/classify (m/load-edn seed-path)))
(defn- approx [a b tol] (< (Math/abs (- (double a) (double b))) tol))

;; ── the flow solve ────────────────────────────────────────────────────────────

(deftest base-mesh-balances
  (testing "surplus exceeds need; the mesh fully serves both loads at base"
    (let [s (m/solve (seed))]
      (is (approx 1700.0 (:surplus s) 1e-6))
      (is (approx 1350.0 (:need s) 1e-6))
      (is (approx 1.0 (:served-fraction s) 1e-9) "base mesh serves 100%")
      (is (approx 0.0 (:unserved s) 1e-6) "no unserved load at base"))))

(deftest energy-conservation
  (testing "sent = delivered + loss (no energy created/destroyed in transit)"
    (let [s (m/solve (seed))]
      (is (approx (:sent s) (+ (:delivered s) (:loss s)) 1e-6))
      (is (> (:loss s) 0.0) "lossy links dissipate some energy")
      (is (> (:curtailed s) 0.0) "surplus exceeds need → some generation is curtailed"))))

(deftest link-utilisation-bounded
  (testing "no link carries more than its capacity"
    (let [s (m/solve (seed))]
      (doseq [[lid lr] (:links s)]
        (is (<= (:flow lr) (+ 1e-6 (:capacity lr))) (str lid " within capacity"))
        (is (<= (:util lr) 1.0000001) (str lid " util ≤ 1"))))))

;; ── N-1 contingency: the SoS insight (looks fine, is fragile) ─────────────────

(deftest n1-reveals-hidden-fragility
  (testing "base service is 100% yet a single link loss strands load — the SoS reason to map it"
    (let [cont (m/contingency (seed))
          crit (first cont)]
      (is (= :l-wb-ct (:link crit)) "wind→city is the critical chokepoint")
      (is (> (:delta-unserved crit) 200.0) "losing it strands >200 kW despite a healthy base")
      ;; contingency is sorted worst-first
      (is (apply >= (map :delta-unserved cont))))))

(deftest import-dependence-computed
  (testing "deficit sites get an import-dependence (SPOF) reading"
    (let [s (m/solve (seed))
          dep (m/import-dependence s (:links (seed)))]
      (is (contains? dep :dc-load-d))
      (is (= 3 (:n-suppliers (:dc-load-d dep))) "datacenter pulls from 3 suppliers")
      (is (<= 0.0 (:dependence (:dc-load-d dep)) 1.0)))))

;; ── G1 commons-not-market: no price / no trade (structural) ───────────────────

(deftest g1-no-market-attributes
  (testing "a COMMONS mesh — :amime/price and :amime/trade are never emitted"
    (let [edn (emit/render-datoms (seed))
          forms (emit/render-kaname-forms (seed))]
      (is (not (str/includes? edn ":amime/price")))
      (is (not (str/includes? edn ":amime/trade")))
      (is (not (str/includes? forms ":amime/price")))
      (is (not (str/includes? forms ":amime/trade")))
      (is (str/includes? edn ":amime.mesh/served-fraction"))
      (is (str/includes? edn ":amime/derived")))))

;; ── G2 ordered-flow / G3 resilience-map framing ──────────────────────────────

(deftest g2-g3-report-framing
  (testing "the report declares ordered-flow (not consumed) + map-not-target"
    (let [md (m/report-md (seed))]
      (is (str/includes? md "ORDERED FLOW"))
      (is (str/includes? md "never a target-list"))
      (is (str/includes? md "REDUNDANCY"))
      (is (str/includes? md "never dispatches")))))

;; ── kaname :energy export shape (composition with ADR-2606212000) ─────────────

(deftest kaname-export-shape
  (testing "emits a kaname-mirror :energy graph: :organism nodes + domain-tagged 縁"
    (let [forms (emit/kaname-forms (seed))
          nodes (filter #(contains? % ":organism/id") forms)
          edges (filter #(contains? % ":en/from") forms)]
      (is (= 6 (count nodes)) "one node per site")
      (is (seq edges))
      (is (every? #(= ":energy" (get % ":en/domain")) edges) "every 縁 is in the :energy layer")
      (is (some #(= ":concentrates" (get % ":en/kind")) edges) "flow concentrates onto loads")
      (is (every? #(contains? % ":en/basis") edges) "every 縁 carries an on-record basis"))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'amime.methods.test-mesh)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
