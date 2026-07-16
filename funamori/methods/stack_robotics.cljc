(ns funamori.methods.stack-robotics
  "stack_robotics — 淡水化発電スタックの設置・保守ロボティクス設計層。

  PRO/RED 塩分濃度差スタックは河口に据え付ける膜モジュールの矩形アレイで、
  ロボティクスが解く問題は二つ：

    1. 膜モジュール搬送  — 組立 + EOL 交換 (D2: ポリマー再生可能膜のみ)
    2. アンチファウリング被覆掃引 — ADR-2605265600 §5 のファウリング対策。
       膜面を頭部 footprint で隙間なく舐める boustrophedon (蛇行) 被覆経路。

  運動学・安全ゲートは **共有 kuni-umi ロボティクス基盤** を再利用する
  (ADR-2606091800; 正本 Clojure ポートは現状 hikari/methods/substrate.cljc に在る)。
  funamori はその infra-robotics 兄弟として基盤を借り、塩分濃度差スタック固有の
  ドメイン層（被覆経路 + モジュール搬送シーケンス + 膜許可ゲート）だけを足す。

  本層は純計算・R0 dry-run のみ。実機は動かさない (G11 no-server-key / no-live-actuation)。"
  (:require [funamori.methods.salinity-gradient :as sg]
            [hikari.methods.substrate :as sub]))

