(ns fuchi.methods.test-allocate
  "Tests for 扶持 (fuchi) allocate.cljc — 1:1 port of methods/test_allocate.py (clojure.test)."
  (:require [clojure.test :refer [deftest is]]
            [fuchi.methods.allocate :as a]))

(defn- M [m] (a/make-maintainer m))

(defn- cohort []
  [(M {:did "did:m:abel" :tenure-months 96 :hazard-permille 1800
       :maintains ["sanae"] :prior-imputed-usd-micros-yr 9000000000})
   (M {:did "did:m:seth" :tenure-months 36 :hazard-permille 1400
       :prior-imputed-usd-micros-yr 28000000000})
   (M {:did "did:m:noah" :tenure-months 2 :hazard-permille 1000
       :prior-imputed-usd-micros-yr 6000000000 :covenant "outreach"})])

(deftest test-shares-sum-to-one-over-vowed
  (let [allocs (a/allocate (cohort) 30000000000)
        vowed (filter #(> (:share %) 0) allocs)]
    (is (< (Math/abs (- (reduce + 0.0 (map :share vowed)) 1.0)) 1e-9))))

(deftest test-cash-is-zero-for-every-allocation
  (doseq [al (a/allocate (cohort) 30000000000)] (is (= (:cash-usd-micros al) 0))))

(deftest test-no-server-held-key
  (doseq [al (a/allocate (cohort) 30000000000)] (is (false? (:server-held-key al)))))

(deftest test-tenure-weight-is-log-compressed
  (let [vet (M {:did "v" :tenure-months 480 :hazard-permille 1000})
        jr (M {:did "j" :tenure-months 60 :hazard-permille 1000})
        ratio (/ (a/tenure-weight vet) (a/tenure-weight jr))]
    (is (< 1.8 ratio 2.3) ratio)))

(deftest test-hazard-amplifies-weight
  (let [safe (M {:did "s" :tenure-months 60 :hazard-permille 1000})
        risky (M {:did "r" :tenure-months 60 :hazard-permille 2000})]
    (is (< (Math/abs (- (a/tenure-weight risky) (* 2 (a/tenure-weight safe)))) 1e-9))))

(deftest test-priority-rank-orders-by-weight
  (let [allocs (into {} (map (fn [al] [(:maintainer-did al) al]) (a/allocate (cohort) 30000000000)))]
    (is (< (:priority-rank (get allocs "did:m:abel")) (:priority-rank (get allocs "did:m:seth"))))))

(deftest test-outreach-gets-zero-share-but-a-floor
  (let [allocs (into {} (map (fn [al] [(:maintainer-did al) al]) (a/allocate (cohort) 30000000000)))
        noah (get allocs "did:m:noah")]
    (is (and (= (:share noah) 0.0) (> (:floor-usd-micros-yr noah) 0)))))

(deftest test-floor-decays-over-five-years
  (is (= (a/floor-decay 0) 1.0))
  (is (< (Math/abs (- (a/floor-decay 30) 0.5)) 1e-9))
  (is (= (a/floor-decay 60) 0.0))
  (is (= (a/floor-decay 120) 0.0)))

(deftest test-floor-is-stage-capped
  (let [allocs (into {} (map (fn [al] [(:maintainer-did al) al]) (a/allocate (cohort) 20000000000)))]
    (is (= (:floor-usd-micros-yr (get allocs "did:m:seth")) 20000000000))))

;; ── G1: no investment vehicle ───────────────────────────────────────────────
(deftest test-assert-instrument-accepts-sustenance-set
  (doseq [i a/ALLOWED-INSTRUMENTS] (is (= (a/assert-instrument i) i))))

(deftest test-equity-instrument-is-unrepresentable
  (doseq [bad ["equity" ":equity" "debt" "revenue-share" "carry" "dividend" "exit"]]
    (is (thrown? clojure.lang.ExceptionInfo (a/assert-instrument bad)) (str bad " must be rejected (G1)"))))

(deftest test-allocate-refuses-investment-instrument
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G1"
                        (a/allocate (cohort) 30000000000 0 "equity"))))

(deftest test-allocation-construction-refuses-cash
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"(?i)cash"
                        (a/make-allocation {:maintainer-did "d" :instrument "sustenance" :weight 1.0
                                            :share 1.0 :priority-rank 1 :floor-usd-micros-yr 1000
                                            :cash-usd-micros 5}))))

(deftest test-allocation-construction-refuses-server-key
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no-server-key"
                        (a/make-allocation {:maintainer-did "d" :instrument "sustenance" :weight 1.0
                                            :share 1.0 :priority-rank 1 :floor-usd-micros-yr 1000
                                            :server-held-key true}))))

;; ── G5: payoff attribution ──────────────────────────────────────────────────
(deftest test-maintainer-cannot-own-payoff
  (let [c (conj (cohort) (M {:did "did:m:x" :tenure-months 12 :hazard-permille 1000 :owns-payoff true}))]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"G5" (a/allocate c 30000000000)))))
