#!/usr/bin/env bb
;; noroshi 烽 — MT19937 / random.Random parity test.
;; Run:  bb --classpath 20-actors 20-actors/noroshi/methods/test_mt19937.cljc
(ns noroshi.methods.test-mt19937
  "Pins noroshi.methods.mt19937 byte-for-byte against CPython's `random.Random(seed)`.

  mt19937's ENTIRE reason to exist is reproducing CPython's seeded RNG exactly, so isac_sim's
  `_add_noise` (random.Random(seed).gauss) yields byte-identical CA-CFAR radar detections vs the
  python3 reference. A subtle MT19937 / genrand_res53 / gauss_next-cache bug would silently diverge
  the whole radar sim from python — yet the module had no test. The pinned literals below are the
  ACTUAL output of `python3 -c 'random.Random(seed)…'` (repr, full precision), so this test fails
  the instant the port drifts from CPython."
  (:require [noroshi.methods.mt19937 :as mt]
            [clojure.test :refer [deftest is run-tests]]))

(def ^:private eps 1.0e-12)
(defn- close? [a b] (< (Math/abs (- (double a) (double b))) eps))
(defn- takes [f n] (vec (repeatedly n f)))

;; ── CPython reference (python3 random.Random(seed); repr, full precision) ──
(def ^:private ref-random
  {0     [0.8444218515250481 0.7579544029403025 0.420571580830845 0.25891675029296335]
   42    [0.6394267984578837 0.025010755222666936 0.27502931836911926 0.22321073814882275]
   12345 [0.41661987254534116 0.010169169457068361 0.8252065092537432 0.2986398551995928]})

(def ^:private ref-gauss
  {0     [0.9417154046806644 -1.3965781047011498 -0.6797144480784211 0.3705035674606598]
   42    [-0.14409032957792836 -0.1729036003315193 -0.11131586156766246 0.7019837250988631]
   12345 [-0.12380079558885389 0.07152496347566478 0.3833691943171596 -0.7499970608869415]})

(deftest random!-matches-cpython-genrand-res53
  (doseq [[seed expected] ref-random]
    (let [g (mt/make seed)
          got (takes #(mt/random! g) (count expected))]
      (is (every? true? (map close? got expected))
          (str "random! parity for seed " seed " — got " got)))))

(deftest gauss!-matches-cpython-with-cache
  ;; the gauss_next one-value cache means gauss() consumes the RNG two-at-a-time then caches one
  (doseq [[seed expected] ref-gauss]
    (let [g (mt/make seed)
          got (takes #(mt/gauss! g 0.0 1.0) (count expected))]
      (is (every? true? (map close? got expected))
          (str "gauss! parity for seed " seed " — got " got)))))

(deftest gauss!-applies-mu-sigma
  ;; python3 random.Random(7).gauss(5.0, 2.0) — affine transform of the standard normal
  (let [g (mt/make 7)
        got (takes #(mt/gauss! g 5.0 2.0) 3)
        expected [4.488239423104799 6.022863025033028 4.547807670433791]]
    (is (every? true? (map close? got expected)) (str "gauss(5,2) parity — got " got))))

(deftest independent-streams-from-same-seed
  ;; two generators seeded identically produce identical streams (pure-ish, deterministic)
  (let [a (mt/make 99) b (mt/make 99)]
    (is (= (takes #(mt/random! a) 8) (takes #(mt/random! b) 8))
        "same seed → identical stream (reproducible / resume-safe)")))

#?(:clj
   (when (= *file* (System/getProperty "babashka.file"))
     (let [{:keys [fail error]} (run-tests 'noroshi.methods.test-mt19937)]
       (System/exit (if (zero? (+ fail error)) 0 1)))))
