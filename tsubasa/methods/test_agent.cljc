(ns tsubasa.methods.test-agent
  "tsubasa 翼 — flight discovery tests. 1:1 port of py/test_agent.py. Verifies the structural
  invariants of ADR-2606072800: G4 emissions-honest (total cost includes baggage; co2Kg on every
  result; greenest first-class), G1 no-affiliate-no-inflow (affiliate params stripped; handoff has
  no commission/tithe, member principal), G3 anti-dark (no urgency/scarcity field). Note: the
  Python `compare` is `compare-fares` here (avoids shadowing clojure.core/compare)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [tsubasa.methods.agent :as agent]))

(defn- fare* [fid fare-minor & {:keys [bag co2 dur carrier url]
                                :or {bag 0 co2 100.0 dur 120 carrier "NH"
                                     url "https://nh.example/book?flt=1"}}]
  {"fareId" fid "origin" "HND" "destination" "ITM" "departDate" "2026-07-01"
   "carrier" carrier "stops" 0 "durationMin" dur "fareMinor" fare-minor
   "baggageMinor" bag "currency" "JPY" "co2Kg" co2 "cabin" "economy"
   "bookUrl" url "sourcing" "representative"})

(defn- route-fares []
  [(fare* "cheap-dirty" 8000 :bag 0 :co2 300 :dur 130)
   (fare* "pricey-green" 12000 :bag 0 :co2 90 :dur 125)
   (fare* "mid" 10000 :bag 1000 :co2 150 :dur 120)
   (assoc (fare* "other" 5000 :co2 10 :dur 60) "destination" "CTS")])   ; not HND->ITM

;; ── total cost ──
(deftest test-includes-baggage
  (is (= 12000 (agent/total-cost-minor (fare* "f" 10000 :bag 2000)))))

;; ── search ──
(deftest test-filters-route-and-date
  (is (= 3 (count (agent/search-fares "HND" "ITM" "2026-07-01" (route-fares))))))   ; CTS excluded

(deftest test-every-result-has-emissions
  (doseq [r (agent/search-fares "HND" "ITM" "2026-07-01" (route-fares))]
    (is (contains? r "co2Kg"))        ; G4: emissions on EVERY option
    (is (contains? r "totalMinor"))))

(deftest test-sort-total-default
  (let [out (agent/search-fares "HND" "ITM" "2026-07-01" (route-fares))]
    (is (= "cheap-dirty" (get (first out) "fareId")))))   ; 8000 total

(deftest test-sort-emissions
  (let [out (agent/search-fares "HND" "ITM" "2026-07-01" (route-fares) "emissions")]
    (is (= "pricey-green" (get (first out) "fareId")))))  ; 90 kg CO2 first

(deftest test-no-urgency-field
  (doseq [r (agent/search-fares "HND" "ITM" "2026-07-01" (route-fares))
          k (keys r)]
    (let [kl (str/lower-case k)]
      (is (not (str/includes? kl "urgen")))
      (is (not (str/includes? kl "scarcit")))
      (is (not (str/includes? (str/replace kl "_" "") "willrise"))))))

;; ── compare ──
(deftest test-greenest-is-first-class
  (let [fares [(fare* "a" 8000 :co2 300) (fare* "b" 12000 :co2 90) (fare* "c" 9000 :co2 150 :dur 90)]
        out (agent/compare-fares fares)]
    (is (= "a" (get (get out "cheapest") "fareId")))
    (is (= "b" (get (get out "greenest") "fareId")))   ; emissions never hidden (G4)
    (is (= "c" (get (get out "fastest") "fareId")))))

(deftest test-empty
  (is (= {"cheapest" nil "greenest" nil "fastest" nil} (agent/compare-fares []))))

;; ── handoff ──
(deftest test-affiliate-stripped
  (let [f (fare* "f" 10000 :url "https://nh.example/book?flt=1&aff=skyscanner&utm_source=meta&tag=x")
        out (agent/self-book-handoff f)
        u (get out "bookUrl")]
    (is (str/includes? u "flt=1"))
    (is (not (str/includes? u "aff=")))
    (is (not (str/includes? u "utm_source")))
    (is (not (str/includes? u "tag=")))))

(deftest test-no-commission-no-tithe-member-principal
  (let [out (agent/self-book-handoff (fare* "f" 10000))]
    (is (= 0 (get out "commissionMinor")))   ; G1
    (is (= 0 (get out "titheMinor")))
    (is (= "member" (get out "principal")))
    (is (= "self-book-handoff" (get out "mode")))))
