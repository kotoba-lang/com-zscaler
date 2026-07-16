(ns fuchi.methods.test-book
  "Tests for 扶持 (fuchi) book.cljc — 1:1 port of methods/test_book.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [fuchi.methods.book :as book]
            [fuchi.methods.provision :as prov]
            [fuchi.methods.route :as route]))

(defn- env [line imputed]
  {":envelope/line" (str ":" line) ":envelope/imputed-usd-micros-yr" imputed
   ":envelope/cash-usd-micros" 0})

(defn- rails [& pairs] (route/route-envelope (mapv (fn [[l v]] (env l v)) pairs)))

;; ── toritate booking ────────────────────────────────────────────────────────
(deftest test-categories-map-to-toritate-enum
  (let [rs (rails ["housing" 1] ["food" 1] ["energy" 1] ["compute" 1] ["tooling" 1] ["care" 1])
        cats (set (map :category (book/book-toritate rs "a" "did:m:x")))]
    (is (= cats #{"subsistence-flow" "vocation-flow" "care-flow"}))))

(deftest test-liquidity-is-not-booked-as-income
  (let [rs (rails ["food" 5] ["liquidity" 5])
        entries (book/book-toritate rs "a" "did:m:x")]
    (is (and (= (count entries) 1) (= (:category (first entries)) "subsistence-flow")))))

(deftest test-every-ledger-entry-is-cashless
  (doseq [e (book/book-toritate (rails ["food" 4] ["care" 1]) "a" "did:m:x")]
    (is (= (:cash-usd-micros e) 0))))

(deftest test-payroll-category-is-unrepresentable
  (doseq [bad ["payroll" "salary" "wage" "bonus"]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (book/make-ledger-entry {:alloc-id "a" :category bad :imputed-usd-micros-yr 1
                                          :counterparty-did "did:m:x"})))))

(deftest test-nonzero-cash-ledger-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"(?i)cash"
                        (book/make-ledger-entry {:alloc-id "a" :category "subsistence-flow"
                                                 :imputed-usd-micros-yr 1 :counterparty-did "did:m:x"
                                                 :cash-usd-micros 5}))))

;; ── kanae flow graph ────────────────────────────────────────────────────────
(deftest test-flow-graph-has-publicfund-source
  (let [edges (book/flow-graph (rails ["food" 4] ["energy" 1]) "a" "did:m:x")
        src (filter #(= (:flow-class %) "publicfund-to-fuchi") edges)]
    (is (and (= (count src) 1) (= (:frm (first src)) book/PUBLIC-FUND) (= (:to (first src)) book/FUCHI)))
    (is (= (:imputed-usd-micros-yr (first src)) 5))))

(deftest test-flow-legs-chain-to-maintainer
  (let [classes (map :flow-class (book/flow-graph (rails ["food" 4]) "a" "did:m:x"))]
    (is (and (some #{"fuchi-to-provider"} classes) (some #{"provider-to-maintainer"} classes)))))

(deftest test-liquidity-leg-is-not-in-kind-and-not-funded
  (let [edges (book/flow-graph (rails ["liquidity" 14]) "a" "did:m:x")]
    (is (not (some #(= (:flow-class %) "publicfund-to-fuchi") edges)))
    (let [legs (filter #(not= (:flow-class %) "publicfund-to-fuchi") edges)]
      (is (and (seq legs) (every? #(false? (:in-kind %)) legs))))))

(deftest test-provision-rails-compose-with-booking
  (let [rs (rails ["food" 4] ["compute" 2])
        intents (prov/provision rs "a")
        entries (book/book-toritate rs "a" "did:m:x")]
    (is (and (= (count intents) 2) (= (count entries) 2)))))

(deftest test-rail-to-category-excludes-liquidity
  (is (not (contains? book/RAIL-TO-CATEGORY "liquidity-warifu"))))
