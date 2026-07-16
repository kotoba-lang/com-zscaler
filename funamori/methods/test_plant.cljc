(ns funamori.methods.test-plant
  "Tests for funamori.methods.plant — 潮汐塩分濃度差発電 plant + grid-tie 制御層。
  潮汐変動 / 容量率 / ピーク上限ゲート / 蓄電平滑 / hikari 連系ハンドオフ を pin。"
  (:require [clojure.test :refer [deftest is testing]]
            [funamori.methods.plant :as p]))

(defn- approx? [a b tol]
  (<= (Math/abs (- (double a) (double b))) (* tol (max 1.0 (Math/abs (double b))))))

;; ── プラント定義 ────────────────────────────────────────────────────────────

(deftest test-make-plant-rated
  (let [pl (p/make-plant :membrane-area-m2 20000.0 :power-density-w-m2 1.5)]
    ;; 20,000 m² × 1.5 W/m² = 30 kW
    (is (approx? (:rated-kw pl) 30.0 1e-9))
    (is (= :pro (:technology pl)))))

;; ── 潮汐変動 ────────────────────────────────────────────────────────────────

(deftest test-power-peaks-at-high-tide
  (testing "満潮 (phase=π/2, draw 最大) で出力最大、干潮 (phase=3π/2) で最小"
    (let [pl (p/make-plant)
          high (p/instantaneous-power-kw pl (/ Math/PI 2.0))
          mid  (p/instantaneous-power-kw pl 0.0)
          low  (p/instantaneous-power-kw pl (* 1.5 Math/PI))]
      (is (> high mid))
      (is (> mid low))
      (is (>= low 0.0)))))

(deftest test-mean-tide-gives-rated
  ;; phase=0 は draw=draw-mean なので出力 ≈ 定格
  (let [pl (p/make-plant :membrane-area-m2 20000.0 :power-density-w-m2 1.5)]
    (is (approx? (p/instantaneous-power-kw pl 0.0) (:rated-kw pl) 1e-6))))

;; ── 発電時系列 / 容量率 ─────────────────────────────────────────────────────

(deftest test-generation-series-shape
  (let [pl (p/make-plant)
        g (p/generation-series pl :samples 48)]
    (is (= 48 (count (:series g))))
    (is (approx? (:dt-h g) (/ p/m2-tidal-period-h 48) 1e-9))
    ;; 容量率 ∈ (0,1]
    (is (< 0.0 (:capacity-factor g)))
    (is (<= (:capacity-factor g) 1.0))
    ;; mean は min と peak の間
    (is (<= (:min-kw g) (:mean-kw g) (:peak-kw g)))))

;; ── ピーク上限ゲート（§1.9 再利用） ─────────────────────────────────────────

(deftest test-peak-power-cap-enforced
  (testing "ピーク出力 ≤50 kW なら通り、超過なら投げる"
    (let [ok (p/make-plant :membrane-area-m2 20000.0 :power-density-w-m2 1.5)   ;; ~34 kW peak
          over (p/make-plant :membrane-area-m2 40000.0 :power-density-w-m2 1.5)] ;; ~69 kW peak
      (is (= ok (p/assert-plant-cap ok)))
      (is (< (p/peak-power-kw ok) 50.0))
      (is (> (p/peak-power-kw over) 50.0))
      (is (thrown? #?(:clj Exception :cljs js/Error) (p/assert-plant-cap over))))))

;; ── 系統連系 蓄電平滑 ───────────────────────────────────────────────────────

(deftest test-grid-tie-large-battery-fully-smooths
  (let [pl (p/make-plant)
        gt (p/grid-tie pl :battery-kwh 1000.0 :samples 48)]
    (is (:fully-smoothed gt))
    (is (approx? (:shortfall-kwh gt) 0.0 1e-6))
    ;; 受け渡しは平均出力（target 未指定時）
    (is (= 48 (count (:delivered gt))))))

(deftest test-grid-tie-tiny-battery-shows-shortfall
  (testing "小さすぎるバッテリは平滑しきれず shortfall を正直に計上"
    (let [pl (p/make-plant)
          gt (p/grid-tie pl :battery-kwh 0.01 :samples 48)]
      (is (false? (:fully-smoothed gt)))
      (is (> (:shortfall-kwh gt) 0.0)))))

(deftest test-grid-tie-bigger-battery-reduces-shortfall
  (let [pl (p/make-plant)
        small (p/grid-tie pl :battery-kwh 5.0 :samples 48)
        big   (p/grid-tie pl :battery-kwh 50.0 :samples 48)]
    (is (<= (:shortfall-kwh big) (:shortfall-kwh small)))))

;; ── hikari 連系ハンドオフ ───────────────────────────────────────────────────

(deftest test-couple-to-microgrid-datom
  (let [pl (p/make-plant :membrane-area-m2 20000.0 :power-density-w-m2 1.5)
        d (p/couple-to-microgrid pl :battery-kwh 1000.0)]
    (is (= "hikari" (:funamori.plant/microgrid d)))
    (is (= :pro (:funamori.plant/technology d)))
    (is (< 0.0 (:funamori.plant/capacity-factor d) 1.0))
    (is (true? (:funamori.plant/model-only d)))      ;; R0 invariant
    (is (true? (:funamori.plant/fully-smoothed d)))))

(deftest test-couple-rejects-over-cap-plant
  (let [over (p/make-plant :membrane-area-m2 40000.0 :power-density-w-m2 1.5)]
    (is (thrown? #?(:clj Exception :cljs js/Error) (p/couple-to-microgrid over)))))
