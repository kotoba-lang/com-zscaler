(ns funamori.methods.salinity-gradient
  "salinity_gradient — 淡水化発電 (salinity-gradient osmotic power) physics + Charter-gate core.

  淡水と海水を混ぜるときに解放される混合のギブズ自由エネルギーを電力に変える、
  二つの膜方式をモデル化する：

    PRO  Pressure-Retarded Osmosis  — 浸透圧で汲み上げた drawの圧力でタービンを回す
    RED  Reverse Electrodialysis    — 陽/陰イオン交換膜の交互スタックで起電力を得る

  ADR-2605265600 (sub-ADR of 2605264100 §4) の D1..D5 評価を **コードで執行**する：
  膜の open-publication 強制 / 商用プロプライエタリ膜の絶対禁止 / PFAS 膜禁止 /
  塩分差 ≥30 g/L / 電力密度 ≥1 W/m² の R3 品質ゲート / ≤50 kW・1サイト上限。
  これらは『文書化された制約』ではなく `ex-info` を投げる関数である。

  純 Clojure (clojure.core のみ)、外部依存なし。Portable .cljc。

  記号：
    C      : 塩濃度 (mol/m³ = mmol/L·1000)。海水 NaCl ≈ 600 mol/m³
    g/L    : 質量濃度 (NaCl 58.44 g/mol で mol/m³ ↔ g/L 変換)
    pi     : 浸透圧 π = i·C·R·T (van't Hoff, Pa)
    Jw     : 水フラックス (m³/m²·s = m/s) Jw = A·(Δπ − ΔP)
    W      : 膜面積あたり電力密度 (W/m²)"
  (:require [clojure.string]))

;; ── 物理定数 ────────────────────────────────────────────────────────────────
(def ^:const R-gas    8.314)     ;; J/(mol·K)  理想気体定数
(def ^:const F-faraday 96485.0)  ;; C/mol      ファラデー定数
(def ^:const T-default 293.15)   ;; K          20 °C 河口の代表温度
(def ^:const i-nacl    2.0)      ;; NaCl の van't Hoff 係数 (完全解離)
(def ^:const M-nacl-g  58.44)    ;; g/mol      NaCl 分子量

;; ADR-2605265600 由来の憲法定数（数値=憲法。緩めるには ADR 改定 + Council Lv7+ unanimity）
(def ^:const min-salinity-diff-g-l 30.0)  ;; §1 条件4: Δ塩分 ≥30 g/L (経済的成立)
(def ^:const r3-power-density-floor 1.0)  ;; §1 条件6: R3 までに ≥1 W/m² 実証
(def ^:const max-kw-per-site        50.0) ;; §1 条件9 / 親§4: ≤50 kW/サイト
(def ^:const max-sites-through-r3   1)    ;; §1 条件9: ≤1 サイト (religious-corp 累計, R3 まで)

;; §2 絶対禁止の商用プロプライエタリ膜（D1 vendor + D5 closed IP）
(def prohibited-membranes
  #{"toray" "toray-tfc-pro" "hydranautics" "hydranautics-red"
    "ge-power" "ge-power-sepa" "statkraft" "statkraft-pro"})

