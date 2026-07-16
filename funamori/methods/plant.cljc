(ns funamori.methods.plant
  "plant — 淡水化発電プラントの発電・系統連系設計層（infra-robotics 3層の plant+control）。

  hikari/mizuho/kamado/noroshi と同じ kuni-umi 3層パターン（plant 物理 → control 制御
  → kinematics 運動学）に funamori を揃える層。funamori は既に
    kinematics  = stack_robotics.cljc（据付・掃引・交換）
    physics     = salinity_gradient.cljc（PRO/RED 電力密度）
  を持つ。本層が残る plant+control を足す：

    1. 潮汐変動する資源モデル — 河口の取水塩分は潮位で振れる（満潮=海水侵入で高 Δ塩分、
       干潮=塩水楔後退で低 Δ塩分）。半日周潮 M2 (T≈12.42 h)。
    2. 発電時系列 — 各時刻の Δπ(t) から PRO/RED 出力を出し、定格・容量率を測る。
    3. 系統連系 grid-tie — 蓄電バッファで潮汐変動を平滑し、hikari マイクログリッドへ
       一定出力で受け渡す（ADR-2605265600 §4 + ADR-2605264200 §3 蓄電ペアリング）。

  純計算・モデルのみ（実プラントは動かさない）。salinity_gradient のゲートを再利用し、
  ピーク出力が ≤50 kW 上限（§1.9）を超えないことを assert する。"
  (:require [funamori.methods.salinity-gradient :as sg]))

(def ^:const m2-tidal-period-h 12.42)  ;; 主太陰半日周潮 M2 周期 (h)

;; ── プラント定義 ────────────────────────────────────────────────────────────

(defn make-plant
  "塩分濃度差発電プラント。
  :technology         :pro | :red
  :membrane-area-m2   総膜面積
  :power-density-w-m2  平均 Δ塩分での電力密度 (W/m²)
  :draw-mean-g-l :draw-amp-g-l :feed-g-l :temp-k  潮汐資源モデル
  :tidal-period-h     潮汐周期 (既定 M2)。"
  [& {:keys [technology membrane-area-m2 power-density-w-m2
             draw-mean-g-l draw-amp-g-l feed-g-l temp-k tidal-period-h]
      :or   {technology :pro membrane-area-m2 20000.0 power-density-w-m2 1.5
             draw-mean-g-l 35.0 draw-amp-g-l 5.0 feed-g-l 0.5 temp-k sg/T-default
             tidal-period-h m2-tidal-period-h}}]
  {:technology technology
   :membrane-area-m2 membrane-area-m2
   :power-density-w-m2 power-density-w-m2
   :draw-mean-g-l draw-mean-g-l :draw-amp-g-l draw-amp-g-l
   :feed-g-l feed-g-l :temp-k temp-k
   :tidal-period-h tidal-period-h
   ;; 平均 Δ塩分での定格 (kW)
   :rated-kw (sg/rated-power-kw membrane-area-m2 power-density-w-m2)})

(defn- mean-delta-pi ^double [plant]
  (sg/delta-pi (sg/make-source-pair :draw-g-l (:draw-mean-g-l plant)
                                    :feed-g-l (:feed-g-l plant)
                                    :temp-k (:temp-k plant))))

;; ── 潮汐資源 ────────────────────────────────────────────────────────────────

(defn tidal-source-pair
  "潮汐位相 phase (rad) における取水源ペア。
  取水塩分 draw(t) = draw-mean + draw-amp·sin(phase)（満潮で高、干潮で低）。"
  [plant ^double phase]
  (sg/make-source-pair
    :draw-g-l (+ (double (:draw-mean-g-l plant)) (* (double (:draw-amp-g-l plant)) (Math/sin phase)))
    :feed-g-l (:feed-g-l plant)
    :temp-k (:temp-k plant)))

;; ── 瞬時出力 ────────────────────────────────────────────────────────────────

(defn instantaneous-power-kw
  "潮汐位相 phase における電気出力 (kW)。
  出力 = 定格 × (Δπ(t)/Δπ_mean)。Δπ(t)≤0 なら 0。膜の最大フラックスは
  ピーク Δ塩分で律速されるため、ピーク定格を超える分は assert-site-cap が捕える。"
  ^double [plant ^double phase]
  (let [dpi   (sg/delta-pi (tidal-source-pair plant phase))
        ratio (max 0.0 (/ dpi (max 1e-9 (mean-delta-pi plant))))]
    (* (double (:rated-kw plant)) ratio)))

(defn peak-power-kw
  "潮汐ピーク（満潮 phase=π/2）の最大出力 (kW)。"
  ^double [plant]
  (instantaneous-power-kw plant (/ Math/PI 2.0)))

