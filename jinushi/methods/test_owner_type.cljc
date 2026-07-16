#!/usr/bin/env bb
;; jinushi 地主 — tests for the global holder-type concentration lens.
;; Run:  bb --classpath 20-actors 20-actors/jinushi/methods/test_owner_type.cljc
(ns jinushi.methods.test-owner-type
  "Tests for owner-type-concentration — the global holder-TYPE breakdown (worldwide acquired land
  area per :owner/type with shares) that surfaces the private-vs-commons split the per-owner HHI
  and the per-country :by-country breakdown do not roll up worldwide. A commons-return MAP,
  aggregate + advisory: the output carries only types + areas + shares, never an owner name,
  parcel, or person (G1/G2)."
  (:require [jinushi.methods.analyze :as a]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private data
  {:owners [{:owner/key "ok-priv" :owner/name "Acme" :owner/type :private}
            {:owner/key "ok-pub"  :owner/name "Gov"  :owner/type :public}
            {:owner/key "ok-coop" :owner/name "Coop" :owner/type :cooperative}]
   :parcels [{:parcel/id "p1" :parcel/country "JP" :parcel/area-m2 100.0 :parcel/owner "ok-priv"}
             {:parcel/id "p2" :parcel/country "JP" :parcel/area-m2 50.0  :parcel/owner "ok-pub"}
             {:parcel/id "p3" :parcel/country "US" :parcel/area-m2 200.0 :parcel/owner "ok-priv"}
             {:parcel/id "p4" :parcel/country "US" :parcel/area-m2 30.0  :parcel/owner "ok-coop"}]})

(def ^:private result (a/analyze data))

(deftest aggregates-area-by-owner-type-worldwide
  (let [by-type (into {} (map (juxt :type :area-m2) (a/owner-type-concentration result)))]
    (is (= 300.0 (:private by-type)) "private = 100 (JP) + 200 (US), summed across countries")
    (is (= 50.0 (:public by-type)))
    (is (= 30.0 (:cooperative by-type)))))

(deftest shares-sum-to-one-and-rank-by-area
  (let [conc (a/owner-type-concentration result)]
    (is (= :private (:type (first conc))) "private leads (largest area) — the commons-return target")
    (is (< (Math/abs (- 1.0 (reduce + (map :share conc)))) 1e-9) "shares sum to 1")
    (is (< (Math/abs (- (/ 300.0 380.0) (:share (first conc)))) 1e-9) "private share = 300/380")))

(deftest only-types-no-person-or-facility-detail-g1
  ;; the output is purely {:type :area-m2 :share} — no owner name, parcel, or person (G1)
  (doseq [row (a/owner-type-concentration result)]
    (is (= #{:type :area-m2 :share} (set (keys row))) "rows carry only type + area + share")
    (is (contains? a/owner-types (:type row)) "the type is a known owner-type, never a person/owner name")))

(deftest empty-input-yields-empty
  (is (= [] (a/owner-type-concentration (a/analyze {:owners [] :parcels []})))))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'jinushi.methods.test-owner-type)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
