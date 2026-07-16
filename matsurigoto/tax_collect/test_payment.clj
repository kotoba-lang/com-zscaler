;; matsurigoto 政 — tax-collect / 納付処理 + 祝日カレンダーの conformance test。ADR-2606062300。
(ns matsurigoto.tax-collect.test-payment
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [matsurigoto.tax-collect.jp-calendar :as cal]
            [matsurigoto.tax-collect.payment :as p]))

;; ── 祝日カレンダー (2026 の既知日で検証) ──
(deftest jp-holidays-2026
  (is (cal/holiday? (cal/parse "2026-01-01")) "元日")
  (is (cal/holiday? (cal/parse "2026-01-12")) "成人の日=1月第2月曜")
  (is (cal/holiday? (cal/parse "2026-03-20")) "春分の日 (近似)")
  (is (cal/holiday? (cal/parse "2026-07-20")) "海の日=7月第3月曜")
  (is (cal/holiday? (cal/parse "2026-09-21")) "敬老の日=9月第3月曜")
  (is (cal/holiday? (cal/parse "2026-09-22")) "国民の休日 (敬老の日と秋分の日に挟まれる)")
  (is (cal/holiday? (cal/parse "2026-09-23")) "秋分の日 (近似)")
  (is (cal/holiday? (cal/parse "2026-10-12")) "スポーツの日=10月第2月曜")
  (is (not (cal/holiday? (cal/parse "2026-02-10"))) "平日"))

(deftest tax-office-closed-and-rollover
  (is (cal/tax-office-closed? (cal/parse "2026-12-30")) "年末年始閉庁")
  (is (cal/tax-office-closed? (cal/parse "2026-01-03")) "年始閉庁")
  (is (cal/weekend? (cal/parse "2026-01-10")) "1/10は土曜")
  (is (= "2026-01-13" (str (cal/next-open-day (cal/parse "2026-01-10"))))
      "土→日→成人の日(月)→火 開庁"))

;; ── 法定納期限 ──
(deftest statutory-due-date-principle
  (testing "原則=翌月10日"
    (is (= "2026-02-10" (:due-date (p/statutory-due-date 2026 1 false))))
    (testing "10日が閉庁日なら翌開庁日"
      (let [d (p/statutory-due-date 2025 12 false)]
        (is (= "2026-01-10" (:legal-due-date d)))
        (is (= "2026-01-13" (:due-date d)))))))

(deftest statutory-due-date-tokurei
  (testing "納期特例 1〜6月分 → 7/10"
    (is (= "2026-07-10" (:legal-due-date (p/statutory-due-date 2026 3 true)))))
  (testing "納期特例 7〜12月分 → 翌年1/20"
    (is (= "2027-01-20" (:legal-due-date (p/statutory-due-date 2026 8 true))))))

(deftest tokurei-eligibility
  (is (p/slip-type-eligible-for-tokurei? :salary-retirement-special))
  (is (not (p/slip-type-eligible-for-tokurei? :fee)))
  (is (not (p/slip-type-eligible-for-tokurei? :dividend)))
  (testing "対象外様式に特例適用は例外"
    (is (thrown? Exception (p/due-date-for-slip :fee 2026 3 true)))))

;; ── 不納付加算税 (通則法67条) ──
(deftest non-payment-additional-tax-rates
  (is (= 100000 (:additional-tax (p/non-payment-additional-tax 1000000))) "10%")
  (is (= 50000  (:additional-tax (p/non-payment-additional-tax 1000000 :voluntary? true))) "自主5%")
  (testing "5,000円未満は不徴収"
    (is (= 0 (:additional-tax (p/non-payment-additional-tax 40000))) "40,000×10%=4,000<5,000"))
  (testing "1か月以内+前年期限内納付は不適用"
    (is (= 0 (:additional-tax (p/non-payment-additional-tax 1000000 :within-grace? true)))))
  (testing "計算基礎は1万円未満切捨て"
    (is (= 60000 (:base (p/non-payment-additional-tax 69000))))))

;; ── 延滞税 (通則法60条) ──
(deftest delinquency-tax-calc
  (testing "期限内納付は0"
    (is (= 0 (:delinquency-tax (p/delinquency-tax 1000000 "2026-02-10" "2026-02-10")))))
  (testing "令和7年(2.4%) 2か月以内, 28日"
    (let [r (p/delinquency-tax 1000000 "2026-02-10" "2026-03-10" (p/delinquency-rates-for-year 2025))]
      (is (= 28 (:days r)))
      ;; floor(1,000,000×24×28 / (1000×365)) = 1841 → 100円未満切捨て = 1,800
      (is (= 1800 (:delinquency-tax r)))))
  (testing "令和8年(2.8%, 引上げ後) 同条件"
    ;; floor(1,000,000×28×28 / (1000×365)) = 2147 → 100円未満切捨て = 2,100
    (is (= 2100 (:delinquency-tax (p/delinquency-tax 1000000 "2026-02-10" "2026-03-10"
                                                     (p/delinquency-rates-for-year 2026))))))
  (testing "割合は :authoritative (国税庁告示)"
    (is (= :authoritative (:sourcing (p/delinquency-rates-for-year 2026)))))
  (testing "1,000円未満は全額切捨て"
    (is (= 0 (:delinquency-tax (p/delinquency-tax 1000000 "2026-02-10" "2026-02-12"))))))

;; ── 納付方法 ──
(deftest payment-methods-by-amount
  (is (= 7 (count (p/applicable-payment-methods 200000))) "30万以下は全7手段")
  (testing "30万超はコンビニQR・スマホアプリ不可"
    (let [ms (set (map :method (p/applicable-payment-methods 500000)))]
      (is (= 5 (count ms)))
      (is (not (contains? ms :convenience-qr)))
      (is (not (contains? ms :smartphone-app)))))
  (testing "クレカ上限超は除外"
    (is (not (contains? (set (map :method (p/applicable-payment-methods 10000000)))
                        :credit-card)))))

;; ── 納付書レコード (G1 unsigned) ──
(deftest remittance-slip-unsigned
  (let [slip (p/build-remittance-slip
              {:slip-type :salary-retirement-general :pay-year 2026 :pay-month 1
               :operated-by ":etzhayyim-council"
               :lines [{:区分 "俸給・給料等" :人員 3 :支給額 900000 :税額 21132}
                       {:区分 "税理士等の報酬" :人員 1 :支給額 300000 :税額 30630}]})]
    (is (= 51762 (:total-tax slip)) "21132 + 30630")
    (is (= "2026-02-10" (:due-date slip)))
    (is (= "prepared-unsigned" (:status slip)))
    (is (nil? (:proof slip)) "G1: 署名なし")
    (is (false? (:server-held-authority slip)) "G1")
    (is (= ":etzhayyim-council" (:operated-by slip)) "G3: 納付主体を保持")))

(deftest payment-invariants
  (is (false? p/SERVER-HELD-AUTHORITY))
  (is (thrown? Exception (p/solve)) "G8: live remit は solve() で raise"))

(defn -main [& _]
  (let [r (run-tests 'matsurigoto.tax-collect.test-payment)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))