(defn assert-plant-cap
  "ピーク出力が §1.9 の ≤50 kW/サイト上限を超えないか執行（salinity_gradient 再利用）。"
  [plant]
  (sg/assert-site-cap (peak-power-kw plant))
  plant)

;; ── 発電時系列 ──────────────────────────────────────────────────────────────

(defn generation-series
  "潮汐 1 周期を `samples` 点でサンプルした発電時系列。
  返り値 {:samples n :dt-h .. :series [{:t-h :phase :power-kw}...]
          :mean-kw :min-kw :peak-kw :capacity-factor}。
  capacity-factor = mean / peak（潮汐変動に伴う設備利用率）。"
  [plant & {:keys [samples] :or {samples 48}}]
  (let [period (double (:tidal-period-h plant))
        dt-h   (/ period samples)
        series (vec (for [k (range samples)]
                      (let [phase (/ (* 2.0 Math/PI k) samples)]
                        {:t-h (* k dt-h) :phase phase
                         :power-kw (instantaneous-power-kw plant phase)})))
        powers (map :power-kw series)
        mean   (/ (reduce + 0.0 powers) samples)
        peak   (reduce max 0.0 powers)]
    {:samples samples :dt-h dt-h :series series
     :mean-kw mean :min-kw (reduce min powers) :peak-kw peak
     :capacity-factor (if (> peak 0.0) (/ mean peak) 0.0)}))

;; ── 系統連系（蓄電平滑） ─────────────────────────────────────────────────────

(defn grid-tie
  "蓄電バッファで潮汐発電を平滑し、一定出力 target-kw で受け渡す。
  各 step の余剰/不足 (gen−target)·dt を SoC に積み、SoC∈[0,battery-kwh] にクランプ。
  SoC が 0 を割る不足は shortfall として計上（バッテリ不足の正直な開示）。
  返り値 {:target-kw :battery-kwh :shortfall-kwh :fully-smoothed :final-soc-kwh
          :delivered [kW...]}。"
  [plant & {:keys [target-kw battery-kwh samples] :or {battery-kwh 500.0 samples 48}}]
  (let [gen    (generation-series plant :samples samples)
        target (double (or target-kw (:mean-kw gen)))
        dt-h   (double (:dt-h gen))
        init   (* 0.5 battery-kwh)
        acc    (reduce
                 (fn [{:keys [soc shortfall-kwh delivered]} {:keys [power-kw]}]
                   (let [net  (* (- (double power-kw) target) dt-h) ;; kWh ±
                         soc' (+ soc net)
                         short (if (< soc' 0.0) (- soc') 0.0)       ;; 不足 kWh
                         soc-c (min battery-kwh (max 0.0 soc'))
                         deliver (- target (/ short dt-h))]
                     {:soc soc-c
                      :shortfall-kwh (+ shortfall-kwh short)
                      :delivered (conj delivered deliver)}))
                 {:soc init :shortfall-kwh 0.0 :delivered []}
                 (:series gen))]
    {:target-kw target
     :battery-kwh battery-kwh
     :shortfall-kwh (:shortfall-kwh acc)
     :fully-smoothed (< (:shortfall-kwh acc) 1e-6)
     :final-soc-kwh (:soc acc)
     :delivered (:delivered acc)}))

;; ── hikari マイクログリッド連系ハンドオフ ───────────────────────────────────

(defn couple-to-microgrid
  "ADR §4: 平滑後の funamori 出力を hikari マイクログリッドへ渡す datom。
  R0 はモデルのみ（実連系は hikari R2 + Council）。"
  [plant & {:keys [target-kw battery-kwh samples] :or {battery-kwh 500.0 samples 48}}]
  (assert-plant-cap plant)
  (let [gt (grid-tie plant :target-kw target-kw :battery-kwh battery-kwh :samples samples)
        gen (generation-series plant :samples samples)]
    {:funamori.plant/technology (:technology plant)
     :funamori.plant/rated-kw (:rated-kw plant)
     :funamori.plant/peak-kw (:peak-kw gen)
     :funamori.plant/mean-kw (:mean-kw gen)
     :funamori.plant/capacity-factor (:capacity-factor gen)
     :funamori.plant/delivered-kw (:target-kw gt)
     :funamori.plant/battery-kwh battery-kwh
     :funamori.plant/fully-smoothed (:fully-smoothed gt)
     :funamori.plant/microgrid "hikari"        ;; ADR §4 cross-actor sink
     :funamori.plant/model-only true}))         ;; R0 — no live grid-tie
