(ns funamori.methods.test-salinity-gradient
  "Tests for funamori.methods.salinity-gradient — 淡水化発電 (PRO/RED) physics + Charter gates.
  Pins both the physics (浸透圧・電力密度が ADR 表の帯に入る) と憲法ゲート
  (open-membrane / PFAS / ≥30 g/L / ≥1 W/m² / ≤50 kW)。"
  (:require [clojure.test :refer [deftest is testing]]
            [funamori.methods.salinity-gradient :as sg]))

(defn- approx?
  ([a b] (approx? a b 1e-6))
  ([a b tol] (<= (Math/abs (- (double a) (double b))) (* tol (max 1.0 (Math/abs (double b)))))))

;; ── 濃度 / 浸透圧 ────────────────────────────────────────────────────────────

(deftest test-concentration-roundtrip
  (is (approx? (sg/mol-m3->g-l (sg/g-l->mol-m3 35.0)) 35.0))
  ;; 海水 35 g/L NaCl ≈ 599 mol/m³
  (is (approx? (sg/g-l->mol-m3 35.0) 598.9 1e-2)))

(deftest test-seawater-osmotic-pressure-near-29-bar
  ;; van't Hoff: 海水 35 g/L, 20°C → ≈ 29 bar (教科書 27-28 bar に近い)
  (let [c (sg/g-l->mol-m3 35.0)
        pi-bar (/ (sg/osmotic-pressure c sg/T-default) 1.0e5)]
    (is (< 27.0 pi-bar 31.0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (sg/osmotic-pressure -1.0 293.0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (sg/osmotic-pressure 100.0 0.0))))

(deftest test-delta-pi-sea-river
  (let [pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
        dpi-bar (/ (sg/delta-pi pair) 1.0e5)]
    (is (< 27.0 dpi-bar 31.0)))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (sg/make-source-pair :draw-g-l 1.0 :feed-g-l 35.0))))

;; ── §1.4 塩分差ゲート ────────────────────────────────────────────────────────

(deftest test-salinity-difference-gate
  (let [good (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)   ;; Δ34.5
        brackish (sg/make-source-pair :draw-g-l 12.0 :feed-g-l 0.5)] ;; Δ11.5
    (is (sg/salinity-difference-ok? good))
    (is (not (sg/salinity-difference-ok? brackish)))
    (is (= good (sg/assert-salinity-difference good)))
    (is (thrown? #?(:clj Exception :cljs js/Error) (sg/assert-salinity-difference brackish)))))

;; ── §1.1/§1.2/§2 膜ゲート ────────────────────────────────────────────────────

(deftest test-membrane-open-license-permitted
  (is (sg/membrane-permitted? {:vendor "in-house" :chemistry "tfc-polyamide" :license :in-house}))
  (is (sg/membrane-permitted? {:vendor "wetsus" :chemistry "speek-sulfonated" :license :open-publication})))

(deftest test-commercial-membrane-prohibited
  (testing "商用プロプライエタリ膜は absolute prohibition (§2)"
    (is (not (sg/membrane-permitted? {:vendor "Toray" :chemistry "tfc" :license :open-publication})))
    (is (not (sg/membrane-permitted? {:vendor "Statkraft" :license :in-house})))
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (sg/assert-membrane-permitted {:vendor "GE-Power" :license :open-publication})))))

(deftest test-pfas-membrane-prohibited
  (testing "PFAS (Nafion 系) は Charter §2(c) で禁止"
    (is (not (sg/membrane-permitted? {:vendor "in-house" :chemistry "Nafion" :license :in-house})))
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (sg/assert-membrane-permitted {:vendor "DuPont-Nafion" :license :open-publication})))))

(deftest test-closed-license-membrane-rejected
  (testing "open でないライセンスは §1.1 で拒否"
    (is (not (sg/membrane-permitted? {:vendor "in-house" :chemistry "tfc" :license :proprietary})))
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (sg/assert-membrane-permitted {:vendor "in-house" :license :proprietary})))))

;; ── PRO 物理 ────────────────────────────────────────────────────────────────

(deftest test-pro-optimal-pressure-is-half-delta-pi
  (let [pair (sg/make-source-pair)
        dpi (sg/delta-pi pair)]
    (is (approx? (sg/pro-optimal-pressure dpi) (/ dpi 2.0)))))

(deftest test-pro-max-power-density-in-adr-band
  ;; ADR 表: PRO 1-3 W/m² (2026), A≈1e-12
  (let [pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
        m (sg/make-pro-membrane :water-permeability 1.0e-12)
        dpi (sg/delta-pi pair)
        wmax (sg/pro-max-power-density m dpi)]
    (is (< 1.0 wmax 3.0))
    ;; 最適点が真に最大: 近傍より大きい
    (is (>= wmax (sg/pro-power-density m dpi (* 0.3 dpi))))
    (is (>= wmax (sg/pro-power-density m dpi (* 0.7 dpi))))))

(deftest test-pro-reverse-osmosis-region-yields-no-power
  ;; ΔP ≥ Δπ で Jw≤0 → 発電せず
  (let [pair (sg/make-source-pair)
        m (sg/make-pro-membrane)
        dpi (sg/delta-pi pair)]
    (is (= 0.0 (sg/pro-water-flux m dpi (* 1.5 dpi))))
    (is (= 0.0 (sg/pro-power-density m dpi (* 1.5 dpi))))))

;; ── RED 物理 ────────────────────────────────────────────────────────────────