;; §1 条件2 / §2 / Charter §2(c): PFAS（過フッ素化）膜は残留汚染物質として禁止
(def pfas-membranes
  #{"nafion" "dupont-nafion" "pfsa" "perfluorosulfonic-acid"})

;; ── 濃度 / 浸透圧 ────────────────────────────────────────────────────────────

(defn g-l->mol-m3
  "質量濃度 g/L を NaCl のモル濃度 mol/m³ に変換 (g/L → mol/m³)。"
  ^double [^double g-per-l]
  (* (/ g-per-l M-nacl-g) 1000.0))

(defn mol-m3->g-l
  ^double [^double mol-per-m3]
  (* (/ mol-per-m3 1000.0) M-nacl-g))

(defn osmotic-pressure
  "van't Hoff 浸透圧 π = i·C·R·T (Pa)。C は mol/m³、T は K。
  海水 ≈ 600 mol/m³, 20°C → ≈ 29 bar。"
  ^double [^double conc-mol-m3 ^double temp-k]
  (when (or (neg? conc-mol-m3) (<= temp-k 0.0))
    (throw (ex-info "concentration must be ≥0 and temperature >0 K"
                    {:error :value :conc conc-mol-m3 :temp-k temp-k})))
  (* i-nacl conc-mol-m3 R-gas temp-k))

;; ── 塩分源（draw=高濃度 / feed=低濃度）の定義 ─────────────────────────────────

(defn make-source-pair
  "河口の塩分源ペアを g/L で定義する。
  :draw-g-l = 海水側 (高濃度), :feed-g-l = 河川側 (低濃度)。"
  [& {:keys [draw-g-l feed-g-l temp-k]
      :or   {draw-g-l 35.0 feed-g-l 0.5 temp-k T-default}}]
  (when (< draw-g-l feed-g-l)
    (throw (ex-info "draw (sea) salinity must be ≥ feed (river) salinity"
                    {:error :value :draw draw-g-l :feed feed-g-l})))
  {:draw-g-l draw-g-l :feed-g-l feed-g-l :temp-k temp-k})

(defn salinity-difference-g-l
  "Δ塩分 (g/L) = draw − feed。"
  ^double [pair]
  (- (double (:draw-g-l pair)) (double (:feed-g-l pair))))

(defn delta-pi
  "Δπ = π(draw) − π(feed) (Pa)。発電を駆動する浸透圧差。"
  ^double [pair]
  (let [t (double (:temp-k pair))]
    (- (osmotic-pressure (g-l->mol-m3 (double (:draw-g-l pair))) t)
       (osmotic-pressure (g-l->mol-m3 (double (:feed-g-l pair))) t))))

;; ── §1 条件4: 塩分差ゲート (≥30 g/L) ─────────────────────────────────────────

(defn salinity-difference-ok?
  "経済的成立に必要な Δ塩分 ≥30 g/L を満たすか。汽水域 (≤15 g/L) は R4+ 送り。"
  [pair]
  (>= (salinity-difference-g-l pair) min-salinity-diff-g-l))

(defn assert-salinity-difference
  "Δ塩分 <30 g/L なら投げる（ADR §1 条件4 を執行）。"
  [pair]
  (when-not (salinity-difference-ok? pair)
    (throw (ex-info (str "salinity difference " (format "%.1f" (salinity-difference-g-l pair))
                         " g/L < required " min-salinity-diff-g-l
                         " g/L (brackish ≤15 g/L deferred to R4+; ADR-2605265600 §1.4)")
                    {:error :charter-gate :gate :salinity-difference
                     :diff-g-l (salinity-difference-g-l pair)
                     :required min-salinity-diff-g-l})))
  pair)

;; ── §1 条件1/2 + §2: 膜の open-license / PFAS ゲート ──────────────────────────

(defn- normalize-tag [s]
  (-> (str s) clojure.string/trim clojure.string/lower-case))

(defn membrane-permitted?
  "膜が憲法的に許可されるか。
  許可 = (religious-corp 自社開発 OR open-publication ライセンス) AND
         商用プロプライエタリ膜でない AND PFAS でない。
  `membrane` は {:vendor :license :chemistry} のマップ。
    :license ∈ #{:in-house :open-publication :openmta :apache-2.0} が必須 (§1 条件1)。"
  [membrane]
  (let [vendor    (normalize-tag (:vendor membrane))
        chemistry (normalize-tag (:chemistry membrane))
        license   (keyword (:license membrane))
        open?     (contains? #{:in-house :open-publication :openmta :apache-2.0} license)
        commercial? (contains? prohibited-membranes vendor)
        pfas?     (or (contains? pfas-membranes vendor)
                      (contains? pfas-membranes chemistry))]
    (and open? (not commercial?) (not pfas?))))

(defn assert-membrane-permitted
  "膜が憲法に違反していれば、失敗ゲートを名指しして投げる (ADR §1.1/§1.2/§2)。"
  [membrane]
  (let [vendor    (normalize-tag (:vendor membrane))
        chemistry (normalize-tag (:chemistry membrane))
        license   (keyword (:license membrane))]
    (cond
      (contains? prohibited-membranes vendor)
      (throw (ex-info (str "prohibited commercial membrane vendor '" vendor
                           "' (D1 vendor + D5 closed IP; ADR-2605265600 §2)")
                      {:error :charter-gate :gate :commercial-membrane :vendor vendor}))

      (or (contains? pfas-membranes vendor) (contains? pfas-membranes chemistry))
      (throw (ex-info (str "PFAS membrane chemistry prohibited (persistent pollutant; "
                           "Charter §2(c) + ADR-2605265600 §1.2)")
                      {:error :charter-gate :gate :pfas-membrane
                       :vendor vendor :chemistry chemistry}))

      (not (contains? #{:in-house :open-publication :openmta :apache-2.0} license))
      (throw (ex-info (str "membrane license " (pr-str license)
                           " not open — must be in-house OR open-publication "
                           "(ADR-2605265600 §1.1 / D5)")
                      {:error :charter-gate :gate :membrane-open-license :license license}))

      :else membrane)))

;; ── PRO: 圧力遅延浸透 ────────────────────────────────────────────────────────

(defn make-pro-membrane
  "PRO 膜パラメータ。:water-permeability A は m³/(m²·s·Pa) ≈ m/(s·Pa)。
  代表値 A ≈ 1e-12 (薄膜複合ポリアミド TFC on ポリスルホン支持; ADR 表)。
  :open-license と :vendor/:chemistry は §1.1/§1.2 ゲート用。"
  [& {:keys [water-permeability vendor chemistry license]
      :or   {water-permeability 1.0e-12
             vendor "in-house" chemistry "tfc-polyamide" license :in-house}}]
  {:type :pro
   :water-permeability water-permeability
   :vendor vendor :chemistry chemistry :license license})

(defn pro-water-flux
  "PRO 水フラックス Jw = A·(Δπ − ΔP) (m/s)。ΔP = 印加圧 (Pa)。
  ΔP ≥ Δπ で逆浸透域に入り Jw≤0（発電せず）。"
  ^double [membrane ^double delta-pi-pa ^double applied-pressure-pa]
  (* (double (:water-permeability membrane))
     (max 0.0 (- delta-pi-pa applied-pressure-pa))))

(defn pro-power-density
  "PRO 電力密度 W = Jw·ΔP (W/m²)。Jw は m/s, ΔP は Pa → W/m²。"
  ^double [membrane ^double delta-pi-pa ^double applied-pressure-pa]
  (* (pro-water-flux membrane delta-pi-pa applied-pressure-pa) applied-pressure-pa))

(defn pro-optimal-pressure
  "最大電力を与える印加圧 ΔP* = Δπ/2 (Pa)。
  W = A·(Δπ−ΔP)·ΔP を ΔP で最大化すると ΔP*=Δπ/2。"
  ^double [^double delta-pi-pa]
  (/ delta-pi-pa 2.0))

(defn pro-max-power-density
  "ΔP* = Δπ/2 における最大電力密度 W_max = A·Δπ²/4 (W/m²)。"
  ^double [membrane ^double delta-pi-pa]
  (let [p* (pro-optimal-pressure delta-pi-pa)]
    (pro-power-density membrane delta-pi-pa p*)))

;; ── RED: 逆電気透析 ──────────────────────────────────────────────────────────

(defn make-red-stack
  "RED スタックパラメータ。
  :cell-pairs N      = 陽/陰イオン交換膜ペア数
  :permselectivity α = 膜の選択透過性 (0..1, 開放設計で ~0.9)
  :area-resistance   = 膜ペアあたり総面積抵抗 (Ω·m²; 膜+希釈側河川水で支配, ~4e-3)
  :pair-area-m2       = 膜ペア1組の有効面積 (m²)。

  注: 電力密度 = E_cell²/(8·area-resistance)（N と面積に依らない閉形式）。
  希釈側（河川水）の高抵抗が支配するため area-resistance を ~4e-3 Ω·m² と取ると
  電力密度は ~1 W/m² となり ADR の 0.5–2 W/m² 帯に収まる。"
  [& {:keys [cell-pairs permselectivity area-resistance pair-area-m2
             vendor chemistry license]
      :or   {cell-pairs 50 permselectivity 0.9 area-resistance 4.0e-3
             pair-area-m2 1.0
             vendor "in-house" chemistry "speek-sulfonated" license :in-house}}]
  {:type :red
   :cell-pairs cell-pairs :permselectivity permselectivity
   :area-resistance area-resistance :pair-area-m2 pair-area-m2
   :vendor vendor :chemistry chemistry :license license})

(defn red-cell-emf
  "膜ペア1組の Nernst 起電力 E = 2α·(RT/F)·ln(C_draw/C_feed) (V)。
  係数2は陽膜と陰膜の両方が寄与するため。"
  ^double [stack pair]
  (let [t (double (:temp-k pair))
        c-draw (g-l->mol-m3 (double (:draw-g-l pair)))
        c-feed (g-l->mol-m3 (double (:feed-g-l pair)))]
    (* 2.0 (double (:permselectivity stack))
       (/ (* R-gas t) F-faraday)
       (Math/log (/ (max 1e-9 c-draw) (max 1e-9 c-feed))))))

(defn red-stack-emf
  "スタック総起電力 EMF = N·E_pair (V)。"
  ^double [stack pair]
  (* (double (:cell-pairs stack)) (red-cell-emf stack pair)))

(defn red-internal-resistance
  "スタック内部抵抗 R_int = N·area-resistance/pair-area (Ω)。"
  ^double [stack]
  (/ (* (double (:cell-pairs stack)) (double (:area-resistance stack)))
     (max 1e-12 (double (:pair-area-m2 stack)))))

(defn red-max-power
  "整合負荷での最大電力 P_max = EMF²/(4·R_int) (W)。"
  ^double [stack pair]
  (let [emf (red-stack-emf stack pair)
        r   (red-internal-resistance stack)]
    (/ (* emf emf) (* 4.0 r))))

(defn red-power-density
  "RED 電力密度 = P_max / 総膜面積 (W/m²)。
  総膜面積 = 2·N·pair-area (陽膜+陰膜)。"
  ^double [stack pair]
  (let [total-area (* 2.0 (double (:cell-pairs stack)) (double (:pair-area-m2 stack)))]
    (/ (red-max-power stack pair) (max 1e-12 total-area))))

;; ── §1 条件6: 電力密度 R3 品質ゲート (≥1 W/m²) ──────────────────────────────

(defn power-density-meets-r3-gate?
  "R3 スケールアップ前に必要な ≥1 W/m² を満たすか。"
  [^double power-density-w-m2]
  (>= power-density-w-m2 r3-power-density-floor))

(defn assert-r3-power-density
  "電力密度 <1 W/m² なら投げる（経済的に無意味 → 再設計 or DEFER; ADR §1.6）。"
  [^double power-density-w-m2]
  (when-not (power-density-meets-r3-gate? power-density-w-m2)
    (throw (ex-info (str "power density " (format "%.3f" power-density-w-m2)
                         " W/m² < R3 floor " r3-power-density-floor
                         " W/m² (re-design or DEFER; ADR-2605265600 §1.6)")
                    {:error :charter-gate :gate :r3-power-density
                     :power-density power-density-w-m2 :floor r3-power-density-floor})))
  power-density-w-m2)

;; ── §1 条件7: PRO vs RED 選択 ────────────────────────────────────────────────

(defn select-technology
  "サイト固有の技術選択 (ADR §1.7)。
  PRO  : 高 Δ塩分 (≥35 g/L) の深い河口
  RED  : 中間 Δ塩分 + 低ファウリング許容
  返り値 {:technology :rationale}（最終決定は Council Lv6+ ≥3 per site）。"
  [pair]
  (let [diff (salinity-difference-g-l pair)]
    (cond
      (>= diff 35.0) {:technology :pro
                      :rationale "high Δsalinity (≥35 g/L) deep estuary favours PRO"}
      (>= diff min-salinity-diff-g-l) {:technology :red
                                       :rationale "intermediate Δsalinity favours RED (lower fouling tolerance)"}
      :else {:technology :defer
             :rationale (str "Δsalinity " (format "%.1f" diff)
                             " g/L below 30 g/L floor — DEFER to R4+ (brackish)")})))

;; ── §1 条件9 / 親§4: サイト電力上限ゲート (≤50 kW, ≤1 サイト) ────────────────

(defn site-cap-ok?
  "サイト出力 ≤50 kW か。"
  [^double rated-kw]
  (<= rated-kw max-kw-per-site))

(defn assert-site-cap
  "定格 >50 kW なら投げる（親 ADR-2605264100 §4 / §1.9 上限を執行）。"
  [^double rated-kw]
  (when-not (site-cap-ok? rated-kw)
    (throw (ex-info (str "site rating " (format "%.1f" rated-kw)
                         " kW > cap " max-kw-per-site
                         " kW per site (ADR-2605265600 §1.9 / parent §4)")
                    {:error :charter-gate :gate :site-cap
                     :rated-kw rated-kw :cap max-kw-per-site})))
  rated-kw)

(defn assert-site-count
  "religious-corp 累計サイト数が R3 上限 (1) を超えないか執行。"
  [^long existing-site-count]
  (when (>= existing-site-count max-sites-through-r3)
    (throw (ex-info (str "religious-corp already operates " existing-site-count
                         " salinity-gradient site(s); cap is " max-sites-through-r3
                         " through R3 (ADR-2605265600 §1.9)")
                    {:error :charter-gate :gate :site-count
                     :existing existing-site-count :cap max-sites-through-r3})))
  existing-site-count)

;; ── サイト規模見積り（面積 → 出力） ──────────────────────────────────────────

(defn rated-power-kw
  "総膜面積 (m²) と実測電力密度 (W/m²) からサイト定格 (kW)。"
  ^double [^double total-membrane-area-m2 ^double power-density-w-m2]
  (/ (* total-membrane-area-m2 power-density-w-m2) 1000.0))

(defn membrane-area-for-kw
  "目標出力 (kW) と電力密度 (W/m²) に必要な総膜面積 (m²)。"
  ^double [^double target-kw ^double power-density-w-m2]
  (when (<= power-density-w-m2 0.0)
    (throw (ex-info "power density must be >0 W/m²" {:error :value})))
  (/ (* target-kw 1000.0) power-density-w-m2))

;; ── 統合: サイト設計の憲法適合評価 ──────────────────────────────────────────

(defn evaluate-site
  "サイト設計を ADR-2605265600 の全ゲートに通して評価する。
  全ゲート合格なら {:permitted true :technology .. :power-density-w-m2 .. :rated-kw ..}、
  違反があれば :permitted false + :violation（投げずに集約）。

  引数マップ:
    :pair                  make-source-pair の結果
    :membrane              PRO 膜 or RED スタック（:vendor/:chemistry/:license 必須）
    :power-density-w-m2    実測/設計電力密度 (W/m²)
    :total-membrane-area-m2 総膜面積
    :existing-site-count   religious-corp 既存サイト数 (default 0)"
  [{:keys [pair membrane power-density-w-m2 total-membrane-area-m2 existing-site-count]
    :or   {existing-site-count 0}}]
  (try
    (assert-salinity-difference pair)
    (assert-membrane-permitted membrane)
    (assert-r3-power-density power-density-w-m2)
    (assert-site-count existing-site-count)
    (let [rated (rated-power-kw total-membrane-area-m2 power-density-w-m2)]
      (assert-site-cap rated)
      {:permitted true
       :technology (:technology (select-technology pair))
       :technology-rationale (:rationale (select-technology pair))
       :salinity-diff-g-l (salinity-difference-g-l pair)
       :delta-pi-bar (/ (delta-pi pair) 1.0e5)
       :power-density-w-m2 power-density-w-m2
       :rated-kw rated})
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) e
      {:permitted false
       :violation (:gate (ex-data e))
       :message #?(:clj (.getMessage e) :cljs (.-message e))})))

(defn design-margins
  "Feasibility headroom of a salinity-gradient design vs the three §1 gates — Δsalinity ≥ 30 g/L
  (条件4 / G4), power-density ≥ 1.0 W/m² (条件6 R3 floor / G5), rated power ≤ 50 kW/site (条件9 / G6).
  For each gate reports the absolute margin (how far inside the limit) and the RELATIVE margin
  (margin ÷ the limit, so the three different-unit gates compare on one scale), names the BINDING
  constraint (the smallest relative margin — the design's limiting factor), and whether the design is
  feasible (every margin ≥ 0). A DESCRIPTIVE design-analysis over the SAME gates the assert-* fns
  enforce — reported as headroom rather than thrown, so a near-limit OR infeasible design is legible
  (a negative margin marks the violated gate). It only READS the gate constants; it never relaxes,
  tunes, or weakens a gate (the floors/cap stay Tier-1). Takes the computed design metrics (the exact
  keys a permitted `evaluate-site` result already carries); returns
  {:salinity {:margin :relative} :power-density {…} :power {…} :binding-constraint k :feasible bool}."
  [{:keys [salinity-diff-g-l power-density-w-m2 rated-kw]}]
  (let [gates {:salinity      [(- salinity-diff-g-l min-salinity-diff-g-l) min-salinity-diff-g-l]
               :power-density [(- power-density-w-m2 r3-power-density-floor) r3-power-density-floor]
               :power         [(- max-kw-per-site rated-kw) max-kw-per-site]}
        rows (into {} (for [[k [margin ref]] gates]
                        [k {:margin margin :relative (/ margin ref)}]))]
    {:salinity (:salinity rows)
     :power-density (:power-density rows)
     :power (:power rows)
     :binding-constraint (key (apply min-key (comp :relative val) rows))
     :feasible (every? #(>= (:margin %) 0.0) (vals rows))}))
