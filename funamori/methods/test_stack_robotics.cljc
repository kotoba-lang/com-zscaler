(ns funamori.methods.test-stack-robotics
  "Tests for funamori.methods.stack-robotics — 淡水化発電スタック設置・保守ロボティクス。
  運動学・安全ゲートの再利用 + アンチファウリング被覆経路 + EOL モジュール交換 +
  膜許可ゲートの cross-method 執行を pin する。"
  (:require [clojure.test :refer [deftest is testing]]
            [funamori.methods.stack-robotics :as r]
            [funamori.methods.salinity-gradient :as sg]))

(def member "did:key:zMember")
(def w2 ["did:key:zRobotA" "did:key:zRobotB"])

;; ── 幾何 ────────────────────────────────────────────────────────────────────

(deftest test-stack-geometry
  (let [st (r/make-stack :rows 4 :cols 4)]
    (is (= 16 (r/module-count st)))
    ;; 行・列が進むほど中心は +y / +x へ
    (let [[x0 y0] (r/module-center st 0 0)
          [x1 y1] (r/module-center st 1 1)]
      (is (< x0 x1))
      (is (< y0 y1)))))

;; ── 被覆経路 ────────────────────────────────────────────────────────────────

(deftest test-cleaning-path-full-coverage
  (testing "pitch ≤ head-width なら膜面を完全被覆 (ファウリング掃引)"
    (let [st (r/make-stack :module-w 1.0 :module-h 1.0)
          p (r/cleaning-path st :head-width 0.25 :overlap 0.1)]
      (is (> (:coverage p) 0.999))
      (is (>= (:lanes p) 4))
      ;; 蛇行: 偶数レーンは左→右, 奇数レーンは右→左
      (is (even? (count (:waypoints p))))
      (is (every? #(= 2 (count %)) (:waypoints p))))))

(deftest test-cleaning-path-validates-params
  (let [st (r/make-stack)]
    (is (thrown? #?(:clj Exception :cljs js/Error) (r/cleaning-path st :head-width 0.0)))
    (is (thrown? #?(:clj Exception :cljs js/Error) (r/cleaning-path st :overlap 1.0)))
    (is (thrown? #?(:clj Exception :cljs js/Error) (r/cleaning-path st :overlap -0.1)))))

(deftest test-coverage-monotone-in-head-width
  ;; 広い頭部ほど少ないレーンで覆える
  (let [st (r/make-stack :module-h 1.0)
        narrow (r/cleaning-path st :head-width 0.1 :overlap 0.0)
        wide   (r/cleaning-path st :head-width 0.4 :overlap 0.0)]
    (is (> (:lanes narrow) (:lanes wide)))
    (is (> (:coverage narrow) 0.999))
    (is (> (:coverage wide) 0.999))))

;; ── 安全ゲート（civilian / no-server-key / witness） ─────────────────────────

(deftest test-clean-pass-requires-member-signature
  (let [st (r/make-stack)]
    (testing "no-server-key: member-sig 無し → 投げる"
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (r/plan-clean-pass {:stack st :row 0 :col 0 :member-sig ""}))))
    (testing "no-server-key: server-sig 有り → 投げる"
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (r/plan-clean-pass {:stack st :row 0 :col 0
                                       :member-sig member :server-sig "srv"}))))))

(deftest test-clean-pass-rejects-forbidden-use
  ;; assert-civilian は内部で "clean" 固定だが、forbidden use を直接渡す経路を pin
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (#'funamori.methods.stack-robotics/assert-safety "weapon" member ""))))

(deftest test-clean-pass-plan-shape-and-invariants
  (let [st (r/make-stack :rows 2 :cols 2)
        res (r/plan-clean-pass {:stack st :row 0 :col 0
                                :member-sig member :witness-sigs w2})]
    (is (:reachable res))
    (is (> (:coverage res) 0.999))
    (is (:witness-ok res))
    ;; G11 構造的不変: server-held-key は常に false, dry-run は常に true
    (is (false? (get-in res [:datom :funamori.robotics/server-held-key])))
    (is (true?  (get-in res [:datom :funamori.robotics/dry-run])))))

(deftest test-witness-quorum-under-two-flags-escalation
  (let [st (r/make-stack :rows 2 :cols 2)
        res (r/plan-clean-pass {:stack st :row 0 :col 0
                                :member-sig member :witness-sigs ["did:key:onlyone"]})]
    (is (false? (:witness-ok res)))))

;; ── EOL モジュール交換 + 膜ゲート cross-method ──────────────────────────────

(def ok-membrane {:id "membrane.pro.tfc-inhouse-r0" :vendor "in-house"
                  :chemistry "tfc-polyamide" :license :in-house})

(deftest test-module-swap-permits-open-membrane
  (let [st (r/make-stack :rows 2 :cols 2)
        res (r/plan-module-swap {:stack st :row 0 :col 0
                                 :new-membrane ok-membrane
                                 :member-sig member :witness-sigs w2})]
    (is (:reachable res))
    (is (:membrane-permitted res))
    (is (false? (get-in res [:datom :funamori.robotics/server-held-key])))))

(deftest test-module-swap-rejects-commercial-membrane
  (testing "swap は salinity-gradient の §2 ゲートを再利用して商用膜を拒否する"
    (let [st (r/make-stack :rows 2 :cols 2)]
      (is (thrown? #?(:clj Exception :cljs js/Error)
                   (r/plan-module-swap {:stack st :row 0 :col 0
                                        :new-membrane {:id "x" :vendor "Toray" :license :open-publication}
                                        :member-sig member :witness-sigs w2}))))))

(deftest test-module-swap-rejects-pfas-membrane
  (let [st (r/make-stack :rows 2 :cols 2)]
    (is (thrown? #?(:clj Exception :cljs js/Error)
                 (r/plan-module-swap {:stack st :row 0 :col 0
                                      :new-membrane {:id "x" :vendor "in-house" :chemistry "Nafion" :license :in-house}
                                      :member-sig member :witness-sigs w2})))))

;; ── スタック全面掃引 ────────────────────────────────────────────────────────

(deftest test-plan-stack-clean-folds-all-modules
  (let [st (r/make-stack :rows 3 :cols 3)
        res (r/plan-stack-clean {:stack st :member-sig member :witness-sigs w2})]
    (is (= 9 (:modules res)))
    (is (= 9 (count (:plans res))))
    (is (> (:min-coverage res) 0.999))
    (is (:all-witness-ok res))))
