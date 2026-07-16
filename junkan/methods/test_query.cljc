#!/usr/bin/env bb
;; junkan 循環 — datom index query tests (EAVT / AVET / VAET).
;; Run:  bb --classpath 20-actors 20-actors/junkan/methods/test_query.cljc
(ns junkan.methods.test-query
  (:require [junkan.methods.junkan-edn :as je]
            [junkan.methods.analyze :as az]
            [junkan.methods.query :as q]
            [clojure.test :refer [deftest is run-tests]]))

(def seed-path "20-actors/junkan/kotoba/seed.governance-asymmetry.edn")
(defn- ds [] (let [is (je/instruments seed-path)] (az/datoms is (az/analyze is))))

;; ── EAVT ──
(deftest eavt-entity
  (let [d (ds)
        ent "junkan-instr:us-foia-1966"
        m (q/entity d ent)]
    (is (= "US" (get m ":junkan.gov.instr/jurisdiction")))
    (is (= ":narrow" (get m ":junkan.gov.instr/polarity")))
    (is (= ":information-asymmetry" (get m ":junkan.gov.instr/stock")))
    (is (= "US" (q/value-of d ent ":junkan.gov.instr/jurisdiction")) "point lookup")))

;; ── AVET ──
(deftest avet-by-attr-value
  (let [d (ds)]
    (is (contains? (set (q/instruments-in d "RU")) "junkan-instr:ru-foreign-agents-law")
        "AVET: instruments in RU")
    (is (every? #(re-find #"^junkan-instr:" %) (q/instruments-by-polarity d ":widen"))
        "AVET: polarity query returns instrument entities")
    (is (pos? (count (q/instruments-by-polarity d ":narrow"))))))

;; ── VAET ──
(deftest vaet-referencing
  (let [d (ds)
        refs (q/referencing d "TH")]
    (is (some (fn [[e a]] (and (= "junkan-instr:th-lese-majeste-112" e)
                              (= ":junkan.gov.instr/jurisdiction" a))) refs)
        "VAET: TH is referenced by the lèse-majesté instrument's jurisdiction attr")))

;; ── governance helpers ──
(deftest governance-queries
  (let [d (ds)]
    (is (>= (count (q/jurisdictions d)) 40) "jurisdictions query (≥40 by iter 4)")
    (is (every? #(re-find #"^junkan-stock:" %) (q/vicious-stocks d))
        "vicious-stocks returns stock entities")
    (let [s (q/summary d)]
      (is (>= (:jurisdictions s) 40))
      (is (pos? (:widen-instruments s)))
      (is (pos? (:narrow-instruments s))))))

;; ── 誰が定めたか / 経緯 / by-stock queryable from the datoms ──────────────────
(deftest enactor-and-origin-queries
  (let [d (ds)]
    (is (re-find #"Congress" (q/enactor-of d "junkan-instr:us-foia-1966"))
        "誰が定めたか: FOIA enactor is queryable")
    (is (string? (q/origin-of d "junkan-instr:us-foia-1966")) "経緯 queryable")
    (is (contains? (set (q/instruments-by-stock d ":economic-capture"))
                   "junkan-instr:us-citizens-united-2010")
        "instruments-by-stock returns economic-capture members")
    (is (pos? (count (q/instruments-by-kind d ":value")))
        "instruments-by-kind returns :value-kind instruments")))

;; ── read-only (G4): query.cljc carries no mutation/outward verb ──────────────
(deftest g4-read-only
  (let [src (slurp "20-actors/junkan/methods/query.cljc")]
    (is (nil? (re-find #"(?im)\((?:spit|append-tx|transact!|post|dispatch|send)\b" src))
        "query.cljc has no write/outward call (G4 read-only by absence)")))

#?(:clj
   (defn -main [& _]
     (let [r (run-tests 'junkan.methods.test-query)]
       (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (-main)))