;; ── 許可 use（閉世界 civilian allowlist; N1/G3） ─────────────────────────────
(def permitted-uses
  #{"assemble" "service" "clean" "inspect" "swap"})

;; ── サービスアーム（河口スタック前の 2-link 平面マニピュレータ） ────────────
(defn service-arm
  "膜面を舐めるサービスアーム。既定 1.5 m + 1.2 m リンク (到達 ~2.7 m)。
  膜モジュール面 (~1 m 角) を十分カバーする。"
  ([] (service-arm [1.5 1.2]))
  ([link-lengths] (sub/->planar-arm link-lengths)))

;; ── スタック幾何 ────────────────────────────────────────────────────────────
(defn make-stack
  "膜スタック = 行×列のモジュールアレイ。
  :rows :cols           = モジュール数
  :module-w :module-h   = 1モジュール面の寸法 (m)
  :origin-x :origin-y    = アーム基準系でのアレイ左下隅 (m)。"
  [& {:keys [rows cols module-w module-h origin-x origin-y]
      :or   {rows 4 cols 4 module-w 1.0 module-h 1.0 origin-x 0.6 origin-y -1.0}}]
  {:rows rows :cols cols :module-w module-w :module-h module-h
   :origin-x origin-x :origin-y origin-y})

(defn module-count ^long [stack] (* (long (:rows stack)) (long (:cols stack))))

(defn module-center
  "(row,col) モジュール面の中心座標 (x y) をアーム基準系で返す。"
  [stack ^long row ^long col]
  [(+ (double (:origin-x stack)) (* (+ 0.5 col) (double (:module-w stack))))
   (+ (double (:origin-y stack)) (* (+ 0.5 row) (double (:module-h stack))))])

;; ── アンチファウリング被覆経路（boustrophedon） ─────────────────────────────

(defn- interval-union-length
  "区間集合 [[a b]...] の和の総長（被覆率計算用）。"
  ^double [intervals]
  (let [sorted (sort-by first intervals)]
    (loop [iv sorted, cur-lo nil, cur-hi nil, acc 0.0]
      (if (empty? iv)
        (+ acc (if cur-lo (- (double cur-hi) (double cur-lo)) 0.0))
        (let [[lo hi] (first iv)]
          (cond
            (nil? cur-lo) (recur (rest iv) lo hi acc)
            (<= (double lo) (double cur-hi)) (recur (rest iv) cur-lo (max (double cur-hi) (double hi)) acc)
            :else (recur (rest iv) lo hi (+ acc (- (double cur-hi) (double cur-lo))))))))))

(defn cleaning-path
  "1モジュール面 (module-w × module-h) を頭部 footprint で舐める蛇行被覆経路。
  :head-width = 掃引頭部の幅 (m), :overlap ∈ [0,1) = レーン重なり。
  返り値 {:waypoints [[x y]...] :coverage 0..1 :lanes n}。
  waypoints はモジュール中心 (cx,cy) を原点とした面内座標。"
  [stack & {:keys [head-width overlap] :or {head-width 0.25 overlap 0.1}}]
  (when (or (<= head-width 0.0) (< overlap 0.0) (>= overlap 1.0))
    (throw (ex-info "head-width must be >0 and overlap in [0,1)"
                    {:error :value :head-width head-width :overlap overlap})))
  (let [w (double (:module-w stack))
        h (double (:module-h stack))
        pitch (* head-width (- 1.0 overlap))
        half  (/ head-width 2.0)
        y-lo  (- (/ h 2.0))
        y-hi  (/ h 2.0)
        ;; レーン中心 y：下端から pitch 刻みで、上端を必ず覆う最終レーンを足す
        lanes (loop [y (+ y-lo half), acc []]
                (if (< y (- y-hi half))
                  (recur (+ y pitch) (conj acc y))
                  (conj acc (- y-hi half))))
        x-lo (- (/ w 2.0))
        x-hi (/ w 2.0)
        waypoints (vec (mapcat (fn [i y]
                                 (if (even? i) [[x-lo y] [x-hi y]] [[x-hi y] [x-lo y]]))
                               (range) lanes))
        covered (interval-union-length
                  (map (fn [y] [(max y-lo (- y half)) (min y-hi (+ y half))]) lanes))
        coverage (min 1.0 (/ covered (max 1e-9 h)))]
    {:waypoints waypoints :coverage coverage :lanes (count lanes)}))

;; ── 安全ゲート統合（kuni-umi 基盤を funamori の use 集合で呼ぶ） ─────────────

(defn- assert-safety
  "civilian (N1) + member-sig (no-server-key) を執行。witness は呼び出し側で記録。"
  [use member-sig server-sig]
  (sub/assert-civilian use permitted-uses)
  (sub/require-member-signature member-sig server-sig))

;; ── アンチファウリング掃引プラン ─────────────────────────────────────────────

(defn plan-clean-pass
  "1モジュール面のアンチファウリング掃引を計画する (ADR §5)。
  被覆経路 → IK で関節軌道 → 安全包絡チェック。R0 dry-run。
  返り値 {:datom .. :coverage .. :envelope-ok .. :reachable ..}。"
  [{:keys [stack arm row col head-width overlap member-sig server-sig
           witness-sigs env dt human-present]
    :or   {head-width 0.25 overlap 0.1 server-sig "" witness-sigs []
           dt 0.1 human-present true}}]
  (assert-safety "clean" member-sig server-sig)
  (let [arm   (or arm (service-arm))
        env   (or env (sub/->safety-envelope {:max-joint-speed 0.8 :human-proximity-speed 0.25}))
        [cx cy] (module-center stack row col)
        {:keys [waypoints coverage lanes]} (cleaning-path stack :head-width head-width :overlap overlap)
        ;; 面内座標 → アーム基準系の絶対座標 → IK 関節配置
        configs (map (fn [[dx dy]]
                       (let [x (+ cx dx) y (+ cy dy)]
                         (when (sub/reachable arm x y)
                           (sub/ik2 arm x y true))))
                     waypoints)
        reachable (every? some? configs)
        traj (vec (keep identity configs))
        ;; 連続 waypoint 間を関節空間で線形補間し 1本の軌道に連結
        full-traj (if (>= (count traj) 2)
                    (vec (mapcat (fn [a b] (sub/joint-trajectory a b 4)) traj (rest traj)))
                    traj)
        env-check (if (>= (count full-traj) 2)
                    (sub/check-trajectory env full-traj dt human-present)
                    {:ok true :violations []})
        quorum (sub/witness-quorum-ok witness-sigs)]
    {:datom {:funamori.robotics/job-id (str "clean." (:rows stack) "x" (:cols stack) "." row "." col)
             :funamori.robotics/use "clean"
             :funamori.robotics/coverage coverage
             :funamori.robotics/lanes lanes
             :funamori.robotics/reachable reachable
             :funamori.robotics/envelope-ok (:ok env-check)
             :funamori.robotics/witness-ok (:ok quorum)
             :funamori.robotics/member-sig member-sig
             :funamori.robotics/server-held-key false  ;; G11 構造的不変
             :funamori.robotics/dry-run true}           ;; R0 no-live-actuation
     :coverage coverage
     :reachable reachable
     :envelope-ok (:ok env-check)
     :envelope-violations (:violations env-check)
     :witness-ok (:ok quorum)}))

;; ── EOL 膜モジュール交換プラン（D2 再生可能膜のみ） ─────────────────────────

(defn plan-module-swap
  "EOL 膜モジュールの抜き取り→再生ビンへの搬送 (D2: ポリマー再生可能のみ)。
  新モジュールの膜は §1.1/§1.2/§2 ゲートを通過する必要がある (商用/PFAS 拒否)。
  R0 dry-run。返り値 {:datom .. :reachable .. :membrane-permitted ..}。"
  [{:keys [stack arm row col new-membrane member-sig server-sig witness-sigs env dt]
    :or   {server-sig "" witness-sigs [] dt 0.1}}]
  (assert-safety "swap" member-sig server-sig)
  ;; 新膜が憲法に違反していれば投げる（cross-method: salinity-gradient のゲート再利用）
  (sg/assert-membrane-permitted new-membrane)
  (let [arm   (or arm (service-arm))
        env   (or env (sub/->safety-envelope {:max-joint-speed 0.6 :human-proximity-speed 0.25}))
        [mx my] (module-center stack row col)
        bin   [0.4 -0.8]   ;; 再生ビン位置（アーム基準系）
        reach-mod (sub/reachable arm mx my)
        reach-bin (sub/reachable arm (first bin) (second bin))
        reachable (and reach-mod reach-bin)
        q-mod (when reach-mod (sub/ik2 arm mx my true))
        q-bin (when reach-bin (sub/ik2 arm (first bin) (second bin) true))
        traj  (when (and q-mod q-bin) (sub/joint-trajectory q-mod q-bin 8))
        env-check (if traj (sub/check-trajectory env traj dt true) {:ok false :violations ["unreachable"]})
        quorum (sub/witness-quorum-ok witness-sigs)]
    {:datom {:funamori.robotics/job-id (str "swap." row "." col)
             :funamori.robotics/use "swap"
             :funamori.robotics/membrane-id (:id new-membrane)
             :funamori.robotics/membrane-permitted true  ;; assert が通った時点で真
             :funamori.robotics/reachable reachable
             :funamori.robotics/envelope-ok (:ok env-check)
             :funamori.robotics/witness-ok (:ok quorum)
             :funamori.robotics/member-sig member-sig
             :funamori.robotics/server-held-key false
             :funamori.robotics/dry-run true}
     :reachable reachable
     :membrane-permitted true
     :envelope-ok (:ok env-check)
     :witness-ok (:ok quorum)}))

;; ── スタック全面アンチファウリング掃引（全モジュール） ──────────────────────

(defn plan-stack-clean
  "スタック全モジュールの掃引プランを畳み込む。
  返り値 {:modules n :all-reachable .. :min-coverage .. :all-envelope-ok .. :plans [..]}。"
  [{:keys [stack] :as opts}]
  (let [plans (for [row (range (:rows stack)) col (range (:cols stack))]
                (plan-clean-pass (assoc opts :row row :col col)))
        plans (vec plans)]
    {:modules (module-count stack)
     :all-reachable (every? :reachable plans)
     :min-coverage (reduce min 1.0 (map :coverage plans))
     :all-envelope-ok (every? :envelope-ok plans)
     :all-witness-ok (every? :witness-ok plans)
     :plans plans}))
