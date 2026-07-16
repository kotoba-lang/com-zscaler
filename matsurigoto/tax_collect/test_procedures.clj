;; matsurigoto 政 — tax-collect / 政府側手続き registry の conformance test。ADR-2606062300。
(ns matsurigoto.tax-collect.test-procedures
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [matsurigoto.tax-collect.procedures :as proc]))

(def ^:private REG (proc/load-registry))

(deftest registry-loads
  (is (= 10 (count (proc/procedures REG))) "10手続き登録")
  (is (every? :source-url (proc/procedures REG)) "全手続きに出典URL (G5)")
  (is (every? :statute (proc/procedures REG)) "全手続きに根拠法令 (G2)"))

(deftest triggers-partition-procedures
  (is (= 1 (count (proc/procedures-for REG :open-payroll-office))))
  (is (= 4 (count (proc/procedures-for REG :annual-statutory-report)))
      "源泉徴収票/退職源泉徴収票/支払調書/法定調書合計表")
  (is (= 2 (count (proc/procedures-for REG :year-end-adjustment)))))

(deftest deadline-months-after
  (testing "開設届=開設から1か月以内"
    (let [d (proc/resolve-deadline (proc/procedure-by-id REG :payroll-office-open) "2026-04-15")]
      (is (= "2026-05-15" (:legal-deadline d)))))
  (testing "起算日なしは説明文"
    (is (string? (:description (proc/resolve-deadline
                                (proc/procedure-by-id REG :payroll-office-open)))))))

(deftest deadline-fixed-next-year-rolls-to-open-day
  (testing "法定調書=翌年1/31。1/31が日曜なら翌開庁日へ繰下げ"
    (let [d (proc/resolve-deadline (proc/procedure-by-id REG :fee-payment-record) "2026-06-01")]
      (is (= "2027-01-31" (:legal-deadline d)) "翌年1/31")
      (is (= "2027-02-01" (:deadline-date d)) "1/31(日)→2/1(月)開庁日"))))

(deftest deadline-relative-descriptions
  (is (= "その年最初の給与支払日の前日まで"
         (:description (proc/resolve-deadline
                        (proc/procedure-by-id REG :dependents-declaration)))))
  (testing "納期特例申請は承認効果の説明"
    (is (re-find #"翌々月"
                 (:description (proc/resolve-deadline
                                (proc/procedure-by-id REG :nokitokurei-application)))))))

(deftest plan-bundles-procedures
  (let [pl (proc/plan REG :annual-statutory-report "2026-06-01")]
    (is (= 4 (count pl)))
    (is (every? #(get-in % [:deadline :deadline-date]) pl))
    (is (every? #(= "2027-02-01" (get-in % [:deadline :deadline-date])) pl))))

(deftest procedures-invariants
  (is (false? proc/SERVER-HELD-AUTHORITY))
  (is (thrown? Exception (proc/solve)) "G8: live 提出は solve() で raise"))

(defn -main [& _]
  (let [r (run-tests 'matsurigoto.tax-collect.test-procedures)]
    (when (pos? (+ (:fail r) (:error r))) (System/exit 1))))