(deftest test-red-cell-emf-positive-and-sane
  (let [pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
        st (sg/make-red-stack)
        e (sg/red-cell-emf st pair)]
    ;; 膜ペアあたり起電力 ~0.1-0.25 V
    (is (< 0.1 e 0.25))
    ;; スタック総 EMF = N·E_pair
    (is (approx? (sg/red-stack-emf st pair) (* (:cell-pairs st) e)))))

(deftest test-red-power-density-in-adr-band
  ;; ADR 表: RED 0.5-2 W/m²。area-resistance 既定 4e-3 → ~1 W/m²
  (let [pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
        st (sg/make-red-stack)
        wd (sg/red-power-density st pair)]
    (is (< 0.5 wd 2.0))))

(deftest test-red-power-density-independent-of-stack-size
  ;; 閉形式 power density = E_cell²/(8·area-resistance) は N にも面積にも依らない
  (let [pair (sg/make-source-pair)
        s1 (sg/make-red-stack :cell-pairs 20 :pair-area-m2 0.5)
        s2 (sg/make-red-stack :cell-pairs 200 :pair-area-m2 2.0)]
    (is (approx? (sg/red-power-density s1 pair) (sg/red-power-density s2 pair) 1e-6))))

;; ── §1.6 電力密度 R3 ゲート ──────────────────────────────────────────────────

(deftest test-r3-power-density-gate
  (is (sg/power-density-meets-r3-gate? 1.2))
  (is (not (sg/power-density-meets-r3-gate? 0.8)))
  (is (= 1.5 (sg/assert-r3-power-density 1.5)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (sg/assert-r3-power-density 0.5))))

;; ── §1.7 技術選択 ────────────────────────────────────────────────────────────

(deftest test-technology-selection
  (is (= :pro (:technology (sg/select-technology (sg/make-source-pair :draw-g-l 38.0 :feed-g-l 0.5)))))
  (is (= :red (:technology (sg/select-technology (sg/make-source-pair :draw-g-l 33.0 :feed-g-l 0.5)))))
  (is (= :defer (:technology (sg/select-technology (sg/make-source-pair :draw-g-l 12.0 :feed-g-l 0.5))))))

;; ── §1.9 サイト上限ゲート ────────────────────────────────────────────────────

(deftest test-site-cap-gate
  (is (sg/site-cap-ok? 45.0))
  (is (not (sg/site-cap-ok? 55.0)))
  (is (= 50.0 (sg/assert-site-cap 50.0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (sg/assert-site-cap 51.0))))

(deftest test-site-count-gate
  (is (= 0 (sg/assert-site-count 0)))
  ;; R3 まで 1 サイトのみ → 既に 1 サイト稼働なら拒否
  (is (thrown? #?(:clj Exception :cljs js/Error) (sg/assert-site-count 1))))

;; ── 面積 ↔ 出力 ─────────────────────────────────────────────────────────────

(deftest test-area-power-roundtrip
  (let [area (sg/membrane-area-for-kw 10.0 1.5)]
    (is (approx? (sg/rated-power-kw area 1.5) 10.0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (sg/membrane-area-for-kw 10.0 0.0))))

;; ── 統合: サイト評価 ────────────────────────────────────────────────────────

(deftest test-evaluate-site-permitted
  (let [res (sg/evaluate-site
              {:pair (sg/make-source-pair :draw-g-l 36.5 :feed-g-l 0.5) ;; Δ36 ≥35 → PRO
               :membrane (sg/make-pro-membrane :water-permeability 1.0e-12)
               :power-density-w-m2 1.5
               :total-membrane-area-m2 20000.0   ;; 20,000 m² × 1.5 W/m² = 30 kW
               :existing-site-count 0})]
    (is (:permitted res))
    (is (= :pro (:technology res)))
    (is (approx? (:rated-kw res) 30.0))
    (is (< 27.0 (:delta-pi-bar res) 31.0))))

(deftest test-evaluate-site-rejects-brackish
  (let [res (sg/evaluate-site
              {:pair (sg/make-source-pair :draw-g-l 12.0 :feed-g-l 0.5)
               :membrane (sg/make-pro-membrane)
               :power-density-w-m2 1.5
               :total-membrane-area-m2 10000.0})]
    (is (not (:permitted res)))
    (is (= :salinity-difference (:violation res)))))

(deftest test-evaluate-site-rejects-commercial-membrane
  (let [res (sg/evaluate-site
              {:pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
               :membrane (sg/make-pro-membrane :vendor "Toray" :license :open-publication)
               :power-density-w-m2 1.5
               :total-membrane-area-m2 10000.0})]
    (is (not (:permitted res)))
    (is (= :commercial-membrane (:violation res)))))

(deftest test-evaluate-site-rejects-low-power-density
  (let [res (sg/evaluate-site
              {:pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
               :membrane (sg/make-pro-membrane)
               :power-density-w-m2 0.4
               :total-membrane-area-m2 10000.0})]
    (is (not (:permitted res)))
    (is (= :r3-power-density (:violation res)))))

(deftest test-evaluate-site-rejects-over-cap
  (let [res (sg/evaluate-site
              {:pair (sg/make-source-pair :draw-g-l 35.0 :feed-g-l 0.5)
               :membrane (sg/make-pro-membrane)
               :power-density-w-m2 1.5
               :total-membrane-area-m2 40000.0})] ;; 40,000 × 1.5 = 60 kW > 50
    (is (not (:permitted res)))
    (is (= :site-cap (:violation res)))))
