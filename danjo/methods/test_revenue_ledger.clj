;; test_revenue_ledger.clj — standalone test suite for the danjo revenue ledger.
;; Run: clojure -M test_revenue_ledger.clj   (from methods/)  — or  bb test_revenue_ledger.clj
;; Prints its own count and exits non-zero on failure (the danjo run_tests.sh contract).
(ns root.danjo.methods.test-revenue-ledger
  (:require [clojure.string :as str]))

(load-file "revenue_ledger.clj")
(alias 'rl 'root.danjo.methods.revenue-ledger)

(def ^:dynamic *seed-path* "../data/gov-revenue-seed.jp.edn")
(defn seed [] (rl/load-seed *seed-path*))

(def checks (atom 0))
(def fails (atom 0))
(defn check [label pred]
  (swap! checks inc)
  (if pred
    (println "  ok  " label)
    (do (swap! fails inc) (println "  FAIL" label))))
(defn throws? [f]
  (try (f) false (catch Exception _ true)))

(defn run []
  (let [s (seed)]

    ;; ── trace: 復興特別所得税 = earmarked, per-yen traceable ──
    (let [r (rl/trace s :reconstruction-surtax 2024)]
      (check "復興: traceable?"            (true? (:traceable? r)))
      (check "復興: per-yen?"              (true? (:per-yen? r)))
      (check "復興: collected = 410B"      (= 410000000000 (:collected r)))
      (check "復興: spent = 410B"          (= 410000000000 (:spent r)))
      (check "復興: residual = 0 (1円まで照合)" (zero? (:residual r)))
      (check "復興: path has collect+transfer+3 outlays"
             (= 5 (count (:path r))))
      (check "復興: path starts at 一般会計 collect"
             (= :collect (:step (first (:path r)))))
      (check "復興: non-adjudicating"      (true? (:non-adjudicating r))))

    ;; ── trace: 源泉所得税 = non-earmarked, NOT per-yen traceable ──
    (let [w (rl/trace s :withholding-income 2024)]
      (check "源泉: traceable? false"       (false? (:traceable? w)))
      (check "源泉: per-yen? false"         (false? (:per-yen? w)))
      (check "源泉: reason = non-earmarked" (= :non-earmarked-general-account (:reason w)))
      (check "源泉: collected reported"     (= 14500000000000 (:collected w)))
      (check "源泉: gives an honest note"   (str/includes? (:note w) "ノン・アフェクタシオン")))

    ;; ── honesty gate: per-yen provenance through a fungible account is unrepresentable ──
    (let [poisoned (update s :outlays conj
                           {:record-id "out-poison" :account :general
                            :program-code "X" :program-name "x" :cofog "0" :recipient-class "x"
                            :fiscal-year 2024 :amount-jpy 1
                            :funded-by-tax :withholding-income     ; ← false provenance claim
                            :source-record-cids ["c1" "c2"]})]
      (check "honesty-gate: linking 源泉 → 一般会計 outlay RAISES"
             (throws? #(rl/outlay-datoms poisoned))))
    ;; but the SAME link IS allowed into an earmarked account
    (let [ok (update s :outlays conj
                     {:record-id "out-ok" :account :special/reconstruction
                      :program-code "Y" :program-name "y" :cofog "0" :recipient-class "y"
                      :fiscal-year 2024 :amount-jpy 1
                      :funded-by-tax :reconstruction-surtax
                      :source-record-cids ["c1" "c2"]})]
      (check "honesty-gate: linking 復興 → 特会 outlay is allowed"
             (not (throws? #(rl/outlay-datoms ok)))))

    ;; ── G5: ≥2 source CIDs required ──
    (let [thin (assoc-in s [:revenue-lines 0 :source-record-cids] ["only-one"])]
      (check "G5: <2 source CIDs RAISES" (throws? #(rl/revenue-datoms thin))))

    ;; ── G4: no verdict token may appear in an attribute ──
    (check "G4: all emitted datoms carry no verdict token"
           (not (throws? #(rl/all-datoms s))))

    ;; ── EAVT shape: every datom is [:db/add E A V] ──
    (let [ds (rl/all-datoms s)]
      (check "datoms non-empty"  (pos? (count ds)))
      (check "datoms are :db/add" (every? #(= :db/add (first %)) ds))
      (check "amounts are exact integers (1円精度)"
             (every? integer?
                     (->> ds (filter #(= :gov.revenue/amount-jpy (nth % 2 nil)))
                          (map #(nth % 3))))))

    ;; ── content-addressed commit-DAG log: append + verify + resume ──
    (let [log (str (System/getProperty "java.io.tmpdir") "/danjo-revtest-"
                   (hash *seed-path*) ".kotoba.edn")]
      (when (.exists (clojure.java.io/file log)) (.delete (clojure.java.io/file log)))
      (let [r1 (rl/run-cycle! {:seed-path *seed-path* :log-path log :tx-id "t1" :as-of 1})
            r2 (rl/run-cycle! {:seed-path *seed-path* :log-path log :tx-id "t2" :as-of 2})
            v  (rl/verify-chain log)]
        (check "cycle persisted datoms"        (pos? (:datom-count r1)))
        (check "second cycle chains on first"  (not= (:head-cid r1) (:head-cid r2)))
        (check "chain verifies ok"             (true? (:ok v)))
        (check "chain length = 2"              (= 2 (:length v)))
        (check "head-cid = last tx cid"        (= (:head-cid r2) (rl/head-cid log))))
      (.delete (clojure.java.io/file log)))))

(run)
(println (format "── revenue_ledger: %d checks, %d failures ──" @checks @fails))
(when (pos? @fails) (System/exit 1))
