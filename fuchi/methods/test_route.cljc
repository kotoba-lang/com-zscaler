(ns fuchi.methods.test-route
  "Tests for 扶持 (fuchi) route.cljc — 1:1 port of methods/test_route.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [fuchi.methods.route :as r]))

(defn- env
  ([line imputed] (env line imputed 0))
  ([line imputed cash]
   {":envelope/line" (str ":" line) ":envelope/imputed-usd-micros-yr" imputed
    ":envelope/cash-usd-micros" cash}))

;; ── route-envelope ──────────────────────────────────────────────────────────
(deftest test-each-line-maps-to-its-producing-actor
  (let [rails (r/route-envelope (mapv #(env % 1000000)
                                      ["housing" "food" "energy" "compute" "tooling" "care" "liquidity"]))
        kinds (set (map :kind rails))
        providers (set (map :provider-actor rails))]
    (is (= kinds (set (map first (vals r/LINE-TO-RAIL)))))
    (is (= #{"commons-land" "mitsuho" "hikari" "murakumo" "okaimono" "iyashi" "warifu"} providers))))

(deftest test-liquidity-is-member-principal-only
  (let [rails (r/route-envelope [(env "food" 1) (env "liquidity" 1)])
        by-kind (into {} (map (fn [x] [(:kind x) x]) rails))]
    (is (false? (:member-principal (get by-kind "food-mitsuho"))))
    (is (true? (:member-principal (get by-kind "liquidity-warifu"))))))

(deftest test-cash-line-is-unrepresentable
  (doseq [bad ["cash" "stipend" "cash-disbursement"]]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cash≡0" (r/route-envelope [(env bad 1)]))
        (str bad " line must be refused (cash≡0)"))))

(deftest test-nonzero-cash-field-is-refused
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cash≡0" (r/route-envelope [(env "food" 1 5)]))))

(deftest test-unknown-line-has-no-rail
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G3" (r/route-envelope [(env "yacht" 1)]))))

;; ── in-kind-coverage ────────────────────────────────────────────────────────
(deftest test-fully-in-kind-is-100pct
  (is (= (r/in-kind-coverage (r/route-envelope [(env "food" 4) (env "energy" 1)])) 1.0)))

(deftest test-liquidity-lowers-coverage
  (is (= (r/in-kind-coverage (r/route-envelope [(env "food" 5) (env "liquidity" 5)])) 0.5)))

;; ── gov-route (G7 pure function) ────────────────────────────────────────────
(deftest test-low-imputed-in-kind-auto-accepts
  (is (= (r/gov-route 1000000000 false "") "auto")))

(deftest test-above-ceiling-goes-to-vote
  (is (= (r/gov-route (+ r/OPTIMISTIC-CEILING-USD-MICROS-YR 1) false "") "sbt-vote")))

(deftest test-invariant-touch-goes-to-council
  (is (= (r/gov-route 1 true "") "council-lv7")))

(deftest test-rider-hit-is-refused-over-everything
  (is (= (r/gov-route (long (Math/pow 10 18)) true "affiliate") "refused")))

(deftest test-rider-hit-detection
  (is (seq (r/rider-hit "requests an affiliate ad-revenue share")))
  (is (seq (r/rider-hit "広告 revenue")))
  (is (= "" (r/rider-hit "maintains the sanae weeder"))))

(deftest test-invariant-touch-detection
  (is (r/touches-invariant "new commons-land grant for housing"))
  (is (not (r/touches-invariant "food and energy sustenance"))))
